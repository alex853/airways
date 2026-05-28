package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static net.simforge.airways2.storage.Storage.Condition.and;

// constantly running process which finds some, few, not too many W or J journeys in looking for tickets status
// and pick them up - mark them as 'special processing'
// save info into dedicated storage and sets expiration date - lets start from 24 hours
// that means 'mission', or 'job', or 'order'
// when mission expires without being picked up by any pilot, the journey is released back to 'normal processing'

// the screen with available missions shows list of missions, from-to airport,
// offered aircraft and its proposed full itinerary, including total flight time,
// plus mission cost, and pilot's paycheck

// the pilot can take the mission, this means pilot commits to make all the flights for the mission within 48 hours window
// all the flight missions are created, pilot can specify and change departure time of any mission
// departure time of passenger flight influences to journey's checkin time
// checkin time and boarding time can be shortened due to small airplane size
// comparison of planned and actual departure & arrival times can be used as a measure for kind of bonus or something else

// "back to base" flight can be omitted *** this will be reviewed later...... there is an obligation to fly back to base
// however if someone flies two missions in a row, this "back to base" flight and then positioning flight is weird
// on the other hand, if someone drops, leaves plane unattended for days or weeks, this should be avoided and punished

// so, the mission has execution time window (48 hours), the airplane should be returned back to the base by end
// of the window, if it is not returned - cancel the mission, pay for flown and charge the fine for non-flown leg
// on the other hand, if the "passenger" flight is already flown, this aircraft can be assigned to other missions,
// including by other pilots
// if such aircraft is assigned to another mission, the previous mission is considered finished and payments can be done

// mission cancellation.....
public class BusyBirdsMissionGenerator {
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsMissionGenerator.class);
    private static final int maxJourneyCount = 100;

    private static long lastExecution;

    // todo ak2 later the same logic can be converted into some dedicated storage file
    public static void process(final World world) {
        if (System.currentTimeMillis() - lastExecution < 10 * 60 * 1000) {
            return;
        }
        lastExecution = System.currentTimeMillis();

        Properties properties = loadMissionsFile();

        List<MissionInfo> allMissions = getMissionInfos(properties);
        for (MissionInfo missionInfo : allMissions) {
            if (!missionInfo.isDeleted() && missionInfo.isExpired()) {
                Journeys.Journey journey = world.journeys().byId(missionInfo.getJourneyId()).orElseThrow();
                journey.setBusyBirdsProcessing(false);
                journey.setHeartbeatTime(world.getWorldTime());

                missionInfo.delete();
            }

            if (missionInfo.needsCleanup()) {
                missionInfo.cleanup();
            }
        }
        saveMissionsFile(properties);

        List<MissionInfo> validMissions = allMissions.stream().filter(m -> !m.isDeleted()).toList();
        Set<Integer> allMissionIds = allMissions.stream().map(MissionInfo::getJourneyId).collect(Collectors.toSet());

        if (validMissions.size() >= maxJourneyCount) {
            log.info("there are {} missions to book available, limit is set to {} missions, no need to pick up more", validMissions.size(), maxJourneyCount);
            return;
        }

        log.info("there are {} mission(s) to book available, limit is set to {} missions, let's pick up one more", validMissions.size(), maxJourneyCount);

        List<Journeys.Journey> allJourneys = world.journeys().filter(and(
                        world.journeys().byNoBusyBirdsProcessing(),
                        world.journeys().byStatus(Journeys.Status.LookingForTickets)))
                .filter(j -> j.getPreferredCabinService() == CabinLayout.Service.F
                        || j.getPreferredCabinService() == CabinLayout.Service.J)
                .filter(j -> !allMissionIds.contains(j.getId()))
                .toList();
        Optional<Journeys.Journey> journey = Tools.random(allJourneys);

        if (journey.isEmpty()) {
            log.info("no journeys found");
            return;
        }

        log.info("found {} journeys, selected journey: {} - from {} to {}, pax {}{} - picked up",
                allJourneys.size(),
                journey.get(),
                world.cities().byId(journey.get().getFromCityId()).orElseThrow().getName(),
                world.cities().byId(journey.get().getToCityId()).orElseThrow().getName(),
                journey.get().getPreferredCabinService(),
                journey.get().getGroupSize());

        MissionInfo newMission = MissionInfo.createNew(properties, journey.get());
        log.info("new mission {}", newMission);

        journey.get().setBusyBirdsProcessing(true);

        saveMissionsFile(properties);
    }

    public static Properties loadMissionsFile() {
        Properties properties = new Properties();
        File file = new File("./busy-birds-missions.properties");
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                properties.load(is);
            } catch (IOException e) {
                log.error("Unable to load missions file", e);
                throw new RuntimeException(e);
            }
        }
        return properties;
    }

    public static void saveMissionsFile(Properties properties) {
        File file = new File("./busy-birds-missions.properties");
        try (OutputStream os = new FileOutputStream(file, false)) {
            properties.store(os, null);
        } catch (IOException e) {
            log.error("Unable to save missions file", e);
            throw new RuntimeException(e);
        }
    }

    public static List<MissionInfo> getMissionInfos(Properties properties) {
        List<Integer> ids = properties.keySet().stream()
                .filter(k -> {
                    String s = (String) k;
                    return s.startsWith("mission.") && s.endsWith(".journey.id");
                })
                .map(k -> {
                    String s = (String) k;
                    String[] parts = s.split("\\.");
                    return parts[1];
                })
                .map(Integer::parseInt)
                .toList();

        return ids.stream().map(id -> MissionInfo.load(properties, id)).toList();
    }

    public static Optional<MissionInfo> getMissionInfoById(Properties properties, int missionId) {
        return getMissionInfos(properties).stream()
                .filter(m -> m.getJourneyId() == missionId)
                .findFirst();
    }

    public static class MissionInfo {
        private static final long ONE_HOUR = 60 * 60 * 1000;
        private static final long ONE_DAY = 24 * ONE_HOUR;

        private Properties properties;
        private int journeyId;
        private long validTill;
        private boolean deleted;

        private MissionInfo(Properties properties, int journeyId, long validTill, boolean deleted) {
            this.properties = properties;
            this.journeyId = journeyId;
            this.validTill = validTill;
            this.deleted = deleted;
        }

        public static MissionInfo createNew(Properties properties, Journeys.Journey journey) {
            long validTill = System.currentTimeMillis() + 7 * ONE_DAY;

            properties.setProperty("mission." + journey.getId() + ".journey.id", "ok");
            properties.setProperty("mission." + journey.getId() + ".valid.till", String.valueOf(validTill));

            return new MissionInfo(properties, journey.getId(), validTill, false);
        }

        public static MissionInfo load(Properties properties, int journeyId) {
            String deletedStr = properties.getProperty("mission." + journeyId + ".deleted");
            String validTillStr = properties.getProperty("mission." + journeyId + ".valid.till");

            return new MissionInfo(properties, journeyId, Long.parseLong(validTillStr), "true".equals(deletedStr));
        }

        public long getValidTill() {
            return validTill;
        }

        public boolean isValid() {
            return !isExpired() && !isDeleted();
        }

        public boolean isDeleted() {
            return deleted;
        }

        public int getJourneyId() {
            return journeyId;
        }

        public boolean isExpired() {
            return validTill < System.currentTimeMillis();
        }

        public void delete() {
            deleted = true;

            properties.setProperty("mission." + journeyId + ".deleted", "true");
        }

        public boolean needsCleanup() {
            return validTill + 4 * ONE_DAY < System.currentTimeMillis();
        }

        public void cleanup() {
            properties.remove("mission." + journeyId + ".journey.id");
            properties.remove("mission." + journeyId + ".valid.till");
            properties.remove("mission." + journeyId + ".deleted");
        }
    }
}
