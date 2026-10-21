package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The earliest time of a manual landing is counted from the planned flying time (without the taxi), so a flight which goes by the plan can land when it
 * arrives.
 */
class FlightMissionHelperLandingTest {
    private World world;
    private Airports.Airport from;
    private Airports.Airport to;
    private Aircrafts.Aircraft aircraft;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        from = world.airports().create(36.59, -115.67, "INS", "KINS", "Indian Springs");
        to = world.airports().create(37.36, -121.93, "SJC", "KSJC", "San Jose");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("GA7C", "GL7"), "BB-GLA", from);
    }

    private FlightMissions.Mission mission(final int blockMinutes) {
        final int departure = world.getWorldTime() + 3600;
        return world.flightMissions().createDispatchedMission(aircraft, from, to, departure, departure + blockMinutes * 60);
    }

    @Test
    void theFlightWhichGoesByThePlanCanLandWhenItArrives() {
        // 71 minutes block to block: 10 minutes of taxi, 51 minutes of flying, 10 minutes of taxi
        final FlightMissions.Mission mission = mission(71);
        final int takeoff = mission.getPlannedDepartureWorldTime() + 10 * 60;
        mission.setActualTakeoffWorldTime(takeoff);

        final int earliest = FlightMissionHelper.calcEarliestAllowedLandingTime(mission);

        // three quarters of 51 minutes after the takeoff, and that is before the planned landing (the takeoff + 51 minutes)
        assertEquals(takeoff + (int) Math.round(51 * 60 * 0.75), earliest);
        assertTrue(earliest < takeoff + 51 * 60);
    }

    @Test
    void aShortBlockTimeGivesNoNegativeFlyingTime() {
        final FlightMissions.Mission mission = mission(15);
        mission.setActualTakeoffWorldTime(mission.getPlannedDepartureWorldTime());

        assertEquals(mission.getActualTakeoffWorldTime(), FlightMissionHelper.calcEarliestAllowedLandingTime(mission));
    }

    @Test
    void theTimeIsCountedFromTheActualTakeoff() {
        final FlightMissions.Mission mission = mission(120);
        // the takeoff is much later than planned
        mission.setActualTakeoffWorldTime(mission.getPlannedDepartureWorldTime() + 45 * 60);

        assertEquals(mission.getActualTakeoffWorldTime() + 75 * 60, FlightMissionHelper.calcEarliestAllowedLandingTime(mission));
    }
}
