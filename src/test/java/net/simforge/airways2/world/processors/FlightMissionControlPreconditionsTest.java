package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every step of the flight (blocks off, takeoff, landing, blocks on, finish) is allowed from one status of the mission only.
 */
class FlightMissionControlPreconditionsTest {
    private World world;
    private FlightMissionControl control;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private FlightMissions.Mission mission;
    private Aircrafts.Aircraft aircraft;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.flightMissionControl();
        airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, departure, departure + 2 * Time.ONE_HOUR);
    }

    /** The step must be refused in every status except the allowed one, and a refused step changes nothing. */
    private void assertStepIsAllowedFrom(final FlightMissions.Status allowed, final Consumer<FlightMissions.Mission> step) {
        for (final FlightMissions.Status status : EnumSet.complementOf(EnumSet.of(allowed))) {
            mission.setStatus(status);

            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> step.accept(mission), "status " + status);
            assertTrue(e.getMessage().contains("requires status " + allowed), e.getMessage());
            assertTrue(e.getMessage().contains("actual status is " + status), e.getMessage());
            assertEquals(status, mission.getStatus());
            assertEquals(0, mission.getActualDepartureWorldTime());
            assertEquals(0, mission.getActualTakeoffWorldTime());
            assertEquals(0, mission.getActualLandingWorldTime());
            assertEquals(0, mission.getActualArrivalWorldTime());
            assertEquals(Aircrafts.LocationStatus.ParkedAtAirport, aircraft.getLocationStatus());
            assertEquals(airportA.getId(), aircraft.getLocationAirportId());
        }
    }

    @Test
    public void test__blocks_off_requires_preflight() {
        assertStepIsAllowedFrom(FlightMissions.Status.Preflight, control::blocksOff);
    }

    @Test
    public void test__takeoff_requires_departure() {
        assertStepIsAllowedFrom(FlightMissions.Status.Departure, control::takeoff);
    }

    @Test
    public void test__landing_requires_flying() {
        assertStepIsAllowedFrom(FlightMissions.Status.Flying, m -> control.landing(m, airportB));
    }

    @Test
    public void test__blocks_on_requires_arrival() {
        assertStepIsAllowedFrom(FlightMissions.Status.Arrival, control::blocksOn);
    }

    @Test
    public void test__finish_requires_postflight() {
        assertStepIsAllowedFrom(FlightMissions.Status.Postflight, control::finish);
    }

    @Test
    public void test__steps_go_one_after_another_in_the_regular_order() {
        assertTrue(control.start(mission).started());
        assertEquals(FlightMissions.Status.Preflight, mission.getStatus());
        control.blocksOff(mission);
        assertEquals(FlightMissions.Status.Departure, mission.getStatus());
        control.takeoff(mission);
        assertEquals(FlightMissions.Status.Flying, mission.getStatus());
        control.landing(mission, airportB);
        assertEquals(FlightMissions.Status.Arrival, mission.getStatus());
        control.blocksOn(mission);
        assertEquals(FlightMissions.Status.Postflight, mission.getStatus());
        control.finish(mission);
        assertEquals(FlightMissions.Status.Finished, mission.getStatus());
    }

    @Test
    public void test__a_step_can_not_be_repeated() {
        control.start(mission);
        control.blocksOff(mission);

        assertThrows(IllegalArgumentException.class, () -> control.blocksOff(mission));
        assertEquals(FlightMissions.Status.Departure, mission.getStatus());
    }
}
