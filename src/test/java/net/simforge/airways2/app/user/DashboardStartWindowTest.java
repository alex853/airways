package net.simforge.airways2.app.user;

import net.simforge.airways2.pilottracker.SimTracker;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The start of a flight is open from 3 hours before the planned departure: a flight which is further ahead cannot be started (both starts get the check and are not
 * allowed), the other actions and the statuses after the start do not have it.
 */
class DashboardStartWindowTest {
    private World world;
    private Aircrafts.Aircraft aircraft;
    private Airports.Airport departure;
    private Airports.Airport destination;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        departure = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        destination = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", departure);
    }

    // a flight which departs in the given number of seconds from now
    private FlightMissions.Mission flightIn(final int seconds) {
        final int departureTime = world.getWorldTime() + seconds;
        return world.flightMissions().createDispatchedMission(aircraft, departure, destination, departureTime, departureTime + 2 * Time.ONE_HOUR);
    }

    private static SimTracker.UserStatus connected() {
        return new SimTracker.UserStatus("Connected", null, "At airport", "EGLF", true, false, "0.0",
                List.of(new SimTracker.UserAction("start-flight", true, List.of())));
    }

    private List<SimTracker.UserAction> actions(final FlightMissions.Mission flight, final boolean tracker) {
        return FlightDashboardController.buildActions(world, flight, connected(), tracker, departure.getId());
    }

    private static SimTracker.UserAction named(final List<SimTracker.UserAction> actions, final String name) {
        return actions.stream().filter(a -> a.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void aFlightWhichIsMoreThanThreeHoursAheadCannotBeStartedInEitherWay() {
        final FlightMissions.Mission flight = flightIn(3 * Time.ONE_HOUR + 60);

        final List<SimTracker.UserAction> actions = actions(flight, true);

        for (final String name : List.of("start-flight", "start-flight-manual")) {
            final SimTracker.UserAction start = named(actions, name);
            assertFalse(start.isAllowed(), name);
            final SimTracker.CheckResult check = start.getChecks().get(start.getChecks().size() - 1);
            assertFalse(check.isResult(), name);
            assertTrue(check.getName().startsWith("The flight can be started not earlier than 3 hours before the planned departure (from "), check.getName());
        }
    }

    @Test
    void aFlightWhichIsInFiveDaysCannotBeStarted() {
        final FlightMissions.Mission flight = flightIn(5 * 24 * Time.ONE_HOUR);

        assertFalse(named(actions(flight, false), "start-flight-manual").isAllowed());
    }

    @Test
    void theStartIsOpenExactlyThreeHoursBeforeTheDeparture() {
        assertTrue(named(actions(flightIn(3 * Time.ONE_HOUR), true), "start-flight").isAllowed());
        assertTrue(named(actions(flightIn(3 * Time.ONE_HOUR), false), "start-flight-manual").isAllowed());
        assertTrue(named(actions(flightIn(Time.ONE_HOUR), true), "start-flight").isAllowed());
    }

    @Test
    void aFlightWhichIsLateOrIsDueNowCanBeStarted() {
        // the world has some past, a time before its start is not a time
        world.setWorldTime(world.getWorldTime() + 24 * Time.ONE_HOUR);
        assertTrue(named(actions(flightIn(0), false), "start-flight-manual").isAllowed());
        assertTrue(named(actions(flightIn(-2 * Time.ONE_HOUR), false), "start-flight-manual").isAllowed());
    }

    @Test
    void theStartOpensWithTheTimeOfTheWorld() {
        final FlightMissions.Mission flight = flightIn(5 * Time.ONE_HOUR);
        assertFalse(named(actions(flight, false), "start-flight-manual").isAllowed());

        world.setWorldTime(world.getWorldTime() + 2 * Time.ONE_HOUR);

        assertTrue(named(actions(flight, false), "start-flight-manual").isAllowed());
    }

    @Test
    void theCheckSaysWhenTheStartOpens() {
        final FlightMissions.Mission flight = flightIn(5 * Time.ONE_HOUR);

        final SimTracker.CheckResult check = FlightDashboardController.startWindowCheck(world, flight);

        // 5 hours after the start of the world minus 3 hours
        assertTrue(check.getName().contains("(from " + Time.toLdt(world.getWorldTime() + 2 * Time.ONE_HOUR).toString().substring(0, 10)), check.getName());
        assertTrue(check.getName().endsWith(" UTC)"), check.getName());
        assertEquals(world.getWorldTime() + 2 * Time.ONE_HOUR, FlightMissionHelper.calcEarliestAllowedStartTime(flight));
    }

    @Test
    void theOtherActionsAndTheStatusesAfterTheStartDoNotHaveTheCheck() {
        final FlightMissions.Mission flight = flightIn(5 * 24 * Time.ONE_HOUR);

        // the reschedule is not a start
        final List<SimTracker.UserAction> offered = FlightDashboardController.withStartWindow(world, flight, List.of(
                new SimTracker.UserAction("reschedule", true, List.of()), new SimTracker.UserAction("continue-now", true, List.of())));
        assertTrue(named(offered, "reschedule").isAllowed());
        assertTrue(named(offered, "continue-now").isAllowed());
        assertTrue(named(offered, "reschedule").getChecks().isEmpty());

        // the flight is started: the actions are the same as before the window
        flight.setStatus(FlightMissions.Status.Preflight);
        final List<SimTracker.UserAction> started = List.of(new SimTracker.UserAction("start-flight", true, List.of()));
        assertEquals(started, FlightDashboardController.withStartWindow(world, flight, started));
    }
}
