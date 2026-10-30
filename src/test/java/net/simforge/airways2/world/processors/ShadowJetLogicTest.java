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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How ShadowJetLogic provides a transport flight for a flight mission: which journeys get into the pack of the flight and when they wake up (JourneyWakeCurve), and when
 * it does not provide the flight at all. The journeys do not book the flight at once, they wake up by the heartbeat and book it by themselves (JourneyProcessor), the tests
 * which look at the booking wake the journeys up by hand.
 * The aircraft is A320, the cabin layout is J12/Y144.
 */
public class ShadowJetLogicTest {
    private static final int SEATS = 12 + 144;

    private World world;
    private Cities.City cityA;
    private Cities.City cityB;
    private Cities.City cityC;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Aircrafts.Aircraft aircraft;
    private FlightMissions.Mission mission;

    @BeforeEach
    public void beforeEach() {
        final int startTime = Time.START_TIME_EPOCH_SECONDS;
        world = World.create(new InMemoryStorageStrategy(), startTime);

        final Countries.Country country = world.countries().create("TT", "Testland");
        cityA = world.cities().create(country.getId(), "Alpha City", 0, 0, 1_000_000);
        cityB = world.cities().create(country.getId(), "Bravo City", 0, 10, 1_000_000);
        cityC = world.cities().create(country.getId(), "Charlie City", 10, 0, 1_000_000);

        airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        world.airport2city().create(airportA.getId(), cityA.getId());
        world.airport2city().create(airportB.getId(), cityB.getId());

        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().create("A320", "320");
        aircraft = world.aircrafts().create(aircraftType, "A-BCDE", airportA);

        mission = createMission(airportA, airportB);
    }

    private FlightMissions.Mission createMission(final Airports.Airport from, final Airports.Airport to) {
        final int departureTime = world.getWorldTime() + 30 * Time.ONE_MINUTE;
        return world.flightMissions().createDispatchedMission(aircraft, from, to, departureTime, departureTime + 2 * Time.ONE_HOUR);
    }

    private Journeys.Journey journey(final Cities.City from, final Cities.City to, final int groupSize, final CabinLayout.Service service) {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, from.getId(), to.getId(), groupSize, service);
        journey.setLocationCityId(from.getId());
        journey.setLastKnownLocationCityId(from.getId());
        return journey;
    }

    private Journeys.Journey journey(final int groupSize) {
        return journey(cityA, cityB, groupSize, CabinLayout.Service.Y);
    }

    private Optional<TransportFlights.Flight> provideFlight() {
        ShadowJetLogic.provideTransportFlightIfRequired(world, mission);
        return world.transportFlights().byFlightMissionId(mission.getId());
    }

    // the journey is in the pack of the flight: it is woken by a heartbeat which is set by the provision, the others have none
    private static boolean isInPack(final Journeys.Journey journey) {
        return journey.getHeartbeatTime() > 0;
    }

    private static boolean isBooked(final Journeys.Journey journey) {
        return (journey.getStatus() == Journeys.Status.WaitingForBoarding || journey.getStatus() == Journeys.Status.WaitingForCheckIn)
                && journey.getTransportFlight1Id() > 0;
    }

    // all the journeys wake up now and look for tickets
    private void wakeUp(final List<Journeys.Journey> journeys) {
        journeys.forEach(j -> j.setHeartbeatTime(world.getWorldTime()));
        JourneyProcessor.process(world);
    }

    @Test
    public void test__pack_has_only_journeys_from_the_departure_city_to_the_destination_city() {
        final Journeys.Journey suitable = journey(4);
        final Journeys.Journey otherDestination = journey(cityA, cityC, 4, CabinLayout.Service.Y);
        final Journeys.Journey otherLocation = journey(cityC, cityB, 4, CabinLayout.Service.Y);

        provideFlight();

        assertTrue(isInPack(suitable));
        assertEquals(Journeys.Status.LookingForTickets, suitable.getStatus()); // it books the flight when it wakes up, not now
        assertEquals(0, suitable.getTransportFlight1Id());
        assertTrue(!isInPack(otherDestination));
        assertTrue(!isInPack(otherLocation));
        assertEquals(0, otherDestination.getTransportFlight1Id());
        assertEquals(0, otherLocation.getTransportFlight1Id());
    }

    @Test
    public void test__woken_journeys_book_the_flight_and_check_in_while_it_is_boarding() {
        final List<Journeys.Journey> journeys = List.of(journey(3), journey(5), journey(2));

        final TransportFlights.Flight transportFlight = provideFlight().orElseThrow();

        assertEquals(TransportFlights.Status.Boarding, transportFlight.getStatus());
        assertEquals(0, transportFlight.getSoldTickets());

        wakeUp(journeys);

        for (final Journeys.Journey j : journeys) {
            assertEquals(Journeys.Status.WaitingForBoarding, j.getStatus());
            assertEquals(transportFlight.getId(), j.getTransportFlight1Id());
            assertEquals(CabinLayout.Service.Y, j.getBookedCabinService());
            assertEquals(cityA.getId(), j.getLastKnownLocationCityId());
            assertEquals(0, j.getLocationCityId()); // is in the flight
        }
        assertEquals(10, transportFlight.getPaxCheckedIn());
        assertEquals(SEATS - 10, transportFlight.getRemainedTickets().getTotal());
    }

    @Test
    public void test__pack_wakes_up_by_the_curve() {
        final List<Journeys.Journey> journeys = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            journeys.add(journey(2)); // 80 PAX of 156 seats
        }
        final int now = world.getWorldTime();

        provideFlight();

        assertTrue(journeys.stream().allMatch(ShadowJetLogicTest::isInPack));
        assertTrue(journeys.stream().allMatch(j -> j.getHeartbeatTime() > now && j.getHeartbeatTime() <= now + 2400 * Time.ONE_MINUTE));
        // the curve: about 14% of the seats (22 PAX, plus minus the spread and the reserve) are woken by the fifth minute, about 36% (56 PAX) by 150 minutes
        final int paxBy5 = journeys.stream().filter(j -> j.getHeartbeatTime() <= now + 5 * Time.ONE_MINUTE).mapToInt(Journeys.Journey::getGroupSize).sum();
        final int paxBy150 = journeys.stream().filter(j -> j.getHeartbeatTime() <= now + 150 * Time.ONE_MINUTE).mapToInt(Journeys.Journey::getGroupSize).sum();
        assertTrue(paxBy5 >= 15 && paxBy5 <= 32, "woken by 5 minutes: " + paxBy5);
        assertTrue(paxBy150 >= 45 && paxBy150 <= 70, "woken by 150 minutes: " + paxBy150);
    }

    @Test
    public void test__waking_up_takes_one_attempt_down_but_not_below_zero() {
        final Journeys.Journey withAttempts = journey(2);
        withAttempts.setAttemptCounter(2);
        final Journeys.Journey withoutAttempts = journey(2);
        withoutAttempts.setAttemptCounter(0);

        provideFlight();

        assertEquals(1, withAttempts.getAttemptCounter());
        assertEquals(0, withoutAttempts.getAttemptCounter());
    }

    @Test
    public void test__a_journey_which_wakes_up_later_still_checks_in_while_the_flight_is_boarding() {
        final List<Journeys.Journey> journeys = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            journeys.add(journey(2));
        }
        final TransportFlights.Flight transportFlight = provideFlight().orElseThrow();
        final Journeys.Journey last = journeys.stream().max(java.util.Comparator.comparingInt(Journeys.Journey::getHeartbeatTime)).orElseThrow();

        world.setWorldTime(last.getHeartbeatTime()); // its moment comes, the flight is still open
        JourneyProcessor.process(world);

        assertEquals(Journeys.Status.WaitingForBoarding, last.getStatus());
        assertEquals(0, last.getLocationCityId());
        assertTrue(transportFlight.getPaxCheckedIn() >= 2);
    }

    @Test
    public void test__flight_to_a_hub_has_journeys_getting_closer_to_their_destination_together_with_direct_ones() {
        // the flight goes to the hub EGLL which is located at the city B
        final Airports.Airport hub = world.airports().create(0, 10, "LHR", "EGLL", "Heathrow");
        world.airport2city().create(hub.getId(), cityB.getId());
        mission = createMission(airportA, hub);
        final Cities.City cityFarEast = world.cities().create(1, "Far East City", 0, 20, 1_000_000); // behind the hub
        final Cities.City cityWest = world.cities().create(1, "West City", 0, -10, 1_000_000); // in the opposite direction

        final Journeys.Journey direct = journey(cityA, cityB, 3, CabinLayout.Service.Y);
        final Journeys.Journey viaHub = journey(cityA, cityFarEast, 3, CabinLayout.Service.Y);
        final Journeys.Journey notCloser = journey(cityA, cityWest, 3, CabinLayout.Service.Y);

        provideFlight();

        assertTrue(isInPack(direct));
        assertTrue(isInPack(viaHub), "the journey which gets closer via the hub should be in the pack");
        assertTrue(!isInPack(notCloser));

        // the journey which gets closer via the hub finds the flight by itself (JourneyProcessor.findHubFlight)
        wakeUp(List.of(direct, viaHub, notCloser));
        assertTrue(isBooked(direct));
        assertTrue(isBooked(viaHub));
        assertEquals(Journeys.Status.LookingForTickets, notCloser.getStatus());
    }

    @Test
    public void test__flight_to_not_a_hub_does_not_take_journeys_with_other_destinations() {
        final Cities.City cityFarEast = world.cities().create(1, "Far East City", 0, 20, 1_000_000);
        final Journeys.Journey otherDestination = journey(cityA, cityFarEast, 3, CabinLayout.Service.Y);
        final Journeys.Journey direct = journey(cityA, cityB, 3, CabinLayout.Service.Y);

        provideFlight(); // airport BBBB is not a hub

        assertTrue(isInPack(direct));
        assertTrue(!isInPack(otherDestination));
    }

    @Test
    public void test__pack_is_the_seats_of_the_flight_and_a_reserve() {
        final List<Journeys.Journey> journeys = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            journeys.add(journey(5)); // 300 PAX are waiting, the flight has 156 seats
        }

        provideFlight();

        final int packPax = journeys.stream().filter(ShadowJetLogicTest::isInPack).mapToInt(Journeys.Journey::getGroupSize).sum();
        // the economy seats (a group of five is not taken if it does not fit: 28 groups, 140 PAX of 144 seats) and the reserve of 5 to 10% of the seats (7 to 15 PAX, whole groups)
        assertTrue(packPax >= 140 + 10 && packPax <= 140 + 20, "pack PAX " + packPax);
    }

    @Test
    public void test__group_which_does_not_fit_the_cabin_is_not_in_the_pack() {
        final Journeys.Journey tooBig = journey(250);
        final Journeys.Journey fits = journey(4);

        provideFlight();

        assertTrue(!isInPack(tooBig));
        assertEquals(0, tooBig.getTransportFlight1Id());
        assertTrue(isInPack(fits));
    }

    @Test
    public void test__preferred_class_without_seats_is_upgraded_when_possible() {
        // there is no premium economy in J12/Y144, so W is upgraded to J: the pack takes the journey, and it books the flight in the upgraded class when it wakes up
        final Journeys.Journey journey = journey(cityA, cityB, 2, CabinLayout.Service.W);

        final TransportFlights.Flight transportFlight = provideFlight().orElseThrow();
        assertTrue(isInPack(journey));
        wakeUp(List.of(journey));

        assertTrue(isBooked(journey));
        assertEquals(CabinLayout.Service.J, journey.getBookedCabinService());
        assertEquals(SEATS - 2, transportFlight.getRemainedTickets().getTotal());
        assertTrue(transportFlight.getRemainedTickets().hasEnoughSeats(10, CabinLayout.Service.J));
        assertTrue(!transportFlight.getRemainedTickets().hasEnoughSeats(11, CabinLayout.Service.J));
    }

    @Test
    public void test__preferred_class_without_seats_is_downgraded_when_it_cannot_be_upgraded() {
        // there is no first class in J12/Y144 and it cannot be upgraded, so F is downgraded to J
        final Journeys.Journey journey = journey(cityA, cityB, 2, CabinLayout.Service.F);

        provideFlight();
        assertTrue(isInPack(journey));
        wakeUp(List.of(journey));

        assertTrue(isBooked(journey));
        assertEquals(CabinLayout.Service.J, journey.getBookedCabinService());
    }

    @Test
    public void test__route_from_the_airport_to_itself_is_skipped() {
        mission = createMission(airportA, airportA);
        final Journeys.Journey journey = journey(4);

        assertTrue(provideFlight().isEmpty());
        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
    }

    @Test
    public void test__route_between_airports_of_the_same_city_is_skipped() {
        world.airport2city().create(airportB.getId(), cityA.getId()); // both airports serve the city A
        final Journeys.Journey journey = journey(cityA, cityB, 4, CabinLayout.Service.Y);

        assertTrue(provideFlight().isEmpty());
        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertEquals(0, journey.getTransportFlight1Id());
    }

    @Test
    public void test__flight_without_suitable_journeys_is_still_provided_and_boards_nobody() {
        final TransportFlights.Flight transportFlight = provideFlight().orElseThrow();

        assertEquals(TransportFlights.Status.Boarding, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxCheckedIn());
        assertEquals(SEATS, transportFlight.getRemainedTickets().getTotal());
    }

    @Test
    public void test__most_active_airports_are_counted_by_departures_and_arrivals_of_the_last_days() {
        final Airports.Airport airportC = world.airports().create(10, 10, "CCC", "CCCC", "Charlie");
        for (int i = 0; i < 3; i++) {
            world.airport2airportDailyFlightStats().incrementTodayCount(airportA.getId(), airportB.getId());
        }
        world.airport2airportDailyFlightStats().incrementTodayCount(airportB.getId(), airportC.getId());

        final java.util.Map<String, Integer> top = ShadowJetLogic.mostActiveAirports(world, 10);

        assertEquals(List.of("BBBB", "AAAA", "CCCC"), new ArrayList<>(top.keySet())); // the busiest first
        assertEquals(4, top.get("BBBB")); // 3 arrivals + 1 departure
        assertEquals(3, top.get("AAAA"));
        assertEquals(1, top.get("CCCC"));
    }

    @Test
    public void test__most_active_airports_are_limited() {
        final Airports.Airport airportC = world.airports().create(10, 10, "CCC", "CCCC", "Charlie");
        world.airport2airportDailyFlightStats().incrementTodayCount(airportA.getId(), airportB.getId());
        world.airport2airportDailyFlightStats().incrementTodayCount(airportB.getId(), airportC.getId());

        assertEquals(1, ShadowJetLogic.mostActiveAirports(world, 1).size());
    }

    @Test
    public void test__most_active_airports_of_an_empty_world_is_empty() {
        final World emptyWorld = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);

        assertTrue(ShadowJetLogic.mostActiveAirports(emptyWorld, 10).isEmpty());
    }
}
