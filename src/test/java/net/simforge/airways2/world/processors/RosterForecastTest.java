package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The forecast of the roster: the expected times, and the conflicts which go down the roster (red where the flight before is as it is, yellow where the flights before would have
 * to be moved).
 */
class RosterForecastTest {
    private static final int USER_ID = 7;

    private World world;
    private Airports.Airport departure;
    private Airports.Airport destination;
    private Airports.Airport divertAirport;
    private Aircrafts.Aircraft aircraft;
    private int base;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        departure = world.airports().create(51.5, -0.1, "AAA", "AAAA", "Alpha");
        destination = world.airports().create(51.5, 4.0, "BBB", "BBBB", "Bravo");
        divertAirport = world.airports().create(51.5, 1.0, "CCC", "CCCC", "Charlie");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", departure);
        base = world.getWorldTime() + Time.ONE_HOUR;
    }

    // a flight of the user which departs the given minutes after the base time and flies the given minutes
    private FlightMissions.Mission flight(final int departureMinute, final int durationMinutes) {
        final int departureTime = base + departureMinute * Time.ONE_MINUTE;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, departure, destination, departureTime, departureTime + durationMinutes * Time.ONE_MINUTE);
        mission.setUserId(USER_ID);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        return mission;
    }

    private List<RosterForecast.Entry> forecast() {
        return RosterForecast.of(world, USER_ID);
    }

    @Test
    void aRosterWithGapsHasNoConflictsAndTheExpectedTimesAreThePlanned() {
        flight(0, 60);
        flight(120, 60);

        final List<RosterForecast.Entry> roster = forecast();

        assertEquals(2, roster.size());
        for (final RosterForecast.Entry entry : roster) {
            assertNull(entry.conflict());
            assertEquals(entry.mission().getPlannedDepartureWorldTime(), entry.expectedDeparture());
            assertEquals(entry.mission().getPlannedArrivalWorldTime(), entry.expectedArrival());
        }
    }

    @Test
    void aLateFlightMakesTheNextOneRedAndTheOneAfterItYellow() {
        final FlightMissions.Mission first = flight(0, 60);
        final FlightMissions.Mission second = flight(105, 60);
        final FlightMissions.Mission third = flight(195, 60);
        final FlightMissions.Mission fourth = flight(400, 60);
        // an hour and a half after the planned departure the first flight has not left: it lands at 150 minutes, the next flight needs 30 minutes after that
        world.setWorldTime(base + 90 * Time.ONE_MINUTE);

        final List<RosterForecast.Entry> roster = forecast();

        assertEquals(List.of(first.getId(), second.getId(), third.getId(), fourth.getId()), roster.stream().map(e -> e.mission().getId()).toList());
        assertNull(roster.get(0).conflict());
        assertEquals(base + 150 * Time.ONE_MINUTE, roster.get(0).expectedArrival());

        // the second flight: planned at 105, the least departure is 180, it is short by 75 minutes; red, the flight before it is as it is
        final RosterForecast.Conflict c2 = roster.get(1).conflict();
        assertNotNull(c2);
        assertEquals(RosterForecast.Level.RED, c2.level());
        assertEquals(75, c2.shortfallMinutes());
        assertEquals(first.getId(), c2.cause().getId());
        assertFalse(c2.estimate());
        assertEquals(base + 180 * Time.ONE_MINUTE, c2.suggestedDeparture());
        assertEquals(base + 180 * Time.ONE_MINUTE, roster.get(1).expectedDeparture());
        assertEquals(base + 240 * Time.ONE_MINUTE, roster.get(1).expectedArrival());

        // the third flight: planned at 195, after the move of the second one it lands at 240 and 270 is needed, short by 75; yellow, it comes from the move
        final RosterForecast.Conflict c3 = roster.get(2).conflict();
        assertNotNull(c3);
        assertEquals(RosterForecast.Level.YELLOW, c3.level());
        assertEquals(75, c3.shortfallMinutes());
        assertEquals(second.getId(), c3.cause().getId());

        // the fourth one starts late enough
        assertNull(roster.get(3).conflict());
    }

    @Test
    void theSuggestedDepartureIsRoundedUpToFiveMinutes() {
        flight(0, 60);
        flight(105, 60);
        // the first flight lands at 150 minutes plus 20 seconds of the world time: the least departure is not on a five minutes mark
        world.setWorldTime(base + 90 * Time.ONE_MINUTE + 20);

        final RosterForecast.Conflict conflict = forecast().get(1).conflict();

        assertNotNull(conflict);
        assertEquals(0, conflict.suggestedDeparture() % (5 * Time.ONE_MINUTE));
        assertTrue(conflict.suggestedDeparture() >= base + 180 * Time.ONE_MINUTE + 20);
        assertTrue(conflict.suggestedDeparture() < base + 180 * Time.ONE_MINUTE + 20 + 5 * Time.ONE_MINUTE);
    }

    @Test
    void aDivertedFlightWithPassengersMakesTheNextOneRedByTheEstimateAndTheOneAfterItYellow() {
        final FlightMissions.Mission first = flight(0, 60);
        flight(30, 60);
        flight(150, 60);
        final FlightMissionControl control = world.flightMissionControl();
        control.start(first);
        control.setTrackingMode(first, FlightMissions.TrackingMode.Manual);
        control.blocksOff(first);
        control.takeoff(first);
        first.setDivertAirportId(divertAirport.getId());
        final TransportFlights.Flight tf = world.transportFlightControl().createTransportFlight(first, CabinLayout.Y(99));
        tf.setStatus(TransportFlights.Status.Flying);
        tf.setPaxOnBoard(50);

        final List<RosterForecast.Entry> roster = forecast();

        final RosterForecast.Entry entry = roster.get(0);
        assertTrue(entry.divertReminder());
        assertEquals(divertAirport.getId(), entry.divertAirport().getId());
        // the expected arrival of a diverted flight is the arrival at the divert airport, the delivery to the destination of the plan is after an hour on the ground there
        assertTrue(entry.expectedArrival() > world.getWorldTime());
        assertTrue(entry.deliveryNotBefore() > entry.expectedArrival() + Time.ONE_HOUR);

        final RosterForecast.Conflict c2 = roster.get(1).conflict();
        assertNotNull(c2);
        assertEquals(RosterForecast.Level.RED, c2.level());
        assertTrue(c2.estimate());
        // the next flight needs an hour after the arrival of a diverted flight
        assertEquals(entry.deliveryNotBefore() + Time.ONE_HOUR, roster.get(1).expectedDeparture());

        final RosterForecast.Conflict c3 = roster.get(2).conflict();
        assertNotNull(c3);
        assertEquals(RosterForecast.Level.YELLOW, c3.level());
        assertTrue(c3.estimate());
    }

    // ---- the times which keep a flight between its neighbours (for the move of the flight)

    private static final int STEP = RosterForecast.RESCHEDULE_OPTION_STEP;

    private static int up(final int time) {
        return (time + STEP - 1) / STEP * STEP;
    }

    private static int down(final int time) {
        return time / STEP * STEP;
    }

    private RosterForecast.Window window(final int index) {
        return RosterForecast.rescheduleWindow(world, forecast(), index).orElseThrow();
    }

    @Test
    void theWindowOfAFlightIsFromTheGapAfterTheFlightBeforeToTheLastDepartureWhichLeavesTheGapBeforeTheFlightAfter() {
        assertEquals(0, base % STEP);
        flight(0, 60);
        flight(150, 60);
        flight(300, 60);

        final RosterForecast.Window window = window(1);

        // the first flight lands at 60, the gap of 30 minutes: 90; the third one leaves at 300, the gap of 30 minutes and the flight of 60 minutes: 210
        assertEquals(up(base + 90 * Time.ONE_MINUTE), window.earliestDeparture());
        assertEquals(down(base + 210 * Time.ONE_MINUTE), window.latestDeparture());
        assertFalse(window.isEmpty());
    }

    @Test
    void theFlightMovedToTheEdgesOfTheWindowHasNoConflictWithItsNeighbours() {
        final FlightMissions.Mission first = flight(0, 60);
        final FlightMissions.Mission second = flight(150, 60);
        flight(300, 60);
        final FlightMissionControl control = world.flightMissionControl();

        final RosterForecast.Window window = window(1);
        control.reschedule(second, (window.earliestDeparture() - second.getPlannedDepartureWorldTime()) / Time.ONE_MINUTE);
        assertTrue(forecast().stream().allMatch(e -> e.conflict() == null));
        assertEquals(window.earliestDeparture(), second.getPlannedDepartureWorldTime());

        control.reschedule(second, (window.latestDeparture() - second.getPlannedDepartureWorldTime()) / Time.ONE_MINUTE);
        assertTrue(forecast().stream().allMatch(e -> e.conflict() == null));
        assertEquals(window.latestDeparture(), second.getPlannedDepartureWorldTime());
        assertEquals(first.getId(), forecast().get(0).mission().getId());
    }

    @Test
    void theWindowOfTheFirstFlightStartsAfterTheReserveOfTheDeparture() {
        flight(60, 60);

        final RosterForecast.Window window = window(0);

        assertEquals(up(world.getWorldTime() + FlightMissionControl.RESCHEDULE_MIN_DEPARTURE_RESERVE), window.earliestDeparture());
    }

    @Test
    void aFlightWithoutTheOneAfterItCanBeMovedTwelveHoursLater() {
        final FlightMissions.Mission only = flight(0, 60);

        assertEquals(down(only.getPlannedDepartureWorldTime() + RosterForecast.RESCHEDULE_WITHOUT_NEXT_FLIGHT), window(0).latestDeparture());
    }

    @Test
    void theWindowOfAFlightIsByThePlannedDepartureOfTheFlightAfterItNotByTheExpectedOne() {
        assertEquals(0, base % STEP);
        flight(0, 60);
        flight(90, 60);
        // the third flight is planned 10 minutes after the landing of the second one: it is in conflict and would leave at 180 (expected), but it is planned at 160
        flight(160, 60);

        final List<RosterForecast.Entry> roster = forecast();
        assertEquals(base + 180 * Time.ONE_MINUTE, roster.get(2).expectedDeparture());
        final RosterForecast.Window window = RosterForecast.rescheduleWindow(world, roster, 1).orElseThrow();

        // the second flight lands the gap before 160 (planned): it leaves not later than 70, and not earlier than 90 (the gap after the first one): there is no time without a conflict
        assertEquals(up(base + 90 * Time.ONE_MINUTE), window.earliestDeparture());
        assertEquals(down(base + 70 * Time.ONE_MINUTE), window.latestDeparture());
        assertTrue(window.isEmpty());
    }

    @Test
    void aFlightWhichIsAlreadyAtTheEarliestTimeAndWhoseNextFlightIsInConflictHasNoOptions() {
        assertEquals(0, base % STEP);
        flight(0, 60);
        flight(90, 60);
        flight(160, 60);

        final RosterForecast.Choices choices = RosterForecast.rescheduleChoices(world, forecast(), 1).orElseThrow();

        assertTrue(choices.options().isEmpty());
        assertTrue(choices.atEarliest());
        assertEquals(forecast().get(0).mission().getId(), choices.before().mission().getId());
        assertEquals(forecast().get(2).mission().getId(), choices.after().mission().getId());
    }

    @Test
    void theOptionsTellWhatEachTimeDoesToTheFlightAfterIt() {
        assertEquals(0, base % STEP);
        flight(0, 60);
        flight(150, 60);
        // the third flight is planned at 190: the second flight lands at 210 and the third one is in conflict
        flight(190, 60);

        final RosterForecast.Choices choices = RosterForecast.rescheduleChoices(world, forecast(), 1).orElseThrow();

        // from the earliest (90) to the current departure (150, it is not an option): at 90 the second flight lands at 150 and the third one has its gap, later it is short
        final int minute = Time.ONE_MINUTE;
        assertEquals(List.of(
                        new RosterForecast.Option(base + 90 * minute, base + 190 * minute, 0),
                        new RosterForecast.Option(base + 105 * minute, base + 195 * minute, 5),
                        new RosterForecast.Option(base + 120 * minute, base + 210 * minute, 20),
                        new RosterForecast.Option(base + 135 * minute, base + 225 * minute, 35)),
                choices.options());
        assertFalse(choices.atEarliest());
    }

    @Test
    void aFlightWhichIsInConflictWithTheFlightBeforeItHasTheTimesFromTheEarliestOneAndWhatTheyDoToTheFlightAfterIt() {
        assertEquals(0, base % STEP);
        flight(0, 60);
        // the second flight is planned at 70, the first one lands at 60 and the gap is 30 minutes: it is in conflict, the earliest time is 90
        flight(70, 60);
        // at 90 the second flight lands at 150, and the third one (planned at 140) is short
        flight(140, 60);

        final RosterForecast.Choices choices = RosterForecast.rescheduleChoices(world, forecast(), 1).orElseThrow();

        final int minute = Time.ONE_MINUTE;
        assertFalse(choices.atEarliest());
        assertEquals(new RosterForecast.Option(base + 90 * minute, base + 180 * minute, 40), choices.options().get(0));
        assertEquals(new RosterForecast.Option(base + 105 * minute, base + 195 * minute, 55), choices.options().get(1));
        // two hours after the earliest time
        assertEquals(base + (90 + 120) * minute, choices.options().get(choices.options().size() - 1).departure());
    }

    @Test
    void aFlightWithoutTheOneAfterItHasOptionsWithoutConflicts() {
        final FlightMissions.Mission only = flight(0, 60);

        final RosterForecast.Choices choices = RosterForecast.rescheduleChoices(world, forecast(), 0).orElseThrow();

        assertNull(choices.after());
        assertNull(choices.before());
        assertFalse(choices.options().isEmpty());
        assertTrue(choices.options().stream().allMatch(o -> o.nextDeparture() == 0 && o.shortfallMinutes() == 0));
        assertTrue(choices.options().stream().noneMatch(o -> o.departure() == only.getPlannedDepartureWorldTime()));
    }

    @Test
    void aFlightWhichHasNoTimeOfTheStepBetweenItsNeighboursHasAnEmptyWindow() {
        assertEquals(0, base % STEP);
        flight(0, 65);
        // the flight is planned at 100: not earlier than 95 (the first flight lands at 65, the gap), not later than 100 (the third one is at 190), no time of the step is there
        flight(100, 60);
        flight(190, 60);

        assertTrue(window(1).isEmpty());
    }

    @Test
    void aFlightWhichHasLeftIsBeforeAFlightMovedToItsPlannedDepartureWhateverTheOrderOfTheIds() {
        // the second flight of the roster is moved to the planned departure of the first one, which has departed earlier than planned: the same plan, the lower id
        final FlightMissions.Mission moved = flight(0, 60);
        final FlightMissions.Mission left = flight(0, 60);
        world.flightMissionControl().start(left);
        world.flightMissionControl().setTrackingMode(left, FlightMissions.TrackingMode.Manual);
        world.flightMissionControl().blocksOff(left);

        final List<FlightMissions.Mission> order = world.flightMissions().allByUserId(USER_ID)
                .sorted(FlightMissions.sortByDepartureTimeFromPastToFuture).toList();

        assertEquals(List.of(left.getId(), moved.getId()), order.stream().map(FlightMissions.Mission::getId).toList());
    }

    @Test
    void aFlightWhichHasLeftCannotBeMoved() {
        final FlightMissions.Mission first = flight(0, 60);
        world.flightMissionControl().start(first);
        world.flightMissionControl().setTrackingMode(first, FlightMissions.TrackingMode.Manual);
        world.flightMissionControl().blocksOff(first);

        assertTrue(RosterForecast.rescheduleWindow(world, forecast(), 0).isEmpty());
    }

    @Test
    void theFlightsWhichAreOverAndTheFlightsOfOthersAreNotInTheRoster() {
        flight(0, 60);
        final FlightMissions.Mission finished = flight(105, 60);
        finished.setStatus(FlightMissions.Status.Finished);
        final FlightMissions.Mission cancelled = flight(120, 60);
        cancelled.setStatus(FlightMissions.Status.Cancelled);
        final FlightMissions.Mission other = flight(110, 60);
        other.setUserId(USER_ID + 1);

        assertEquals(1, forecast().size());
        assertEquals(1, RosterForecast.of(world, USER_ID + 1).size());
        assertTrue(RosterForecast.of(world, 0).isEmpty());
    }
}
