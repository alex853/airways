package net.simforge.airways2.app.user;

import net.simforge.airways2.pilottracker.SimTracker;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Without the sim tracker the dashboard is in the manual mode: the actions it offers follow the status of the flight and of its transport flight.
 */
class DashboardManualActionsTest {
    private World world;
    private FlightMissions.Mission mission;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final Airports.Airport a = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        final Airports.Airport b = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", a);
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, a, b, departure, departure + 2 * Time.ONE_HOUR);
    }

    private List<String> names() {
        return FlightDashboardController.buildManualActions(world, mission).stream().map(SimTracker.UserAction::getName).toList();
    }

    @Test
    void ferryFlightGoesThroughAllTheSteps() {
        assertEquals(List.of("start-flight"), names());

        mission.setStatus(FlightMissions.Status.Preflight);
        assertEquals(List.of("blocks-off"), names());

        mission.setStatus(FlightMissions.Status.Departure);
        assertEquals(List.of("takeoff"), names());

        mission.setStatus(FlightMissions.Status.Flying);
        assertEquals(List.of("landing"), names());

        mission.setStatus(FlightMissions.Status.Arrival);
        assertEquals(List.of("blocks-on"), names());

        mission.setStatus(FlightMissions.Status.Postflight);
        assertEquals(List.of("finish-flight"), names());

        mission.setStatus(FlightMissions.Status.Finished);
        assertEquals(List.of(), names());
    }

    @Test
    void passengerFlightWaitsForBoardingAndDeboarding() {
        final TransportFlights.Flight tf = world.transportFlightControl().createTransportFlight(mission);

        mission.setStatus(FlightMissions.Status.Preflight);
        tf.setStatus(TransportFlights.Status.WaitingForBoarding);
        assertEquals(List.of("start-boarding"), names());

        tf.setStatus(TransportFlights.Status.Boarding);
        assertEquals(List.of(), names());

        tf.setStatus(TransportFlights.Status.WaitingForDeparture);
        assertEquals(List.of("blocks-off"), names());

        mission.setStatus(FlightMissions.Status.Postflight);
        tf.setStatus(TransportFlights.Status.WaitingForDeboarding);
        assertEquals(List.of("start-deboarding"), names());

        tf.setStatus(TransportFlights.Status.Deboarding);
        assertEquals(List.of(), names());

        tf.setStatus(TransportFlights.Status.Finished);
        assertEquals(List.of("finish-flight"), names());
    }

    @Test
    void manualLandingIsNotAllowedBeforeThreeQuartersOfThePlannedDuration() {
        mission.setStatus(FlightMissions.Status.Flying);
        final int takeoff = world.getWorldTime();
        mission.setActualTakeoffWorldTime(takeoff);
        // planned duration is 2 hours, so the earliest landing is 1.5 hours after the takeoff

        world.setWorldTime(takeoff + 90 * 60 - 1);
        final SimTracker.UserAction early = FlightDashboardController.buildManualActions(world, mission).get(0);
        assertEquals("landing", early.getName());
        assertFalse(early.isAllowed());
        assertFalse(early.getChecks().get(0).isResult());

        world.setWorldTime(takeoff + 90 * 60);
        assertTrue(FlightDashboardController.buildManualActions(world, mission).get(0).isAllowed());
    }

    @Test
    void manualActionsAreAllowed() {
        assertTrue(FlightDashboardController.buildManualActions(world, mission).stream().allMatch(SimTracker.UserAction::isAllowed));
    }
}
