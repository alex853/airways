package net.simforge.airways2.world.computations;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

public class FlightMissionToTimeline {
    private static final Logger log = LoggerFactory.getLogger(FlightMissionToTimeline.class);

    /**
     * The timeline of the mission with the estimates of a flight in the air counted from where the aircraft is, not from the plan: the estimated landing is the time now plus the
     * time of the flight from the position and the altitude of the aircraft to the airport it flies to (the divert if it is declared, the destination otherwise), see
     * SimpleFlight.fromPosition; the estimated blocks on follows the landing. It is for the flights of pilots (the PC mode: the manual flights, the flights tracked by the sim
     * tracker and via VATSIM). The AI flights (NPC) keep the plan, their steps go by the estimates of the plan. Before the takeoff and after the landing the estimates are
     * those of the plan and of the actual times.
     */
    public static FlightTimeline byMissionAndAircraftPosition(final World world, final FlightMissions.Mission mission) {
        final FlightTimeline timeline = byMission(mission);
        if (mission.getCharacterMode() != FlightMissions.CharacterMode.PC
                || mission.getActualTakeoffWorldTime() == 0
                || mission.getActualLandingWorldTime() != 0) {
            return timeline;
        }

        final Optional<Aircrafts.Aircraft> aircraft = world.aircrafts().byId(mission.getAircraftId());
        final Optional<Airports.Airport> target = world.airports().byId(mission.getTargetAirportId());
        final Optional<String> typeIcao = aircraft.flatMap(a -> world.aircraftTypes().byId(a.getAircraftTypeId())).map(AircraftTypes.AircraftType::getIcao);
        if (aircraft.isEmpty() || target.isEmpty() || typeIcao.isEmpty()) {
            return timeline;
        }

        try {
            final SimpleFlight remaining = SimpleFlight.fromPosition(
                    aircraft.get().getLocationCoords(),
                    aircraft.get().getLocationAltitude(),
                    target.get().getCoords(),
                    AircraftPerformanceData.getData(typeIcao.get()));
            timeline.getLanding().setEstimatedTime(Time.toLdt(world.getWorldTime()).plus(remaining.getTotalTime()));
        } catch (final NoSuchElementException e) {
            log.warn("f/m #{} - no performance data of {}, the estimates of the flight stay by the plan", mission.getId(), typeIcao.get());
        }
        return timeline;
    }

    public static FlightTimeline byMission(final FlightMissions.Mission mission) {
        FlightTimeline timeline = FlightTimeline.byScheduledDepartureArrivalTime(
                Time.toLdt(mission.getPlannedDepartureWorldTime()),
                Time.toLdt(mission.getPlannedArrivalWorldTime()));

        final LocalDateTime actualDepartureTime = Time.toLdtOrNull(mission.getActualDepartureWorldTime());
        if (actualDepartureTime != null) {
            timeline.getBlocksOff().setActualTime(actualDepartureTime);
        }

        final LocalDateTime actualTakeoffTime = Time.toLdtOrNull(mission.getActualTakeoffWorldTime());
        if (actualTakeoffTime != null) {
            timeline.getTakeoff().setActualTime(actualTakeoffTime);
        }

        final LocalDateTime actualLandingTime = Time.toLdtOrNull(mission.getActualLandingWorldTime());
        if (actualLandingTime != null) {
            timeline.getLanding().setActualTime(actualLandingTime);
        }

        final LocalDateTime actualArrivalTime = Time.toLdtOrNull(mission.getActualArrivalWorldTime());
        if (actualArrivalTime != null) {
            timeline.getBlocksOn().setActualTime(actualArrivalTime);
        }

        return timeline;
    }
}
