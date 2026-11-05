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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Resolve conflicts" of the roster: the flights in conflict are moved later, each the least that the gap needs, the flights before the first conflict and the ones which are far
 * enough stay.
 */
class RosterResolveConflictsTest {
    private static final int USER_ID = 7;

    private World world;
    private Airports.Airport departure;
    private Airports.Airport destination;
    private Aircrafts.Aircraft aircraft;
    private int base;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        departure = world.airports().create(51.5, -0.1, "AAA", "AAAA", "Alpha");
        destination = world.airports().create(51.5, 4.0, "BBB", "BBBB", "Bravo");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", departure);
        base = world.getWorldTime() + Time.ONE_HOUR;
    }

    private FlightMissions.Mission flight(final int departureMinute, final int durationMinutes) {
        final int departureTime = base + departureMinute * Time.ONE_MINUTE;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, departure, destination, departureTime, departureTime + durationMinutes * Time.ONE_MINUTE);
        mission.setUserId(USER_ID);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        return mission;
    }

    @Test
    void aRosterWithoutConflictsHasNothingToMove() {
        flight(0, 60);
        flight(120, 60);

        final RosterCascade.Plan plan = world.flightMissionControl().resolveConflictsCascade(USER_ID);

        assertTrue(plan.moves().isEmpty());
        assertTrue(plan.isPossible());
    }

    @Test
    void theFlightsInConflictAreMovedTheLeastAndTheFarOnesStay() {
        final FlightMissions.Mission first = flight(0, 60);
        final FlightMissions.Mission second = flight(105, 60);
        final FlightMissions.Mission third = flight(195, 60);
        final FlightMissions.Mission fourth = flight(400, 60);
        // the first flight has not left an hour and a half after its plan: it lands at 150, the second one needs 180, the third one 270 (the second lands at 240)
        world.setWorldTime(base + 90 * Time.ONE_MINUTE);

        final RosterCascade.Plan plan = world.flightMissionControl().resolveConflictsCascade(USER_ID);

        assertTrue(plan.isPossible());
        assertEquals(2, plan.moves().size());
        assertEquals(second.getId(), plan.moves().get(0).mission().getId());
        assertEquals(base + 180 * Time.ONE_MINUTE, plan.moves().get(0).newDeparture());
        assertEquals(third.getId(), plan.moves().get(1).mission().getId());
        assertEquals(base + 270 * Time.ONE_MINUTE, plan.moves().get(1).newDeparture());

        world.flightMissionControl().resolveConflicts(USER_ID);

        assertEquals(base, first.getPlannedDepartureWorldTime());
        assertEquals(base + 180 * Time.ONE_MINUTE, second.getPlannedDepartureWorldTime());
        assertEquals(base + 240 * Time.ONE_MINUTE, second.getPlannedArrivalWorldTime());
        assertEquals(base + 270 * Time.ONE_MINUTE, third.getPlannedDepartureWorldTime());
        assertEquals(base + 400 * Time.ONE_MINUTE, fourth.getPlannedDepartureWorldTime());
        RosterForecast.of(world, USER_ID).forEach(entry -> assertNull(entry.conflict()));
    }

    @Test
    void aFlightWhichCannotBeMovedStopsTheWholePlan() {
        flight(0, 60);
        final FlightMissions.Mission second = flight(105, 60);
        world.setWorldTime(base + 90 * Time.ONE_MINUTE);
        // a flight which has been dispatched by a scheduled flight cannot be moved: use the status, a started flight is not moved
        second.setStatus(FlightMissions.Status.Departure);

        final RosterCascade.Plan plan = world.flightMissionControl().resolveConflictsCascade(USER_ID);

        assertEquals(1, plan.problems().size());
        assertEquals(second.getId(), plan.problems().get(0).mission().getId());
    }

    @Test
    void aFlightMovedWithTheNextOnesPushesOnlyTheFlightsWhichNeedIt() {
        final FlightMissions.Mission first = flight(0, 60);
        final FlightMissions.Mission second = flight(120, 60);
        final FlightMissions.Mission third = flight(190, 60);
        final FlightMissions.Mission fourth = flight(600, 60);

        // the second flight goes 90 minutes later: it lands at 270, the third one needs 300, the fourth one is far enough
        final int newDeparture = base + 210 * Time.ONE_MINUTE;
        final RosterCascade.Plan plan = world.flightMissionControl().rescheduleWithNextCascade(second, newDeparture);

        assertTrue(plan.isPossible());
        assertEquals(2, plan.moves().size());
        assertEquals(second.getId(), plan.moves().get(0).mission().getId());
        assertEquals(third.getId(), plan.moves().get(1).mission().getId());
        assertEquals(base + 300 * Time.ONE_MINUTE, plan.moves().get(1).newDeparture());

        world.flightMissionControl().rescheduleWithNext(second, newDeparture);

        assertEquals(base, first.getPlannedDepartureWorldTime());
        assertEquals(newDeparture, second.getPlannedDepartureWorldTime());
        assertEquals(base + 300 * Time.ONE_MINUTE, third.getPlannedDepartureWorldTime());
        assertEquals(base + 600 * Time.ONE_MINUTE, fourth.getPlannedDepartureWorldTime());
    }

    @Test
    void aFlightCannotBeMovedWithTheNextOnesTooCloseToTheFlightBeforeIt() {
        flight(0, 60);
        final FlightMissions.Mission second = flight(120, 60);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> world.flightMissionControl().rescheduleWithNextCascade(second, base + 80 * Time.ONE_MINUTE));
    }
}
