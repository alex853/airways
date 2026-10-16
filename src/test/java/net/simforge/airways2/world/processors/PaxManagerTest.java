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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            paxManager.continueBoarding(transportFlight);
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

        assertEquals(startTime + 8 * Time.ONE_MINUTE + RESERVE, paxManager.getEstimatedBoardingFinishTime(transportFlight));
    }

    @Test
    public void test__estimated_finish_time_rounds_seconds_up() {
        checkedInJourneys(16); // 16 / 15 * 60 = 64 seconds

        tfControl.startBoarding(transportFlight);

        assertEquals(startTime + 64 + RESERVE, paxManager.getEstimatedBoardingFinishTime(transportFlight));
    }

    @Test
    public void test__boarding_finish_time_is_not_passed_at_start_and_is_passed_at_estimated_time() {
        checkedInJourneys(30, 30);

        tfControl.startBoarding(transportFlight);
        assertFalse(paxManager.wasBoardingFinishTimePassed(transportFlight));

        world.setWorldTime(paxManager.getEstimatedBoardingFinishTime(transportFlight));
        assertTrue(paxManager.wasBoardingFinishTimePassed(transportFlight));
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
        assertEquals(startTime + 80 + RESERVE, paxManager.getEstimatedBoardingFinishTime(transportFlight));
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
}
