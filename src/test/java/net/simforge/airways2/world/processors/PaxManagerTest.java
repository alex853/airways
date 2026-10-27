package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaxManagerTest {
    private static final int RATE_PAX_PER_MINUTE = 15; // Boarding.ratePaxPerMinute
    private static final int RESERVE = 3 * Time.ONE_MINUTE; // Boarding.boardingTimeReserve

    private World world;
    private Cities.City cityA;
    private Cities.City cityB;
    private TransportFlights.Flight transportFlight;
    private PaxManager paxManager;
    private TransportFlightControl tfControl;
    private int startTime;

    @BeforeEach
    public void beforeEach() {
        startTime = Time.START_TIME_EPOCH_SECONDS;
        world = World.create(new InMemoryStorageStrategy(), startTime);

        final Countries.Country country = world.countries().create("TT", "Testland");
        cityA = world.cities().create(country.getId(), "Alpha City", 0, 0, 1_000_000);
        cityB = world.cities().create(country.getId(), "Bravo City", 0, 10, 1_000_000);

        final Airports.Airport airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        final Airports.Airport airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");

        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().create("A320", "320");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-BCDE", airportA);

        final int departureTime = startTime + 2 * Time.ONE_HOUR;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(
                aircraft, airportA, airportB, departureTime, departureTime + 2 * Time.ONE_HOUR);

        tfControl = world.transportFlightControl();
        paxManager = world.paxManager();

        transportFlight = tfControl.createTransportFlight(mission);
        tfControl.startCheckIn(transportFlight);
    }

    // a journey which is booked and checked-in to the transport flight, the same way ShadowJetLogic does it
    private Journeys.Journey checkedInJourney(final int groupSize) {
        final Journeys.Journey journey = world.journeys().create(
                Journeys.Status.LookingForTickets, cityA.getId(), cityB.getId(), groupSize, CabinLayout.Service.Y);
        journey.setLocationCityId(cityA.getId());
        world.journeyControl().bookDirectFlightJourneyNoChecks(journey, transportFlight);
        world.journeyControl().waitForCheckin(journey);
        world.journeyControl().checkin(journey);
        return journey;
    }

    private List<Journeys.Journey> checkedInJourneys(final int... groupSizes) {
        final List<Journeys.Journey> journeys = new ArrayList<>();
        for (final int groupSize : groupSizes) {
            journeys.add(checkedInJourney(groupSize));
        }
        return journeys;
    }

    private void runBoardingFor(final int seconds) {
        final int finishTime = world.getWorldTime() + seconds;
        while (world.getWorldTime() < finishTime) {
            world.setWorldTime(world.getWorldTime() + 10);
            paxManager.boarding(transportFlight).proceed();
        }
    }

    private int totalOnBoard(final List<Journeys.Journey> journeys) {
        return journeys.stream()
                .filter(j -> j.getStatus() == Journeys.Status.OnBoard)
                .mapToInt(Journeys.Journey::getGroupSize)
                .sum();
    }

    @Test
    public void test__estimated_finish_time_is_remaining_pax_by_rate_plus_reserve() {
        checkedInJourneys(40, 40, 40); // 120 PAX, 120 / 15 = 8 minutes

        tfControl.startBoarding(transportFlight);

        assertEquals(startTime + 8 * Time.ONE_MINUTE + RESERVE, paxManager.boarding(transportFlight).getEstimatedFinishTime());
    }

    @Test
    public void test__estimated_finish_time_rounds_seconds_up() {
        checkedInJourneys(16); // 16 / 15 * 60 = 64 seconds

        tfControl.startBoarding(transportFlight);

        assertEquals(startTime + 64 + RESERVE, paxManager.boarding(transportFlight).getEstimatedFinishTime());
    }

    @Test
    public void test__boarding_finish_time_is_not_passed_at_start_and_is_passed_at_estimated_time() {
        checkedInJourneys(30, 30);

        tfControl.startBoarding(transportFlight);
        assertFalse(paxManager.boarding(transportFlight).wasFinishTimePassed());

        world.setWorldTime(paxManager.boarding(transportFlight).getEstimatedFinishTime());
        assertTrue(paxManager.boarding(transportFlight).wasFinishTimePassed());
    }

    @Test
    public void test__nobody_boards_at_the_very_start() {
        final List<Journeys.Journey> journeys = checkedInJourneys(10, 10);

        tfControl.startBoarding(transportFlight);

        assertEquals(0, totalOnBoard(journeys));
        assertEquals(0, transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__journeys_board_one_by_one_and_all_are_on_board_eventually() {
        final List<Journeys.Journey> journeys = checkedInJourneys(8, 5, 12, 3);
        final int total = 8 + 5 + 12 + 3;

        tfControl.startBoarding(transportFlight);
        runBoardingFor(10 * Time.ONE_MINUTE);

        journeys.forEach(j -> assertEquals(Journeys.Status.OnBoard, j.getStatus()));
        assertEquals(total, totalOnBoard(journeys));
        assertEquals(total, transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__journeys_board_in_order_of_the_queue() {
        final List<Journeys.Journey> journeys = checkedInJourneys(20, 20, 20);

        tfControl.startBoarding(transportFlight);
        runBoardingFor(2 * Time.ONE_MINUTE); // about 25-37 PAX are ready, the first group is on board, the third cannot be yet

        assertEquals(Journeys.Status.OnBoard, journeys.get(0).getStatus());
        assertEquals(Journeys.Status.WaitingForBoarding, journeys.get(2).getStatus());
    }

    @Test
    public void test__restored_boarding_counts_journeys_already_on_board() {
        final List<Journeys.Journey> journeys = checkedInJourneys(6, 10, 10);
        world.journeyControl().board(journeys.get(0)); // boarded before the boarding state is created, as after a restart

        tfControl.startBoarding(transportFlight);

        assertEquals(6, transportFlight.getPaxOnBoard());
        // remaining to board is 20, so the estimate is 20 / 15 * 60 = 80 seconds + reserve
        assertEquals(startTime + 80 + RESERVE, paxManager.boarding(transportFlight).getEstimatedFinishTime());
    }

    @Test
    public void test__pax_on_board_includes_pax_who_are_still_boarding_counter() {
        // documents the current behaviour: while a group has not boarded yet, PAX on board already grows by the counter
        final List<Journeys.Journey> journeys = checkedInJourneys(60, 60);

        tfControl.startBoarding(transportFlight);
        runBoardingFor(Time.ONE_MINUTE); // the counter is about 15 PAX, the first group of 60 cannot board yet

        assertEquals(0, totalOnBoard(journeys));
        assertTrue(transportFlight.getPaxOnBoard() > 0, "PAX on board should grow with the boarding counter");
        assertTrue(transportFlight.getPaxOnBoard() < 60);
    }

    @Test
    public void test__finish_boarding_marks_not_boarded_journeys_as_too_late_and_keeps_boarded() {
        final List<Journeys.Journey> journeys = checkedInJourneys(10, 60, 60);

        tfControl.startBoarding(transportFlight);
        runBoardingFor(Time.ONE_MINUTE); // only the first group (10) is on board
        assertEquals(Journeys.Status.OnBoard, journeys.get(0).getStatus());
        assertEquals(Journeys.Status.WaitingForBoarding, journeys.get(1).getStatus());

        tfControl.waitForDeparture(transportFlight); // finishBoarding is inside

        assertEquals(TransportFlights.Status.WaitingForDeparture, transportFlight.getStatus());
        assertEquals(Journeys.Status.OnBoard, journeys.get(0).getStatus());
        assertEquals(Journeys.Status.OnBoard, journeys.get(1).getStatus()); // was being boarded, see the next test
        assertEquals(Journeys.Status.TooLateToBoard, journeys.get(2).getStatus());
    }

    @Test
    public void test__finish_boarding_boards_the_journey_which_is_being_boarded__pax_on_board_does_not_jump_down() {
        final List<Journeys.Journey> journeys = checkedInJourneys(10, 60, 60);

        tfControl.startBoarding(transportFlight);
        runBoardingFor(Time.ONE_MINUTE); // the first group is on board, the counter holds some PAX of the second one
        final int paxOnBoardBeforeFinish = transportFlight.getPaxOnBoard();
        assertTrue(paxOnBoardBeforeFinish > 10, "the counter should hold PAX of the second group");

        tfControl.waitForDeparture(transportFlight);

        assertEquals(Journeys.Status.OnBoard, journeys.get(1).getStatus());
        assertEquals(10 + 60, transportFlight.getPaxOnBoard());
        assertTrue(transportFlight.getPaxOnBoard() >= paxOnBoardBeforeFinish);
    }

    @Test
    public void test__finish_boarding_without_a_started_journey_boards_nobody_new() {
        final List<Journeys.Journey> journeys = checkedInJourneys(60, 60);

        tfControl.startBoarding(transportFlight); // the counter is zero, nobody is being boarded
        tfControl.waitForDeparture(transportFlight);

        journeys.forEach(j -> assertEquals(Journeys.Status.TooLateToBoard, j.getStatus()));
        assertEquals(0, transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__too_late_journeys_are_returned_to_the_city_before_the_flight() {
        final List<Journeys.Journey> journeys = checkedInJourneys(60);

        tfControl.startBoarding(transportFlight);
        assertEquals(0, journeys.get(0).getLocationCityId()); // in the flight, the city is zeroed
        assertEquals(cityA.getId(), journeys.get(0).getLastKnownLocationCityId());

        tfControl.waitForDeparture(transportFlight);

        assertEquals(cityA.getId(), journeys.get(0).getLocationCityId());
    }

    // Known issue: when the flight departs while it is still boarding, finishBoarding is not called,
    // PAX on board keeps the boarding counter and the flight can never be deboarded to zero
    @Test
    public void test__departure_during_boarding_leaves_exact_pax_on_board() {
        final List<Journeys.Journey> journeys = checkedInJourneys(8, 60, 60);

        tfControl.startBoarding(transportFlight);
        runBoardingFor(Time.ONE_MINUTE); // the first group is on board, the counter holds some PAX of the next one

        tfControl.whenFlightDepartsFromGate(transportFlight);

        assertEquals(totalOnBoard(journeys), transportFlight.getPaxOnBoard());
    }

    // ---- the interrupted boarding: the pilot departs while the flight is still boarding ----

    @Test
    public void test__finish_boarding_sets_exact_pax_on_board() {
        final List<Journeys.Journey> journeys = checkedInJourneys(8, 60, 60);

        tfControl.startBoarding(transportFlight);
        runBoardingFor(Time.ONE_MINUTE); // the first group is on board, the counter holds some PAX of the next one

        tfControl.waitForDeparture(transportFlight);

        assertEquals(totalOnBoard(journeys), transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__departure_during_boarding_marks_not_boarded_journeys_too_late() {
        final List<Journeys.Journey> journeys = checkedInJourneys(8, 60, 60);

        tfControl.startBoarding(transportFlight);
        runBoardingFor(Time.ONE_MINUTE);
        assertEquals(Journeys.Status.OnBoard, journeys.get(0).getStatus());

        tfControl.whenFlightDepartsFromGate(transportFlight);

        assertEquals(TransportFlights.Status.Departure, transportFlight.getStatus());
        assertEquals(Journeys.Status.OnBoard, journeys.get(0).getStatus());
        assertEquals(Journeys.Status.OnBoard, journeys.get(1).getStatus()); // was being boarded
        assertEquals(Journeys.Status.TooLateToBoard, journeys.get(2).getStatus());
    }

    @Test
    public void test__start_deboarding_sets_exact_pax_on_board() {
        final List<Journeys.Journey> journeys = checkedInJourneys(8, 12);

        tfControl.startBoarding(transportFlight);
        runBoardingFor(10 * Time.ONE_MINUTE);
        tfControl.waitForDeparture(transportFlight);
        flyToTheGate();
        transportFlight.setPaxOnBoard(99); // a corrupted value

        tfControl.startDeboarding(transportFlight);

        // the journeys wait for deboarding now, so the exact value is the sum of their group sizes
        assertEquals(8 + 12, journeys.stream().mapToInt(Journeys.Journey::getGroupSize).sum());
        assertEquals(8 + 12, transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__all_pax_are_deboarded_after_the_boarding_is_interrupted_by_departure() {
        final List<Journeys.Journey> journeys = checkedInJourneys(8, 60, 60);

        tfControl.startBoarding(transportFlight);
        runBoardingFor(Time.ONE_MINUTE);
        tfControl.whenFlightDepartsFromGate(transportFlight);
        flyToTheGate(false);

        tfControl.startDeboarding(transportFlight);
        journeys.stream()
                .filter(j -> j.getStatus() == Journeys.Status.WaitingForDeboarding)
                .forEach(j -> world.journeyControl().deboard(j));

        assertTrue(tfControl.areAllPaxDeboarded(transportFlight),
                "PAX on board after deboarding is " + transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__pax_carried_is_set_at_the_takeoff_and_stays_after_the_deboarding() {
        final List<Journeys.Journey> journeys = checkedInJourneys(8, 12);

        tfControl.startBoarding(transportFlight);
        runBoardingFor(10 * Time.ONE_MINUTE);
        tfControl.waitForDeparture(transportFlight);
        tfControl.whenFlightDepartsFromGate(transportFlight);
        assertEquals(0, transportFlight.getPaxCarried());

        tfControl.whenFlightTakeoffs(transportFlight);
        assertEquals(8 + 12, transportFlight.getPaxCarried());

        tfControl.whenFlightLands(transportFlight);
        tfControl.whenFlightArrivesToGate(transportFlight);
        tfControl.startDeboarding(transportFlight);
        journeys.forEach(j -> world.journeyControl().deboard(j));

        assertTrue(tfControl.areAllPaxDeboarded(transportFlight));
        assertEquals(8 + 12, transportFlight.getPaxCarried());
    }

    // ---- the deboarding: the same fractional counter as the boarding, in the opposite direction and a bit faster ----

    private static final int DEBOARDING_RATE = PaxManager.Deboarding.RATE_PAX_PER_MINUTE;
    // Tools.random(85, 125) / 100 is the random factor of the counter
    private static final double MIN_FACTOR = 0.85;
    private static final double MAX_FACTOR = 1.25;

    /** Boards everybody, flies the flight to the gate and starts the deboarding. The journeys wait for deboarding when it returns. */
    private List<Journeys.Journey> startDeboardingOf(final int... groupSizes) {
        final List<Journeys.Journey> journeys = checkedInJourneys(groupSizes);

        tfControl.startBoarding(transportFlight);
        for (int i = 0; i < 1000 && !paxManager.areAllBoarded(transportFlight); i++) {
            world.setWorldTime(world.getWorldTime() + 10);
            paxManager.boarding(transportFlight).proceed();
        }
        assertTrue(paxManager.areAllBoarded(transportFlight));
        tfControl.waitForDeparture(transportFlight);
        flyToTheGate();

        tfControl.startDeboarding(transportFlight);
        assertEquals(TransportFlights.Status.Deboarding, transportFlight.getStatus());
        return journeys;
    }

    private void deboardingTick() {
        world.setWorldTime(world.getWorldTime() + 10);
        tfControl.continueDeboarding(transportFlight);
    }

    private static int waitingGroups(final List<Journeys.Journey> journeys) {
        return (int) journeys.stream().filter(j -> j.getStatus() == Journeys.Status.WaitingForDeboarding).count();
    }

    private static int waitingPax(final List<Journeys.Journey> journeys) {
        return journeys.stream()
                .filter(j -> j.getStatus() == Journeys.Status.WaitingForDeboarding)
                .mapToInt(Journeys.Journey::getGroupSize)
                .sum();
    }

    /** Ticks the deboarding until nobody waits (at most maxTicks), returns the PAX on board after every tick. */
    private List<Integer> deboardEverybody(final List<Journeys.Journey> journeys, final int maxTicks) {
        final List<Integer> paxAfterTick = new ArrayList<>();
        for (int i = 0; i < maxTicks && waitingGroups(journeys) > 0; i++) {
            deboardingTick();
            paxAfterTick.add(transportFlight.getPaxOnBoard());
        }
        return paxAfterTick;
    }

    private static int maxPaxPerTick() {
        return (int) Math.ceil(DEBOARDING_RATE * MAX_FACTOR * 10 / 60.0);
    }

    @Test
    public void test__deboarding_is_faster_than_the_boarding_and_the_journeys_check_their_flight_every_5_minutes() {
        assertEquals(25, DEBOARDING_RATE);
        assertTrue(DEBOARDING_RATE > RATE_PAX_PER_MINUTE);
        assertEquals(5 * Time.ONE_MINUTE, PaxManager.Deboarding.JOURNEY_WATCH_INTERVAL);
    }

    @Test
    public void test__start_deboarding_makes_all_the_journeys_wait_and_nobody_leaves_at_once() {
        final List<Journeys.Journey> journeys = startDeboardingOf(3, 4, 5, 2, 6, 4);

        journeys.forEach(j -> {
            assertEquals(Journeys.Status.WaitingForDeboarding, j.getStatus());
            assertEquals(world.getWorldTime() + PaxManager.Deboarding.JOURNEY_WATCH_INTERVAL, j.getHeartbeatTime());
        });
        assertEquals(24, transportFlight.getPaxOnBoard());
        assertEquals(world.getWorldTime() + 10, transportFlight.getHeartbeatTime());
    }

    @Test
    public void test__tick_without_elapsed_time_changes_nothing() {
        final List<Journeys.Journey> journeys = startDeboardingOf(10, 10);

        tfControl.continueDeboarding(transportFlight); // the world time is the same

        assertEquals(20, transportFlight.getPaxOnBoard());
        assertEquals(2, waitingGroups(journeys));
    }

    @Test
    public void test__the_first_tick_lets_a_few_pax_out_but_not_the_whole_group() {
        final List<Journeys.Journey> journeys = startDeboardingOf(10);

        deboardingTick();

        assertEquals(1, waitingGroups(journeys));
        assertTrue(transportFlight.getPaxOnBoard() < 10, "PAX on board " + transportFlight.getPaxOnBoard());
        assertTrue(transportFlight.getPaxOnBoard() >= 10 - maxPaxPerTick(), "PAX on board " + transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__one_group_pax_on_board_goes_down_gradually_and_never_jumps() {
        final List<Journeys.Journey> journeys = startDeboardingOf(10);

        final List<Integer> pax = deboardEverybody(journeys, 100);

        assertEquals(0, waitingGroups(journeys));
        assertEquals(Journeys.Status.JustArrived, journeys.get(0).getStatus());
        assertTrue(pax.size() >= 2 && pax.size() <= 3, "ticks " + pax.size()); // 10 PAX need from 2 to 3 ticks
        int previous = 10;
        for (final int value : pax) {
            assertTrue(value <= previous, "PAX on board goes up: " + pax);
            assertTrue(previous - value <= maxPaxPerTick(), "PAX on board jumps: " + pax);
            previous = value;
        }
        assertEquals(0, pax.get(pax.size() - 1));
        // at least one value between the start and the end: PAX on board does not jump from 10 to 0
        assertTrue(pax.get(0) > 0 && pax.get(0) < 10, "PAX on board values: " + pax);
    }

    @Test
    public void test__big_group_pax_on_board_goes_down_all_the_way_while_the_group_is_waiting() {
        final List<Journeys.Journey> journeys = startDeboardingOf(100);

        final List<Integer> pax = deboardEverybody(journeys, 200);

        // 100 PAX at 25 PAX per minute with the factor 0.85 .. 1.25 take 3.2 .. 4.7 minutes
        final double minTicks = 100 / (DEBOARDING_RATE * MAX_FACTOR) * 6;
        final double maxTicks = 100 / (DEBOARDING_RATE * MIN_FACTOR) * 6;
        assertTrue(pax.size() >= Math.floor(minTicks) && pax.size() <= Math.ceil(maxTicks) + 1, "ticks " + pax.size());
        assertEquals(0, pax.get(pax.size() - 1));
        assertTrue(pax.stream().distinct().count() >= 15, "PAX on board values: " + pax);
        for (int i = 1; i < pax.size(); i++) {
            assertTrue(pax.get(i) <= pax.get(i - 1), "PAX on board goes up: " + pax);
        }
        // the group is still waiting while PAX on board is going down, it leaves when the counter covers the whole group
        assertEquals(Journeys.Status.JustArrived, journeys.get(0).getStatus());
    }

    @Test
    public void test__many_groups_are_deboarded_in_about_pax_by_rate_minutes() {
        final int[] groups = new int[36];
        java.util.Arrays.fill(groups, 4);
        final List<Journeys.Journey> journeys = startDeboardingOf(groups); // 144 PAX, all the economy seats of the A320

        final List<Integer> pax = deboardEverybody(journeys, 500);

        assertEquals(0, waitingGroups(journeys));
        final double minTicks = 144 / (DEBOARDING_RATE * MAX_FACTOR) * 6;
        final double maxTicks = 144 / (DEBOARDING_RATE * MIN_FACTOR) * 6;
        assertTrue(pax.size() >= Math.floor(minTicks) - 1 && pax.size() <= Math.ceil(maxTicks) + 1,
                "ticks " + pax.size() + ", expected " + minTicks + " .. " + maxTicks);
        assertEquals(0, transportFlight.getPaxOnBoard());
        journeys.forEach(j -> assertEquals(Journeys.Status.JustArrived, j.getStatus()));
    }

    @Test
    public void test__journeys_leave_one_after_another_in_the_order_of_their_ids() {
        final int[] groups = new int[30];
        java.util.Arrays.fill(groups, 3);
        final List<Journeys.Journey> journeys = startDeboardingOf(groups);
        final int[] leftAtTick = new int[journeys.size()];
        java.util.Arrays.fill(leftAtTick, -1);

        for (int tick = 1; tick <= 500 && waitingGroups(journeys) > 0; tick++) {
            deboardingTick();
            for (int i = 0; i < journeys.size(); i++) {
                if (leftAtTick[i] == -1 && journeys.get(i).getStatus() != Journeys.Status.WaitingForDeboarding) {
                    leftAtTick[i] = tick;
                }
            }
        }

        for (int i = 0; i < journeys.size(); i++) {
            assertTrue(leftAtTick[i] > 0, "journey " + i + " has not left");
            if (i > 0) {
                assertTrue(leftAtTick[i] >= leftAtTick[i - 1], "journey " + i + " left before journey " + (i - 1));
            }
        }
        assertTrue(leftAtTick[journeys.size() - 1] > leftAtTick[0], "everybody left at the same tick");
    }

    @Test
    public void test__pax_on_board_always_matches_the_journeys_which_are_still_waiting() {
        final List<Journeys.Journey> journeys = startDeboardingOf(3, 4, 5, 2, 6, 4, 8, 1, 7);

        for (int tick = 0; tick < 500 && waitingGroups(journeys) > 0; tick++) {
            deboardingTick();
            final int waiting = waitingPax(journeys);
            final int pax = transportFlight.getPaxOnBoard();
            assertTrue(pax <= waiting, "PAX on board " + pax + " is more than waiting " + waiting);
            assertEquals(waiting == 0, pax == 0, "PAX on board " + pax + ", waiting " + waiting);
            if (waiting > 0) {
                final int nextGroup = journeys.stream()
                        .filter(j -> j.getStatus() == Journeys.Status.WaitingForDeboarding)
                        .findFirst().orElseThrow().getGroupSize();
                assertTrue(pax > waiting - nextGroup, "PAX on board " + pax + ", waiting " + waiting + ", next group " + nextGroup);
            }
        }
        assertEquals(0, transportFlight.getPaxOnBoard());
        assertTrue(tfControl.areAllPaxDeboarded(transportFlight));
    }

    @Test
    public void test__all_pax_are_not_deboarded_while_somebody_waits() {
        final List<Journeys.Journey> journeys = startDeboardingOf(10, 10, 10);

        while (waitingGroups(journeys) > 1) {
            deboardingTick();
            assertFalse(tfControl.areAllPaxDeboarded(transportFlight));
        }
        assertFalse(tfControl.areAllPaxDeboarded(transportFlight));
        deboardEverybody(journeys, 100);
        assertTrue(tfControl.areAllPaxDeboarded(transportFlight));
    }

    @Test
    public void test__deboarded_journeys_just_arrived_with_a_heartbeat_within_the_hour() {
        final List<Journeys.Journey> journeys = startDeboardingOf(5, 5, 5);

        deboardEverybody(journeys, 200);

        journeys.forEach(j -> {
            assertEquals(Journeys.Status.JustArrived, j.getStatus());
            assertTrue(j.getHeartbeatTime() >= world.getWorldTime() - 10, "heartbeat is in the past");
            assertTrue(j.getHeartbeatTime() <= world.getWorldTime() + Time.ONE_HOUR, "heartbeat is later than an hour");
        });
    }

    @Test
    public void test__the_state_of_the_deboarding_is_restored_when_the_memory_is_lost() {
        final List<Journeys.Journey> journeys = startDeboardingOf(6, 6, 6, 6, 6);
        for (int i = 0; i < 5; i++) {
            deboardingTick();
        }
        final int waitingBefore = waitingGroups(journeys);
        assertTrue(waitingBefore > 0 && waitingBefore < 5, "waiting " + waitingBefore);

        paxManager.dropState(transportFlight); // the same as a restart of the application

        final List<Integer> pax = deboardEverybody(journeys, 500);

        assertEquals(0, waitingGroups(journeys));
        assertEquals(0, transportFlight.getPaxOnBoard());
        assertTrue(pax.size() > 0);
        journeys.forEach(j -> assertEquals(Journeys.Status.JustArrived, j.getStatus()));
    }

    @Test
    public void test__journeys_of_another_flight_are_not_touched() {
        final List<Journeys.Journey> journeys = startDeboardingOf(10, 10);
        final FlightMissions.Mission anotherMission = world.flightMissions().createDispatchedMission(
                world.aircrafts().byId(world.flightMissions().byId(transportFlight.getFlightMissionId()).orElseThrow().getAircraftId()).orElseThrow(),
                world.airports().byIcao("AAAA").orElseThrow(), world.airports().byIcao("BBBB").orElseThrow(),
                startTime + 5 * Time.ONE_HOUR, startTime + 7 * Time.ONE_HOUR);
        final TransportFlights.Flight anotherFlight = tfControl.createTransportFlight(anotherMission);
        final Journeys.Journey stranger = world.journeys().create(
                Journeys.Status.WaitingForDeboarding, cityA.getId(), cityB.getId(), 7, CabinLayout.Service.Y);
        stranger.setTransportFlight1Id(anotherFlight.getId());

        deboardEverybody(journeys, 200);

        assertEquals(Journeys.Status.WaitingForDeboarding, stranger.getStatus());
        assertEquals(anotherFlight.getId(), stranger.getTransportFlight1Id());
        assertEquals(0, transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__finished_flight_wakes_up_the_journeys_which_are_still_waiting() {
        final List<Journeys.Journey> journeys = startDeboardingOf(10, 10);
        deboardingTick();
        assertEquals(2, waitingGroups(journeys));

        tfControl.finish(transportFlight); // for example the deboarding window is over

        assertEquals(TransportFlights.Status.Finished, transportFlight.getStatus());
        journeys.forEach(j -> assertEquals(world.getWorldTime(), j.getHeartbeatTime()));
    }

    @Test
    public void test__cancelled_flight_drops_the_state_and_a_tick_without_journeys_is_harmless() {
        final List<Journeys.Journey> journeys = startDeboardingOf(10, 10);
        deboardingTick();

        tfControl.unloadJourneysForcefullyFromActiveFlight(transportFlight);
        deboardingTick();

        journeys.forEach(j -> assertEquals(Journeys.Status.LookingForTickets, j.getStatus()));
        assertEquals(0, transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__tick_after_everybody_has_left_keeps_zero() {
        final List<Journeys.Journey> journeys = startDeboardingOf(5);
        deboardEverybody(journeys, 100);

        for (int i = 0; i < 5; i++) {
            deboardingTick();
            assertEquals(0, transportFlight.getPaxOnBoard());
        }
    }

    @Test
    public void test__continue_deboarding_sets_the_next_tick_of_the_flight_to_10_seconds() {
        startDeboardingOf(5, 5);

        deboardingTick();

        assertEquals(world.getWorldTime() + 10, transportFlight.getHeartbeatTime());
    }

    @Test
    public void test__deboarding_tick_is_refused_for_a_flight_which_is_not_deboarding() {
        checkedInJourneys(5);
        tfControl.startBoarding(transportFlight);

        assertThrows(IllegalArgumentException.class, () -> paxManager.deboarding(transportFlight).proceed());
        assertThrows(IllegalArgumentException.class, () -> paxManager.deboarding(transportFlight).start());
    }

    @Test
    public void test__the_world_deboards_a_flight_without_any_help_of_the_test() {
        final List<Journeys.Journey> journeys = startDeboardingOf(4, 4, 4, 4, 4, 4);

        // the transport flight processor ticks the flight by its heartbeat, as the world does
        final int deadline = world.getWorldTime() + 10 * Time.ONE_MINUTE;
        while (world.getWorldTime() < deadline && transportFlight.getStatus() == TransportFlights.Status.Deboarding) {
            world.process(world.getWorldTime() + 10);
        }

        assertEquals(TransportFlights.Status.Finished, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());
        assertEquals(0, waitingGroups(journeys));
    }

    @Test
    public void test__random_flights_keep_all_the_invariants_of_the_deboarding() {
        final java.util.Random random = new java.util.Random(20261018);
        for (int flight = 0; flight < 25; flight++) {
            beforeEach(); // a clean world for every random flight

            final int groupCount = 1 + random.nextInt(30);
            final int[] groups = new int[groupCount];
            int total = 0;
            for (int i = 0; i < groupCount; i++) {
                groups[i] = 1 + random.nextInt(Math.max(1, Math.min(10, (144 - total) / (groupCount - i))));
                total += groups[i];
            }
            final List<Journeys.Journey> journeys = startDeboardingOf(groups);
            final int maxGroup = java.util.Arrays.stream(groups).max().orElse(1);

            int previousPax = total;
            int ticks = 0;
            while (waitingGroups(journeys) > 0 && ticks < 2000) {
                deboardingTick();
                ticks++;
                final int pax = transportFlight.getPaxOnBoard();
                final int waiting = waitingPax(journeys);
                final String where = "flight " + flight + " groups " + java.util.Arrays.toString(groups) + " tick " + ticks;
                assertTrue(pax <= previousPax, "PAX on board goes up, " + where);
                assertTrue(previousPax - pax <= maxPaxPerTick() + maxGroup, "PAX on board jumps, " + where);
                assertTrue(pax <= waiting, "PAX on board is more than waiting, " + where);
                assertEquals(waiting == 0, pax == 0, "PAX on board and waiting disagree, " + where);
                previousPax = pax;
            }

            assertEquals(0, waitingGroups(journeys), "somebody is still waiting after " + ticks + " ticks, groups " + java.util.Arrays.toString(groups));
            final double minTicks = total / (DEBOARDING_RATE * MAX_FACTOR) * 6;
            final double maxTicks = total / (DEBOARDING_RATE * MIN_FACTOR) * 6;
            assertTrue(ticks >= Math.floor(minTicks) - 1 && ticks <= Math.ceil(maxTicks) + 1,
                    "ticks " + ticks + ", expected " + minTicks + " .. " + maxTicks + ", groups " + java.util.Arrays.toString(groups));
            journeys.forEach(j -> assertEquals(Journeys.Status.JustArrived, j.getStatus()));
        }
    }

    @Test
    public void test__journey_which_watches_its_flight_does_not_deboard_by_itself_and_looks_again_in_5_minutes() {
        final List<Journeys.Journey> journeys = startDeboardingOf(100); // the group needs about 4 minutes
        final Journeys.Journey journey = journeys.get(0);
        final int watchTime = journey.getHeartbeatTime();
        assertEquals(world.getWorldTime() + PaxManager.Deboarding.JOURNEY_WATCH_INTERVAL, watchTime);

        world.setWorldTime(watchTime);
        JourneyProcessor.process(world); // the flight did not let the journey out (its tick has not been done), the journey only watches

        assertEquals(Journeys.Status.WaitingForDeboarding, journey.getStatus());
        assertEquals(watchTime + PaxManager.Deboarding.JOURNEY_WATCH_INTERVAL, journey.getHeartbeatTime());
        assertEquals(100, transportFlight.getPaxOnBoard()); // the journey did not touch PAX on board either
    }

    @Test
    public void test__two_flights_deboard_independently_with_their_own_counters() {
        final List<Journeys.Journey> firstJourneys = startDeboardingOf(10, 10, 10);
        final FlightMissions.Mission anotherMission = world.flightMissions().createDispatchedMission(
                world.aircrafts().byId(world.flightMissions().byId(transportFlight.getFlightMissionId()).orElseThrow().getAircraftId()).orElseThrow(),
                world.airports().byIcao("AAAA").orElseThrow(), world.airports().byIcao("BBBB").orElseThrow(),
                startTime + 5 * Time.ONE_HOUR, startTime + 7 * Time.ONE_HOUR);
        final TransportFlights.Flight second = tfControl.createTransportFlight(anotherMission);
        final List<Journeys.Journey> secondJourneys = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final Journeys.Journey journey = world.journeys().create(
                    Journeys.Status.WaitingForDeboarding, cityA.getId(), cityB.getId(), 6, CabinLayout.Service.Y);
            journey.setTransportFlight1Id(second.getId());
            secondJourneys.add(journey);
        }
        second.setStatus(TransportFlights.Status.Deboarding);
        second.setPaxOnBoard(24);
        paxManager.deboarding(second).start();

        for (int tick = 0; tick < 500 && (waitingGroups(firstJourneys) > 0 || waitingGroups(secondJourneys) > 0); tick++) {
            world.setWorldTime(world.getWorldTime() + 10);
            tfControl.continueDeboarding(transportFlight);
            tfControl.continueDeboarding(second);
            assertEquals(waitingGroups(firstJourneys) == 0, transportFlight.getPaxOnBoard() == 0);
            assertEquals(waitingGroups(secondJourneys) == 0, second.getPaxOnBoard() == 0);
            assertTrue(transportFlight.getPaxOnBoard() <= waitingPax(firstJourneys));
            assertTrue(second.getPaxOnBoard() <= waitingPax(secondJourneys));
        }

        firstJourneys.forEach(j -> assertEquals(Journeys.Status.JustArrived, j.getStatus()));
        secondJourneys.forEach(j -> assertEquals(Journeys.Status.JustArrived, j.getStatus()));
        assertEquals(0, transportFlight.getPaxOnBoard());
        assertEquals(0, second.getPaxOnBoard());
    }

    private void flyToTheGate() {
        flyToTheGate(true);
    }

    private void flyToTheGate(final boolean departFromTheGate) {
        if (departFromTheGate) {
            tfControl.whenFlightDepartsFromGate(transportFlight);
        }
        tfControl.whenFlightTakeoffs(transportFlight);
        tfControl.whenFlightLands(transportFlight);
        tfControl.whenFlightArrivesToGate(transportFlight);
    }

    // the lines of the log of the PaxManager while the code runs
    private List<String> logOf(final Runnable code) {
        return logOf(Level.INFO, code);
    }

    private List<String> logOf(final Level level, final Runnable code) {
        final Logger logger = (Logger) LoggerFactory.getLogger(PaxManager.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        final Level oldLevel = logger.getLevel();
        logger.setLevel(level);
        try {
            code.run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(oldLevel);
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    public void test__the_progress_line_of_the_boarding_is_short_and_keeps_the_id_with_the_hash() {
        checkedInJourneys(40, 40);
        tfControl.startBoarding(transportFlight);

        // the progress line is at the debug level: nothing of it at the info level
        assertTrue(logOf(() -> runBoardingFor(5)).stream().noneMatch(l -> l.contains(" PAX ")));

        final List<String> lines = logOf(Level.DEBUG, () -> runBoardingFor(30));

        final List<String> progress = lines.stream().filter(l -> l.contains(" PAX ")).toList();
        assertFalse(progress.isEmpty());
        // t/f #5037 - board PAX 16 [c16,r115,y25,f17:45,k0.65]
        final String pattern = "t/f #" + transportFlight.getId() + " - board PAX \\d+ \\[c\\d+,r\\d+,y\\d+,f[0-9:]+,k\\d+(\\.\\d{1,2})?\\]";
        progress.forEach(l -> assertTrue(l.matches(pattern), l));
    }

    @Test
    public void test__the_counter_of_the_progress_line_has_no_noise_of_the_arithmetic() {
        assertEquals(0.65, PaxManager.round2(0.6500000000000004), 0.0);
        assertEquals(2.67, PaxManager.round2(2.674999999999999), 0.0);
        assertEquals(0.0, PaxManager.round2(0.0), 0.0);
        assertEquals("k0.65", "k" + PaxManager.round2(0.6500000000000004));
    }

    @Test
    public void test__the_start_and_the_finish_lines_have_no_ing_endings() {
        checkedInJourneys(10);

        final List<String> start = logOf(() -> tfControl.startBoarding(transportFlight));
        runBoardingFor(600);
        final List<String> finish = logOf(() -> paxManager.boarding(transportFlight).finish());

        assertTrue(start.contains("t/f #" + transportFlight.getId() + " - board START"), start.toString());
        assertTrue(finish.contains("t/f #" + transportFlight.getId() + " - board finish"), finish.toString());
        assertTrue(finish.stream().anyMatch(l -> l.startsWith("t/f #" + transportFlight.getId() + " - board found ")), finish.toString());
    }

    // ---- the first and the business class are not in a hurry: the boarding and the deboarding of them are two times slower ----

    private void bookIn(final CabinLayout.Service service, final Journeys.Journey... journeys) {
        for (final Journeys.Journey journey : journeys) {
            journey.setBookedCabinService(service);
        }
    }

    @Test
    public void test__economy_boards_at_the_normal_rate_in_a_minute() {
        checkedInJourneys(40);
        tfControl.startBoarding(transportFlight);

        runBoardingFor(Time.ONE_MINUTE);

        // 15 PAX a minute with the random factor 0.85 - 1.25: not less than 12
        assertTrue(transportFlight.getPaxOnBoard() >= 12, "PAX on board " + transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__business_and_first_board_two_times_slower() {
        for (final CabinLayout.Service service : List.of(CabinLayout.Service.J, CabinLayout.Service.F)) {
            final List<Journeys.Journey> journeys = checkedInJourneys(40);
            bookIn(service, journeys.get(0));
            tfControl.startBoarding(transportFlight);
            final int before = transportFlight.getPaxOnBoard();

            runBoardingFor(Time.ONE_MINUTE);

            // 7.5 PAX a minute with the random factor 0.85 - 1.25: not more than 9 (an economy group would have 12 and more)
            assertTrue(transportFlight.getPaxOnBoard() - before <= 9, service + ", PAX on board " + transportFlight.getPaxOnBoard());
            return; // the flight of the test is one, the second class would join the same queue
        }
    }

    @Test
    public void test__premium_economy_is_not_premium() {
        final List<Journeys.Journey> journeys = checkedInJourneys(40);
        bookIn(CabinLayout.Service.W, journeys.get(0));
        tfControl.startBoarding(transportFlight);

        runBoardingFor(Time.ONE_MINUTE);

        assertTrue(transportFlight.getPaxOnBoard() >= 12, "PAX on board " + transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__the_estimated_finish_time_counts_the_premium_pax_twice() {
        final List<Journeys.Journey> journeys = checkedInJourneys(30);
        bookIn(CabinLayout.Service.J, journeys.get(0));

        tfControl.startBoarding(transportFlight);

        // 30 business PAX are like 60 economy ones: 60 / 15 = 4 minutes
        assertEquals(startTime + 4 * Time.ONE_MINUTE + RESERVE, paxManager.boarding(transportFlight).getEstimatedFinishTime());
    }

    @Test
    public void test__the_estimate_of_a_mixed_queue_counts_only_the_premium_part_twice() {
        final List<Journeys.Journey> journeys = checkedInJourneys(10, 20);
        bookIn(CabinLayout.Service.F, journeys.get(0));

        tfControl.startBoarding(transportFlight);

        // 10 first class PAX are like 20 and 20 economy ones: 40 / 15 = 2 minutes 40 seconds
        assertEquals(startTime + 160 + RESERVE, paxManager.boarding(transportFlight).getEstimatedFinishTime());
    }

    @Test
    public void test__the_premium_journey_of_the_head_of_the_queue_slows_the_others_down_while_it_is_the_head() {
        // the rate is chosen by the journey which is processed now: the economy group after the premium one boards at the normal rate again
        final List<Journeys.Journey> journeys = checkedInJourneys(8, 60);
        bookIn(CabinLayout.Service.J, journeys.get(0));
        tfControl.startBoarding(transportFlight);

        runBoardingFor(10 * Time.ONE_MINUTE);

        assertEquals(Journeys.Status.OnBoard, journeys.get(0).getStatus());
        assertEquals(Journeys.Status.OnBoard, journeys.get(1).getStatus());
    }

    @Test
    public void test__business_passengers_leave_two_times_slower() {
        final List<Journeys.Journey> journeys = startDeboardingOf(40);
        bookIn(CabinLayout.Service.J, journeys.get(0));

        for (int i = 0; i < 6; i++) { // a minute
            deboardingTick();
        }

        // 12.5 PAX a minute with the random factor 0.85 - 1.25: not more than 15.6 have left, the economy ones would be 21 and more
        assertTrue(transportFlight.getPaxOnBoard() >= 40 - 16, "PAX on board " + transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__economy_passengers_leave_at_the_normal_rate() {
        startDeboardingOf(40);

        for (int i = 0; i < 6; i++) {
            deboardingTick();
        }

        assertTrue(transportFlight.getPaxOnBoard() <= 40 - 20, "PAX on board " + transportFlight.getPaxOnBoard());
    }
}
