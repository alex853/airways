package net.simforge.airways2.world;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.world.datamodel.EventsToProcess;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.world.processors.FlightMissionControl;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.world.processors.PaxManager;
import net.simforge.airways2.world.processors.ShadowJetLogic;
import net.simforge.airways2.world.processors.TransportFlightControl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole life of a flight with a bunch of journeys: f/m + t/f + j/y.
 * The flight is provided the same way as ShadowJet does it for a VATSIM flight, the pilot (the mission is in PC mode)
 * is driven by the test: blocks off, takeoff, landing, blocks on. Everything else is processed by the world.
 * <p>
 * The happy path goes first, then the variations and the deviations.
 */
public class FlightWorkflowTest {
    private static final int[] GROUP_SIZES = {3, 4, 5, 2, 6, 4};
    private static final int TOTAL_PAX = 24;

    private World world;
    private Cities.City cityA;
    private Cities.City cityB;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private FlightMissions.Mission mission;
    private TransportFlightControl tfControl;
    private TransportFlights.Flight transportFlight;
    private List<Journeys.Journey> journeys;

    @BeforeEach
    public void beforeEach() {
        final int startTime = Time.START_TIME_EPOCH_SECONDS;
        world = World.create(new InMemoryStorageStrategy(), startTime);

        final Countries.Country country = world.countries().create("TT", "Testland");
        cityA = world.cities().create(country.getId(), "Alpha City", 0, 0, 1_000_000);
        cityB = world.cities().create(country.getId(), "Bravo City", 0, 10, 1_000_000);

        airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        world.airport2city().create(airportA.getId(), cityA.getId());
        world.airport2city().create(airportB.getId(), cityB.getId());

        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().create("A320", "320");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-BCDE", airportA);

        final int departureTime = startTime + 30 * Time.ONE_MINUTE;
        mission = world.flightMissions().createDispatchedMission(
                aircraft, airportA, airportB, departureTime, departureTime + 2 * Time.ONE_HOUR);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);

        tfControl = world.transportFlightControl();

        journeys = new ArrayList<>();
        for (final int groupSize : GROUP_SIZES) {
            final Journeys.Journey journey = world.journeys().create(
                    Journeys.Status.LookingForTickets, cityA.getId(), cityB.getId(), groupSize, CabinLayout.Service.Y);
            journey.setLocationCityId(cityA.getId());
            journey.setLastKnownLocationCityId(cityA.getId());
            journeys.add(journey);
        }
    }

    // ---- the happy path ----

    @Test
    public void test__happy_path__everybody_boards_flies_deboards_and_stays_at_the_destination() {
        provideFlight();

        // booked, a part is checked-in at once and the others check-in later, the boarding has started
        assertEquals(TransportFlights.Status.Boarding, transportFlight.getStatus());
        journeys.forEach(j -> {
            assertEquals(transportFlight.getId(), j.getTransportFlight1Id());
            assertEquals(cityA.getId(), j.getLastKnownLocationCityId());
            if (j.getStatus() == Journeys.Status.WaitingForBoarding) {
                assertEquals(0, j.getLocationCityId()); // in the flight
            } else {
                assertEquals(Journeys.Status.WaitingForCheckIn, j.getStatus());
                assertEquals(cityA.getId(), j.getLocationCityId());
            }
        });

        // the boarding is over: all are on board, also the ones who checked-in later
        runUntil(30 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.WaitingForDeparture);
        assertEquals(TransportFlights.Status.WaitingForDeparture, transportFlight.getStatus());
        journeys.forEach(j -> assertEquals(Journeys.Status.OnBoard, j.getStatus()));
        assertEquals(TOTAL_PAX, transportFlight.getPaxOnBoard());

        // the flight itself
        startFlightAndFlyToTheGate();
        assertEquals(TransportFlights.Status.WaitingForDeboarding, transportFlight.getStatus());
        assertEquals(FlightMissions.Status.Postflight, mission.getStatus());

        // the deboarding starts automatically, the journeys deboard randomly during it
        runUntil(10 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.Deboarding);
        assertEquals(TransportFlights.Status.Deboarding, transportFlight.getStatus());

        runUntil(4 * Time.ONE_HOUR, this::allJourneysAreSpendingTheirTime);
        journeys.forEach(j -> {
            assertEquals(Journeys.Status.SpendingTheirTime, j.getStatus());
            assertEquals(cityB.getId(), j.getLocationCityId());
            assertEquals(cityB.getId(), j.getLastKnownLocationCityId());
            assertEquals(0, j.getTransportFlight1Id());
        });
        assertEquals(TransportFlights.Status.Finished, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());
    }

    // ---- variations ----

    @Test
    public void test__variation__pilot_departs_in_the_middle_of_the_boarding__not_boarded_journeys_stay_in_the_departure_city() {
        provideFlight();

        runFor(40); // a part of the journeys is on board, the rest is still waiting for boarding
        final List<Journeys.Journey> onBoard = journeysIn(Journeys.Status.OnBoard);
        // the ones who have not boarded yet and the ones who have not even checked-in yet
        final List<Journeys.Journey> notBoarded = journeys.stream()
                .filter(j -> j.getStatus() == Journeys.Status.WaitingForBoarding || j.getStatus() == Journeys.Status.WaitingForCheckIn)
                .toList();
        assertTrue(!onBoard.isEmpty() && !notBoarded.isEmpty(), "the test expects a partly boarded flight");

        startFlightAndDepart();
        assertEquals(TransportFlights.Status.Departure, transportFlight.getStatus());

        runFor(6 * Time.ONE_MINUTE);

        onBoard.forEach(j -> assertEquals(Journeys.Status.OnBoard, j.getStatus()));
        // the journey which was being boarded at the moment of the departure boards, the others are too late
        final List<Journeys.Journey> boardedAtTheDeparture = notBoarded.stream()
                .filter(j -> j.getStatus() == Journeys.Status.OnBoard)
                .toList();
        assertTrue(boardedAtTheDeparture.size() <= 1, "boarded at the departure " + boardedAtTheDeparture.size());
        notBoarded.stream().filter(j -> !boardedAtTheDeparture.contains(j)).forEach(j -> {
            assertTrue(EnumSet.of(Journeys.Status.TooLateToBoard, Journeys.Status.LookingForTickets).contains(j.getStatus()),
                    "unexpected status " + j.getStatus());
            assertEquals(cityA.getId(), j.getLocationCityId());
            assertEquals(0, j.getTransportFlight1Id());
        });
    }

    @Test
    public void test__variation__stopover_journey_is_not_considered_and_a_journey_without_a_seat_is_not_booked() {
        // a group which does not fit the cabin is not booked, the flight is provided for the others
        final Journeys.Journey tooBig = world.journeys().create(
                Journeys.Status.LookingForTickets, cityA.getId(), cityB.getId(), 250, CabinLayout.Service.Y);
        tooBig.setLocationCityId(cityA.getId());

        provideFlight();

        assertEquals(Journeys.Status.LookingForTickets, tooBig.getStatus());
        assertEquals(0, tooBig.getTransportFlight1Id());
        journeys.forEach(j -> assertTrue(j.getTransportFlight1Id() > 0, "j/y #" + j.getId() + " is not booked"));
    }

    // ---- deviations ----

    @Test
    public void test__deviation__transport_flight_is_lost_while_journeys_wait_for_boarding__they_return_to_the_departure_city() {
        provideFlight();
        // a part of the journeys is checked-in and waits for boarding, the others wait for their check-in time
        assertTrue(!journeysIn(Journeys.Status.WaitingForBoarding).isEmpty() && !journeysIn(Journeys.Status.WaitingForCheckIn).isEmpty());

        world.transportFlights().deleteById(transportFlight.getId());
        runFor(21 * Time.ONE_MINUTE); // the last check-in time is in 20 minutes

        assertJourneysAreReturnedTo(cityA);
    }

    @Test
    public void test__deviation__transport_flight_is_lost_while_journeys_wait_for_deboarding__they_return_to_the_city_before_the_flight() {
        boardEverybodyFlyAndStartDeboarding();
        assertEquals(TOTAL_PAX, journeysIn(Journeys.Status.WaitingForDeboarding).stream().mapToInt(Journeys.Journey::getGroupSize).sum());

        world.transportFlights().deleteById(transportFlight.getId());
        // the journeys look at their flight every 5 minutes (the safety net)
        runFor(PaxManager.Deboarding.JOURNEY_WATCH_INTERVAL + Time.ONE_MINUTE);

        journeys.forEach(j -> {
            assertEquals(Journeys.Status.LookingForTickets, j.getStatus());
            assertEquals(cityA.getId(), j.getLocationCityId());
            assertEquals(0, j.getTransportFlight1Id());
        });
    }

    @Test
    public void test__deviation__unexpected_transport_flight_status_while_journeys_wait_for_deboarding__they_are_orphaned() {
        boardEverybodyFlyAndStartDeboarding();

        transportFlight.setStatus(TransportFlights.Status.Flying);
        runFor(PaxManager.Deboarding.JOURNEY_WATCH_INTERVAL + Time.ONE_MINUTE);

        journeys.forEach(j -> assertEquals(Journeys.Status.Orphaned, j.getStatus()));
    }

    @Test
    public void test__deviation__transport_flight_is_finished_before_the_journeys_deboard__they_deboard_anyway() {
        boardEverybodyFlyAndStartDeboarding();

        transportFlight.setStatus(TransportFlights.Status.Finished);
        transportFlight.setHeartbeatTime(0);
        transportFlight.setPaxOnBoard(0);

        runUntil(4 * Time.ONE_HOUR, this::allJourneysAreSpendingTheirTime);

        journeys.forEach(j -> {
            assertEquals(Journeys.Status.SpendingTheirTime, j.getStatus());
            assertEquals(cityB.getId(), j.getLocationCityId());
        });
    }

    @Test
    public void test__deviation__corrupted_pax_on_board_is_healed_by_the_deboarding__the_flight_finishes_when_the_last_journey_has_left() {
        boardEverybodyFlyAndStartDeboarding();
        transportFlight.setPaxOnBoard(transportFlight.getPaxOnBoard() + 5); // 'phantom' PAX who are not in any journey

        // PAX on board is counted from the journeys which are still waiting, so the phantom PAX disappear at the first tick
        runFor(10);
        assertTrue(transportFlight.getPaxOnBoard() <= TOTAL_PAX, "PAX on board " + transportFlight.getPaxOnBoard());

        runUntil(Time.ONE_HOUR / 2, () -> transportFlight.getStatus() == TransportFlights.Status.Finished);
        assertEquals(TransportFlights.Status.Finished, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());

        runUntil(4 * Time.ONE_HOUR, this::allJourneysAreSpendingTheirTime);
        journeys.forEach(j -> assertEquals(Journeys.Status.SpendingTheirTime, j.getStatus()));
    }

    @Test
    public void test__deboarding__pax_on_board_goes_down_gradually_while_the_world_runs() {
        boardEverybodyFlyAndStartDeboarding();
        assertEquals(TOTAL_PAX, transportFlight.getPaxOnBoard());

        final List<Integer> pax = new ArrayList<>();
        for (int i = 0; i < 60 && transportFlight.getStatus() == TransportFlights.Status.Deboarding; i++) {
            runFor(10);
            pax.add(transportFlight.getPaxOnBoard());
        }

        assertEquals(TransportFlights.Status.Finished, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());
        // 24 PAX at 25 PAX per minute are out in about a minute, the number goes down by a few PAX every 10 seconds
        assertTrue(pax.size() <= 12, "ticks " + pax.size() + " " + pax);
        assertTrue(pax.stream().distinct().count() >= 4, "PAX on board values: " + pax);
        int previous = TOTAL_PAX;
        for (final int value : pax) {
            assertTrue(value <= previous, "PAX on board goes up: " + pax);
            assertTrue(previous - value <= 8, "PAX on board jumps: " + pax);
            previous = value;
        }
    }

    @Test
    public void test__deboarding__nobody_waits_for_a_random_roll_every_journey_leaves_within_a_few_minutes() {
        boardEverybodyFlyAndStartDeboarding();

        runUntil(10 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.Finished);

        assertEquals(TransportFlights.Status.Finished, transportFlight.getStatus());
        journeys.forEach(j -> assertTrue(j.getStatus() != Journeys.Status.WaitingForDeboarding && j.getStatus() != Journeys.Status.OnBoard));
    }

    // ---- the pilot disconnects (the same actions as PilotContext does for VATSIM pilots) ----

    @Test
    public void test__disconnect__pilot_goes_offline_on_the_ground_during_the_boarding__flight_is_cancelled_and_journeys_stay_in_the_departure_city() {
        provideFlight();
        world.flightMissionControl().start(mission); // Preflight, as PilotContext dispatches and starts the mission
        runFor(40); // a part of the journeys is on board

        // PilotContext.mission_cancelBeforeTakeoffIfExists
        world.flightMissionControl().cancelFlightAndReturnAircraftToDepartureAirport(mission);
        world.transportFlightControl().cancelTransportFlightIfExists(mission);

        assertEquals(TransportFlights.Status.Cancelled, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());
        assertJourneysAreReturnedTo(cityA);
    }

    @Test
    public void test__disconnect__pilot_goes_offline_while_flying_longer_than_allowed__flight_is_cancelled_and_journeys_return_to_the_departure_city() {
        provideFlight();
        runUntil(30 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.WaitingForDeparture);
        world.flightMissionControl().start(mission);
        world.flightMissionControl().blocksOff(mission);
        world.flightMissionControl().takeoff(mission);
        runFor(10 * Time.ONE_MINUTE);
        journeys.forEach(j -> assertEquals(Journeys.Status.OnBoard, j.getStatus()));

        // PilotContext.mission_cancelFromFlying
        world.flightMissionControl().cancelFlightAndReturnAircraftToDepartureAirport(mission);
        world.transportFlightControl().cancelTransportFlightIfExists(mission);

        assertEquals(TransportFlights.Status.Cancelled, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());
        assertJourneysAreReturnedTo(cityA);
    }

    @Test
    public void test__disconnect__pilot_goes_offline_right_after_landing__flight_is_finished_and_journeys_deboard_as_usual() {
        provideFlight();
        runUntil(30 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.WaitingForDeparture);
        world.flightMissionControl().start(mission);
        world.flightMissionControl().blocksOff(mission);
        world.flightMissionControl().takeoff(mission);
        runFor(Time.ONE_MINUTE);
        world.flightMissionControl().landing(mission, airportB);
        assertEquals(TransportFlights.Status.Arrival, transportFlight.getStatus());

        // PilotContext.noPositionInReport on the Arriving stage: mission_blocksOnAndFinish
        world.flightMissionControl().blocksOn(mission);
        world.flightMissionControl().finish(mission);
        ShadowJetLogic.deboardTransportFlightIfExists(world, mission);

        assertEquals(TransportFlights.Status.Deboarding, transportFlight.getStatus());
        assertEquals(TOTAL_PAX, journeysIn(Journeys.Status.WaitingForDeboarding).stream().mapToInt(Journeys.Journey::getGroupSize).sum());

        runUntil(4 * Time.ONE_HOUR, this::allJourneysAreSpendingTheirTime);
        journeys.forEach(j -> {
            assertEquals(Journeys.Status.SpendingTheirTime, j.getStatus());
            assertEquals(cityB.getId(), j.getLocationCityId());
        });
        assertEquals(TransportFlights.Status.Finished, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__disconnect__pilot_taxied_to_the_gate_but_went_offline_before_the_arrival_is_detected__same_as_after_landing() {
        // the arrival (blocks-on) is detected with a lag, so the pilot context is still in the Arriving stage,
        // the aircraft is at the gate, but the mission is in Arrival status: the same actions as above
        provideFlight();
        runUntil(30 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.WaitingForDeparture);
        world.flightMissionControl().start(mission);
        world.flightMissionControl().blocksOff(mission);
        world.flightMissionControl().takeoff(mission);
        runFor(Time.ONE_MINUTE);
        world.flightMissionControl().landing(mission, airportB);
        runFor(5 * Time.ONE_MINUTE); // taxiing in, nothing happens with the t/f in the meantime
        assertEquals(TransportFlights.Status.Arrival, transportFlight.getStatus());
        journeys.forEach(j -> assertEquals(Journeys.Status.OnBoard, j.getStatus()));

        world.flightMissionControl().blocksOn(mission);
        world.flightMissionControl().finish(mission);
        ShadowJetLogic.deboardTransportFlightIfExists(world, mission);

        runUntil(4 * Time.ONE_HOUR, this::allJourneysAreSpendingTheirTime);
        journeys.forEach(j -> assertEquals(cityB.getId(), j.getLocationCityId()));
        assertEquals(TransportFlights.Status.Finished, transportFlight.getStatus());
    }

    @Test
    public void test__variation__pilot_departs_in_the_middle_of_the_boarding__pax_on_board_is_exact_and_flight_deboards_to_zero() {
        provideFlight();
        runFor(40);
        final int paxOnBoardBeforeDeparture = transportFlight.getPaxOnBoard();

        startFlightAndDepart();
        final int onBoard = journeysIn(Journeys.Status.OnBoard).stream().mapToInt(Journeys.Journey::getGroupSize).sum();
        assertEquals(onBoard, transportFlight.getPaxOnBoard()); // no 'phantom' PAX of the boarding counter
        assertTrue(transportFlight.getPaxOnBoard() >= paxOnBoardBeforeDeparture, "PAX on board must not jump down");

        world.flightMissionControl().takeoff(mission);
        runFor(Time.ONE_MINUTE);
        world.flightMissionControl().landing(mission, airportB);
        world.flightMissionControl().blocksOn(mission);
        tfControl.scheduleAutomaticDeboarding(transportFlight);

        runUntil(4 * Time.ONE_HOUR, () -> transportFlight.getStatus() == TransportFlights.Status.Finished);
        assertEquals(TransportFlights.Status.Finished, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());
    }

    // ---- cancellation of the flight: what happens with the t/f and the journeys ----

    @Test
    public void test__cancellation__pilot_goes_offline_after_blocks_off_before_takeoff__flight_is_cancelled_and_journeys_stay_in_the_departure_city() {
        provideFlight();
        runUntil(30 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.WaitingForDeparture);
        startFlightAndDepart();
        assertEquals(FlightMissions.Status.Departure, mission.getStatus());
        journeys.forEach(j -> assertEquals(Journeys.Status.OnBoard, j.getStatus()));

        // PilotContext.mission_cancelBeforeTakeoffIfExists in Departure status
        world.flightMissionControl().cancelFlightAndReturnAircraftToDepartureAirport(mission);
        world.transportFlightControl().cancelTransportFlightIfExists(mission);

        assertEquals(FlightMissions.Status.Cancelled, mission.getStatus());
        assertEquals(TransportFlights.Status.Cancelled, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());
        assertJourneysAreReturnedTo(cityA);
        assertAircraftIsBackAtTheDepartureAirport();
    }

    @Test
    public void test__cancellation__t_f_stays_cancelled_and_does_not_come_back_to_life_in_the_world() {
        provideFlight();
        runUntil(30 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.WaitingForDeparture);
        world.flightMissionControl().start(mission);
        world.flightMissionControl().cancelFlightAndReturnAircraftToDepartureAirport(mission);
        world.transportFlightControl().cancelTransportFlightIfExists(mission);

        runFor(2 * Time.ONE_HOUR);

        assertEquals(TransportFlights.Status.Cancelled, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());
        journeys.forEach(j -> assertEquals(0, j.getTransportFlight1Id()));
    }

    // ---- start of the mission: the result and what the callers do with a failure ----

    @Test
    public void test__start__mission_which_is_not_dispatched_anymore_is_refused_and_goes_on() {
        provideFlight();
        runUntil(30 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.WaitingForDeparture);
        assertTrue(world.flightMissionControl().start(mission).started());
        assertEquals(FlightMissions.Status.Preflight, mission.getStatus());

        final FlightMissionControl.StartResult second = world.flightMissionControl().start(mission); // the second start of the same mission

        assertFalse(second.started());
        assertTrue(second.reason().contains("Preflight"), second.reason());
        assertEquals(FlightMissions.Status.Preflight, mission.getStatus()); // the mission is not cancelled by the failed start
        assertEquals(TransportFlights.Status.WaitingForDeparture, transportFlight.getStatus());
        journeys.forEach(j -> assertEquals(transportFlight.getId(), j.getTransportFlight1Id()));
    }

    @Test
    public void test__start__aircraft_is_not_at_the_departure_airport__start_is_refused_and_nothing_is_changed() {
        provideFlight();
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setLocationAirportId(airportB.getId());

        final FlightMissionControl.StartResult result = world.flightMissionControl().start(mission);

        assertFalse(result.started());
        assertTrue(result.reason().contains("location airport"), result.reason());
        assertEquals(FlightMissions.Status.Dispatched, mission.getStatus());
        assertEquals(Aircrafts.OperationalStatus.Idle, aircraft.getOperationalStatus());
        assertEquals(airportB.getId(), aircraft.getLocationAirportId());
        assertEquals(0, aircraft.getFlightMissionId());
    }

    @Test
    public void test__start__aircraft_is_busy__start_is_refused_and_the_aircraft_keeps_its_own_flight() {
        provideFlight();
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Active);
        aircraft.setFlightMissionId(12345);

        final FlightMissionControl.StartResult result = world.flightMissionControl().start(mission);

        assertFalse(result.started());
        assertEquals(FlightMissions.Status.Dispatched, mission.getStatus());
        assertEquals(Aircrafts.OperationalStatus.Active, aircraft.getOperationalStatus());
        assertEquals(12345, aircraft.getFlightMissionId());
    }

    @Test
    public void test__start__aircraft_in_the_air_is_refused() {
        provideFlight();
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setLocationStatus(Aircrafts.LocationStatus.Flying);

        assertFalse(world.flightMissionControl().start(mission).started());
        assertEquals(FlightMissions.Status.Dispatched, mission.getStatus());
    }

    @Test
    public void test__cancel_dispatched__t_f_is_cancelled_and_journeys_return_to_the_departure_city_and_stay_there() {
        provideFlight();
        runUntil(30 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.WaitingForDeparture);
        journeys.forEach(j -> assertEquals(Journeys.Status.OnBoard, j.getStatus()));
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setLocationAirportId(airportB.getId());
        final FlightMissionControl.StartResult result = world.flightMissionControl().start(mission);
        assertFalse(result.started());

        world.flightMissionControl().cancelDispatched(mission, result.reason());

        assertEquals(FlightMissions.Status.Cancelled, mission.getStatus());
        assertEquals(TransportFlights.Status.Cancelled, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());
        assertJourneysAreReturnedTo(cityA);
        assertEquals(airportB.getId(), aircraft.getLocationAirportId()); // the aircraft is not touched by the cancellation

        runFor(2 * Time.ONE_HOUR);

        assertEquals(TransportFlights.Status.Cancelled, transportFlight.getStatus());
        journeys.forEach(j -> assertEquals(0, j.getTransportFlight1Id()));
    }

    @Test
    public void test__cancel_dispatched__mission_without_a_t_f_is_cancelled_without_trouble() {
        world.flightMissionControl().cancelDispatched(mission, "test");

        assertEquals(FlightMissions.Status.Cancelled, mission.getStatus());
        assertTrue(world.transportFlights().byFlightMissionId(mission.getId()).isEmpty());
    }

    @Test
    public void test__cancel_dispatched__started_mission_can_not_be_cancelled_this_way() {
        assertTrue(world.flightMissionControl().start(mission).started());

        assertThrows(IllegalArgumentException.class, () -> world.flightMissionControl().cancelDispatched(mission, "test"));
        assertEquals(FlightMissions.Status.Preflight, mission.getStatus());
    }

    // ---- NPC pilot goes on duty (PilotOnDuty event of the world) ----

    private FlightMissions.Mission scheduleNpcMissionWithFlight() {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        final FlightMissions.Mission npcMission = FlightMissionHelper.scheduleDispatchedMission(
                world, aircraft, airportA, airportB, world.getWorldTime() + 45 * Time.ONE_MINUTE);
        assertEquals(FlightMissions.CharacterMode.NPC, npcMission.getCharacterMode());

        ShadowJetLogic.provideTransportFlightIfRequired(world, npcMission);
        transportFlight = world.transportFlights().byFlightMissionId(npcMission.getId()).orElseThrow();
        return npcMission;
    }

    @Test
    public void test__pilot_on_duty__npc_mission_is_started_when_everything_is_fine() {
        final FlightMissions.Mission npcMission = scheduleNpcMissionWithFlight();

        runUntil(30 * Time.ONE_MINUTE, () -> npcMission.getStatus() != FlightMissions.Status.Dispatched);

        assertEquals(FlightMissions.Status.Preflight, npcMission.getStatus());
        assertEquals(npcMission.getId(), world.aircrafts().byId(npcMission.getAircraftId()).orElseThrow().getFlightMissionId());
        assertTrue(transportFlight.getStatus() != TransportFlights.Status.Cancelled);
    }

    @Test
    public void test__pilot_on_duty__npc_mission_is_cancelled_with_its_t_f_when_the_aircraft_has_gone__journeys_return_to_the_departure_city() {
        final FlightMissions.Mission npcMission = scheduleNpcMissionWithFlight();
        runFor(10 * Time.ONE_MINUTE); // the flight is selling and checking-in the journeys
        assertEquals(FlightMissions.Status.Dispatched, npcMission.getStatus());
        assertTrue(journeys.stream().allMatch(j -> j.getTransportFlight1Id() == transportFlight.getId()));
        world.aircrafts().byId(npcMission.getAircraftId()).orElseThrow().setLocationAirportId(airportB.getId());

        runUntil(30 * Time.ONE_MINUTE, () -> npcMission.getStatus() != FlightMissions.Status.Dispatched);

        assertEquals(FlightMissions.Status.Cancelled, npcMission.getStatus());
        assertEquals(TransportFlights.Status.Cancelled, transportFlight.getStatus());
        assertJourneysAreReturnedTo(cityA);

        runFor(Time.ONE_HOUR);

        assertEquals(TransportFlights.Status.Cancelled, transportFlight.getStatus());
        journeys.forEach(j -> assertEquals(0, j.getTransportFlight1Id()));
    }

    @Test
    public void test__pilot_on_duty__second_event_for_a_started_mission_does_not_cancel_it() {
        final FlightMissions.Mission npcMission = scheduleNpcMissionWithFlight();
        runUntil(30 * Time.ONE_MINUTE, () -> npcMission.getStatus() != FlightMissions.Status.Dispatched);
        assertEquals(FlightMissions.Status.Preflight, npcMission.getStatus());

        world.eventsToProcess().sendEvent(EventsToProcess.Type.PilotOnDuty, npcMission.getId(), world.getWorldTime());
        runFor(Time.ONE_MINUTE);

        assertTrue(npcMission.getStatus() != FlightMissions.Status.Cancelled);
        assertTrue(transportFlight.getStatus() != TransportFlights.Status.Cancelled);
    }

    private void assertAircraftIsBackAtTheDepartureAirport() {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        assertEquals(Aircrafts.OperationalStatus.Idle, aircraft.getOperationalStatus());
        assertEquals(Aircrafts.LocationStatus.ParkedAtAirport, aircraft.getLocationStatus());
        assertEquals(airportA.getId(), aircraft.getLocationAirportId());
        assertEquals(0, aircraft.getFlightMissionId());
    }

    private void assertJourneysAreReturnedTo(final Cities.City city) {
        journeys.forEach(j -> {
            assertEquals(Journeys.Status.LookingForTickets, j.getStatus());
            assertEquals(city.getId(), j.getLocationCityId());
            assertEquals(0, j.getTransportFlight1Id());
        });
    }

    // ---- helpers ----

    private void provideFlight() {
        ShadowJetLogic.provideTransportFlightIfRequired(world, mission);
        transportFlight = world.transportFlights().byFlightMissionId(mission.getId()).orElseThrow();
    }

    private void startFlightAndDepart() {
        world.flightMissionControl().start(mission);
        world.flightMissionControl().blocksOff(mission);
    }

    private void startFlightAndFlyToTheGate() {
        startFlightAndDepart();
        world.flightMissionControl().takeoff(mission);
        runFor(Time.ONE_MINUTE);
        world.flightMissionControl().landing(mission, airportB);
        world.flightMissionControl().blocksOn(mission);
        tfControl.scheduleAutomaticDeboarding(transportFlight); // as the world does it for an NPC flight
    }

    private void boardEverybodyFlyAndStartDeboarding() {
        provideFlight();
        runUntil(30 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.WaitingForDeparture);
        startFlightAndFlyToTheGate();
        runUntil(10 * Time.ONE_MINUTE, () -> transportFlight.getStatus() == TransportFlights.Status.Deboarding);
        assertEquals(TransportFlights.Status.Deboarding, transportFlight.getStatus());
    }

    private List<Journeys.Journey> journeysIn(final Journeys.Status status) {
        return journeys.stream().filter(j -> j.getStatus() == status).toList();
    }

    private boolean allJourneysHaveDeboarded() {
        return journeys.stream().noneMatch(j -> j.getStatus() == Journeys.Status.OnBoard
                || j.getStatus() == Journeys.Status.WaitingForDeboarding);
    }

    private boolean allJourneysAreSpendingTheirTime() {
        final Set<Journeys.Status> done = EnumSet.of(Journeys.Status.SpendingTheirTime);
        return journeys.stream().allMatch(j -> done.contains(j.getStatus()));
    }

    private void runFor(final int seconds) {
        final int finishTime = world.getWorldTime() + seconds;
        while (world.getWorldTime() < finishTime) {
            world.process(world.getWorldTime() + 10);
            assertHeartbeatIndexesAreConsistent();
        }
    }

    private void runUntil(final int maxSeconds, final BooleanSupplier condition) {
        final int finishTime = world.getWorldTime() + maxSeconds;
        while (world.getWorldTime() < finishTime && !condition.getAsBoolean()) {
            world.process(world.getWorldTime() + 10);
            assertHeartbeatIndexesAreConsistent();
        }
    }

    // the heartbeat indexes of the storages must match their data after every step of the world, whatever the scenario does
    private void assertHeartbeatIndexesAreConsistent() {
        assertEquals(List.of(), world.journeys().checkHeartbeatIndex(), "journeys heartbeat index");
        assertEquals(List.of(), world.transportFlights().checkHeartbeatIndex(), "transport flights heartbeat index");
        assertEquals(List.of(), world.flightMissions().checkHeartbeatIndex(), "flight missions heartbeat index");
    }

    @AfterEach
    public void heartbeatIndexesAreConsistentAtTheEnd() {
        assertHeartbeatIndexesAreConsistent();
    }
}
