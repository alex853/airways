package net.simforge.airways2.app.user;

import net.simforge.airways2.pilottracker.SimTracker;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The limits of the pilot (awake, duty, flight time in 30 and in 365 days) are the checks of both starts of a flight: over a limit the start is not allowed.
 */
class DashboardPilotLimitsTest {
    private World world;
    private Airports.Airport oakland;
    private Airports.Airport sanFrancisco;
    private Aircrafts.Aircraft jet;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        // a year of the past is needed for the windows of the flight time
        world.setWorldTime(world.getWorldTime() + 400 * 24 * 3600);
        oakland = world.airports().create(37.72, -122.22, "OAK", "KOAK", "Oakland");
        sanFrancisco = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
        jet = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", oakland);
    }

    private Persons.Person pilotWithCredit(final int creditMinutes) {
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.Airport, oakland.getId(), 0, "AK", 0);
        person.setAwakeCredit(creditMinutes, world.getWorldTime());
        return person;
    }

    // a flight of the logbook which ended hoursAgo hours ago and lasted hours hours
    private void flight(final Persons.Person person, final int endedHoursAgo, final int hours) {
        final int now = world.getWorldTime();
        world.pilotLogbook().create(person.getId(), jet.getId(), oakland.getId(), sanFrancisco.getId(),
                now - (endedHoursAgo + hours) * 3600, now - endedHoursAgo * 3600);
    }

    private static List<String> failed(final List<SimTracker.CheckResult> checks) {
        return checks.stream().filter(c -> !c.isResult()).map(SimTracker.CheckResult::getName).toList();
    }

    @Test
    void pilotWithinAllTheLimitsHasFourPassedChecks() {
        final List<SimTracker.CheckResult> checks = FlightDashboardController.pilotLimitChecks(world, pilotWithCredit(10 * 60));

        assertEquals(4, checks.size());
        assertTrue(failed(checks).isEmpty());
    }

    @Test
    void overtiredPilotFailsTheAwakeCheck() {
        final List<String> failed = failed(FlightDashboardController.pilotLimitChecks(world, pilotWithCredit(-60)));

        assertEquals(1, failed.size());
        assertTrue(failed.get(0).startsWith("The awake time"));
    }

    @Test
    void tooMuchDutyFailsTheDutyCheck() {
        final Persons.Person person = pilotWithCredit(10 * 60);
        // 14 h of the flight with the preflight and the postflight make 14 h 50 min of the duty in 24 hours, over the limit of 14 h
        flight(person, 2, 14);

        final List<String> failed = failed(FlightDashboardController.pilotLimitChecks(world, person));

        assertEquals(1, failed.size());
        assertTrue(failed.get(0).startsWith("The duty time"));
    }

    @Test
    void tooMuchFlightTimeFailsTheChecksOfTheFlightTime() {
        final Persons.Person person = pilotWithCredit(10 * 60);
        // 81 h in the last 30 days (three flights of 27 h, each out of the duty window) is over 80 h but far from 800 h
        flight(person, 30, 27);
        flight(person, 60, 27);
        flight(person, 90, 27);

        final List<String> failed = failed(FlightDashboardController.pilotLimitChecks(world, person));

        assertEquals(1, failed.size());
        assertTrue(failed.get(0).startsWith("The flight time in 30 days"));
    }

    @Test
    void tooMuchFlightTimeInAYearFailsTheCheckOfTheYear() {
        final Persons.Person person = pilotWithCredit(10 * 60);
        // 89 flights of 9 h make 801 h, they ended from 40 to 304 days ago
        for (int i = 0; i < 89; i++) {
            flight(person, (40 + i * 3) * 24, 9);
        }

        final List<String> failed = failed(FlightDashboardController.pilotLimitChecks(world, person));

        assertTrue(failed.contains("The flight time in 365 days is within the limit"));
        assertFalse(failed.contains("The flight time in 30 days is within the limit"));
    }

    @Test
    void overALimitBothStartsAreNotAllowedAndTheOtherActionsAreNotTouched() {
        final List<SimTracker.UserAction> actions = List.of(
                new SimTracker.UserAction("start-flight", true, List.of(new SimTracker.CheckResult("The pilot is at the departure airport KOAK", true))),
                new SimTracker.UserAction("start-flight-manual", true, List.of()),
                new SimTracker.UserAction("blocks-off", true, List.of()));
        final List<SimTracker.CheckResult> limits = FlightDashboardController.pilotLimitChecks(world, pilotWithCredit(-60));

        final List<SimTracker.UserAction> result = FlightDashboardController.withPilotLimits(actions, limits);

        assertFalse(result.get(0).isAllowed());
        assertEquals(5, result.get(0).getChecks().size());
        assertFalse(result.get(1).isAllowed());
        assertEquals(4, result.get(1).getChecks().size());
        assertTrue(result.get(2).isAllowed());
        assertTrue(result.get(2).getChecks().isEmpty());
    }

    @Test
    void withinTheLimitsTheStartsKeepTheirVerdictAndShowTheLimitChecks() {
        final List<SimTracker.UserAction> actions = List.of(new SimTracker.UserAction("start-flight-manual", true, List.of()));

        final List<SimTracker.UserAction> result = FlightDashboardController.withPilotLimits(actions,
                FlightDashboardController.pilotLimitChecks(world, pilotWithCredit(10 * 60)));

        assertTrue(result.get(0).isAllowed());
        assertEquals(4, result.get(0).getChecks().size());
    }

    @Test
    void withoutAPilotThereAreNoLimitChecks() {
        final List<SimTracker.UserAction> actions = List.of(new SimTracker.UserAction("start-flight-manual", true, List.of()));

        assertSame(actions, FlightDashboardController.withPilotLimits(actions, List.of()));
    }
}
