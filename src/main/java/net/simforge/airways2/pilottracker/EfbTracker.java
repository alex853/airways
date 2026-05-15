package net.simforge.airways2.pilottracker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.simforge.airways2.app.WorldAccess;
import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.pilottracker.track.TrackPosition;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class EfbTracker {
    private static final Logger log = LoggerFactory.getLogger(EfbTracker.class);

    private static final EfbTracker INSTANCE = new EfbTracker();

    public static EfbTracker get() {
        return INSTANCE;
    }

    private static final Map<Integer, Context> userContexts = new ConcurrentHashMap<>();

    public void processPosrep(WorldAccess worldAccess, int userId, String posrep) {
        TrackPosition newPosition = worldAccess.read(world -> parse(world, posrep)); // todo ak1 this can be improved by caching airport list

        savePosrep(userId, posrep);

        Context context = userContexts
                .computeIfAbsent(userId, id -> Context.empty())
                .processPosition(newPosition);

        // todo ak1 isValid?

        if (context.isTakeoff()) {
            final FlightMissions.Mission mission = mission_takeoff(worldAccess, context);
            log.info("{} - Event 'takeoff'", missionLogHead(userId, mission));
            FlightStats.event("efb - takeoff");
        } else if (context.isLanding()) {
            final FlightMissions.Mission mission = mission_landing(worldAccess, context);

            log.info("{} - Event 'landing'", missionLogHead(userId, mission));
            FlightStats.event("efb - landing");
        }

        userContexts.put(userId, context);
    }

    public Optional<UserStatus> getUserStatus(int userId) {
        Context context = userContexts.get(userId);
        if (context == null) {
            return Optional.empty();
        }

        long lastSeen = 0;
        String status = null;
        String airportIcao = null;
        if (context.currentPosition != null) {
            lastSeen = context.currentPosition.getLastSeen();
            airportIcao = context.currentPosition.getAirportIcao();
            if (context.currentPosition.isOnGround()) {
                status = airportIcao != null ? "At airport" : "On ground out of any airport";
            } else {
                status = "In flight";
            }
        }

        return Optional.of(
                new UserStatus(
                        lastSeen,
                        context.getFlightMissionId(),
                        status,
                        airportIcao,
                        0));
    }

    public void notifyMissionStarted(int userId, int flightMissionId) {
        Context context = userContexts
                .computeIfAbsent(userId, id -> Context.empty())
                .notifyMissionStarted(flightMissionId);
        userContexts.put(userId, context);
        FlightStats.event("efb - notify-mission-started");
    }

    public void notifyMissionFinished(int userId) {
        Context context = userContexts
                .computeIfAbsent(userId, id -> Context.empty())
                .notifyMissionFinished();
        userContexts.put(userId, context);
        FlightStats.event("efb - notify-mission-finished");
    }

    private static class Context {
        private final TrackPosition currentPosition;
        @Getter
        private final int flightMissionId;
        @Getter
        private boolean takeoff;
        @Getter
        private boolean landing;

        private Context(TrackPosition currentPosition, int flightMissionId) {
            this.currentPosition = currentPosition;
            this.flightMissionId = flightMissionId;
        }

        public static Context empty() {
            return new Context(null, 0);
        }

        public Context processPosition(TrackPosition newPosition) {
            if (currentPosition == null) {
                return new Context(newPosition, 0);
            }

            Context newContext = new Context(newPosition, flightMissionId);
            newContext.takeoff = !newPosition.isOnGround() && currentPosition.isOnGround();
            newContext.landing = newPosition.isOnGround() && !currentPosition.isOnGround();
            return newContext;
        }

        public Context notifyMissionStarted(int flightMissionId) {
            return new Context(currentPosition, flightMissionId);
        }

        public Context notifyMissionFinished() {
            return new Context(currentPosition, 0);
        }
    }

    @AllArgsConstructor
    @Data
    public static class UserStatus {
        private long lastSeen;
        private int flightMissionId;
        private String status;
        private String airportIcao;
        private int trackTailGs;
    }

    private FlightMissions.Mission mission_takeoff(WorldAccess worldAccess, Context context) {
        return worldAccess.modifySync(world -> {
            int flightMissionId = context.getFlightMissionId();
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_takeoff <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("efb - erroneous case - mission_takeoff - fm not found");
                return null;
            }
            final FlightMissions.Mission mission = mission1.get();

            if (mission.getStatus() == FlightMissions.Status.Departure) {
                world.flightMissionControl().takeoff(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event("efb - takeoff");

            return mission;
        });
    }

    private FlightMissions.Mission mission_landing(WorldAccess worldAccess, Context context) {
        return worldAccess.modifySync(world -> {
            int flightMissionId = context.getFlightMissionId();
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_landing <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("efb - erroneous case - mission_landing - fm not found");
                return null;
            }
            final FlightMissions.Mission mission = mission1.get();

            String landingAirportIcao = context.currentPosition.getAirportIcao();
            final Airports.Airport landingAirport = world.airports().byIcao(landingAirportIcao).orElseThrow();

            if (mission.getStatus() == FlightMissions.Status.Flying) {
                world.flightMissionControl().landing(mission, landingAirport);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event("efb - landing");

            return mission;
        });
    }

    private static TrackPosition parse(World world, String posrep) {
        String[] strs = posrep.split(",");

        String timeStr = strs[0]; // todo ak1 parse it
        String onGroundStr = strs[1];
        String latStr = strs[2];
        String lngStr = strs[3];
        String gsStr = strs[4];
        String hdgStr = strs[5];

        boolean onGround = Integer.parseInt(onGroundStr) == 1;
        double lat = Double.parseDouble(latStr);
        double lng = Double.parseDouble(lngStr);
        double gs = Double.parseDouble(gsStr); // todo ak1 really needed?
        int hdg = Integer.parseInt(hdgStr); // todo ak1 really needed?

        Geo.Coords coords = Geo.coords(lat, lng);

        Optional<Airports.Airport> airport = onGround
                ? world.airports().all().filter(a -> Geo.distance(a.getCoords(), coords) < 2.5).findFirst()
                : Optional.empty();

        return new TrackPosition(
                System.currentTimeMillis(),
                onGround,
                coords,
                airport.map(Airports.Airport::getIcao).orElse(null));
    }

    private void savePosrep(int userId, String posrep) {
        try {
            File file = new File("./efb-tracker/user-posreps/user-" + userId + ".csv");
            file.getParentFile().mkdirs();
            String content = file.exists() ? IOHelper.loadFile(file) : "";
            content += posrep + "\n";
            IOHelper.saveFile(file, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String missionLogHead(int userId, FlightMissions.Mission mission) {
        return String.format("[%s] f/m #%s, a/c #%s",
                userId,
                mission != null ? mission.getId() : "-",
                mission != null ? mission.getAircraftId() : "-");
    }
}
