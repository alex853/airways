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
 * How ShadowJetLogic provides a transport flight for a (VATSIM) flight mission: which journeys it books and checks in,
 * how many, in which cabin class, and when it does not provide the flight at all.
 * The aircraft is A320, the cabin layout is J12/Y144.
 */
public class ShadowJetLogicTest {
    private static final int SEATS = 12 + 144;
    // the fill is random from 70% to 100% of seats
    private static final int MIN_BOOKED_PAX = (int) (SEATS * 0.70);

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

    // booked journeys are either checked-in at once (WaitingForBoarding) or check-in later at a random time (WaitingForCheckIn)
    private static boolean isBooked(final Journeys.Journey journey) {
        return (journey.getStatus() == Journeys.Status.WaitingForBoarding || journey.getStatus() == Journeys.Status.WaitingForCheckIn)
                && journey.getTransportFlight1Id() > 0;
    }

    @Test
    public void test__books_only_journeys_from_the_departure_city_to_the_destination_city() {
        final Journeys.Journey suitable = journey(4);
        final Journeys.Journey otherDestination = journey(cityA, cityC, 4, CabinLayout.Service.Y);
        final Journeys.Journey otherLocation = journey(cityC, cityB, 4, CabinLayout.Service.Y);

        provideFlight();

        assertTrue(isBooked(suitable));
        assertEquals(Journeys.Status.LookingForTickets, otherDestination.getStatus());
        assertEquals(0, otherDestination.getTransportFlight1Id());
        assertEquals(Journeys.Status.LookingForTickets, otherLocation.getStatus());
        assertEquals(0, otherLocation.getTransportFlight1Id());
    }

    @Test
    public void test__booked_journeys_are_checked_in_at_once_or_later_and_the_boarding_starts() {
        final List<Journeys.Journey> journeys = List.of(journey(3), journey(5), journey(2));
        final int now = world.getWorldTime();

        final TransportFlights.Flight transportFlight = provideFlight().orElseThrow();

        assertEquals(TransportFlights.Status.Boarding, transportFlight.getStatus());
        int checkedInPax = 0;
        for (final Journeys.Journey j : journeys) {
            assertTrue(isBooked(j), "unexpected status " + j.getStatus());
            assertEquals(transportFlight.getId(), j.getTransportFlight1Id());
            assertEquals(CabinLayout.Service.Y, j.getBookedCabinService());
            assertEquals(cityA.getId(), j.getLastKnownLocationCityId());
            if (j.getStatus() == Journeys.Status.WaitingForBoarding) {
                assertEquals(0, j.getLocationCityId()); // is in the flight
                checkedInPax += j.getGroupSize();
            } else {
                assertEquals(cityA.getId(), j.getLocationCityId()); // is still in the city and will check-in later
                assertTrue(j.getHeartbeatTime() >= now && j.getHeartbeatTime() <= now + 20 * Time.ONE_MINUTE,
                        "check-in time is out of the window");
            }
        }
        assertEquals(checkedInPax, transportFlight.getPaxCheckedIn());
        assertEquals(SEATS - 10, transportFlight.getRemainedTickets().getTotal()); // all the tickets are sold
    }

    @Test
    public void test__booked_journeys_are_split_to_check_in_groups_30_30_20_20() {
        final List<Journeys.Journey> journeys = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            journeys.add(journey(2)); // 40 PAX are less than the fill of the flight, all are booked
        }
        final int now = world.getWorldTime();

        provideFlight();

        final List<Journeys.Journey> atOnce = journeys.stream().filter(j -> j.getStatus() == Journeys.Status.WaitingForBoarding).toList();
        final List<Journeys.Journey> later = journeys.stream().filter(j -> j.getStatus() == Journeys.Status.WaitingForCheckIn).toList();
        assertEquals(6, atOnce.size()); // 30%
        assertEquals(14, later.size());

        // 6 journeys in 0..5 minutes (30%), 4 in 5..10 (20%), 4 in 10..20 (20%), the borders may be shared by the neighbours
        assertTrue(later.stream().allMatch(j -> j.getHeartbeatTime() >= now && j.getHeartbeatTime() <= now + 20 * Time.ONE_MINUTE));
        assertTrue(later.stream().filter(j -> j.getHeartbeatTime() <= now + 5 * Time.ONE_MINUTE).count() >= 6, "group 0..5");
        assertTrue(later.stream().filter(j -> j.getHeartbeatTime() <= now + 10 * Time.ONE_MINUTE).count() >= 10, "groups 0..10");
        assertTrue(later.stream().filter(j -> j.getHeartbeatTime() >= now + 10 * Time.ONE_MINUTE).count() >= 4, "group 10..20");
        assertTrue(later.stream().filter(j -> j.getHeartbeatTime() > now + 10 * Time.ONE_MINUTE).count() <= 4, "group 10..20");
    }

    @Test
    public void test__delayed_check_in_works_while_the_flight_is_boarding() {
        final List<Journeys.Journey> journeys = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            journeys.add(journey(2));
        }
        final TransportFlights.Flight transportFlight = provideFlight().orElseThrow();
        final Journeys.Journey delayed = journeys.stream()
                .filter(j -> j.getStatus() == Journeys.Status.WaitingForCheckIn)
                .min(java.util.Comparator.comparingInt(Journeys.Journey::getHeartbeatTime))
                .orElseThrow();
        final int paxCheckedInBefore = transportFlight.getPaxCheckedIn();

        world.setWorldTime(delayed.getHeartbeatTime()); // the earliest check-in time
        JourneyProcessor.process(world);

        assertEquals(Journeys.Status.WaitingForBoarding, delayed.getStatus());
        assertEquals(0, delayed.getLocationCityId());
        assertTrue(transportFlight.getPaxCheckedIn() >= paxCheckedInBefore + 2);
    }

    @Test
    public void test__flight_to_a_hub_takes_journeys_getting_closer_to_their_destination_together_with_direct_ones() {
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

        assertTrue(isBooked(direct));
        assertTrue(isBooked(viaHub), "the journey which gets closer via the hub should be booked");
        assertEquals(Journeys.Status.LookingForTickets, notCloser.getStatus());
    }

    @Test
    public void test__flight_to_not_a_hub_does_not_take_journeys_with_other_destinations() {
        final Cities.City cityFarEast = world.cities().create(1, "Far East City", 0, 20, 1_000_000);
        final Journeys.Journey otherDestination = journey(cityA, cityFarEast, 3, CabinLayout.Service.Y);
        final Journeys.Journey direct = journey(cityA, cityB, 3, CabinLayout.Service.Y);

        provideFlight(); // airport BBBB is not a hub

        assertTrue(isBooked(direct));
        assertEquals(Journeys.Status.LookingForTickets, otherDestination.getStatus());
    }

    @Test
    public void test__fill_percent_limits_the_booked_pax() {
        final List<Journeys.Journey> journeys = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            journeys.add(journey(5)); // 300 PAX are waiting, the flight has 156 seats
        }

        provideFlight();

        final int bookedPax = journeys.stream().filter(ShadowJetLogicTest::isBooked).mapToInt(Journeys.Journey::getGroupSize).sum();
        // the fill is random from 70% to 100% of seats, a group is not booked if it does not fit the target
        assertTrue(bookedPax >= MIN_BOOKED_PAX - 4 && bookedPax <= SEATS, "booked PAX " + bookedPax);
    }

    @Test
    public void test__group_which_does_not_fit_the_cabin_is_not_booked() {
        final Journeys.Journey tooBig = journey(250);
        final Journeys.Journey fits = journey(4);

        provideFlight();

        assertEquals(Journeys.Status.LookingForTickets, tooBig.getStatus());
        assertEquals(0, tooBig.getTransportFlight1Id());
        assertTrue(isBooked(fits));
    }

    @Test
    public void test__preferred_class_without_seats_is_upgraded_when_possible() {
        // there is no premium economy in J12/Y144, so W is upgraded to J
        final Journeys.Journey journey = journey(cityA, cityB, 2, CabinLayout.Service.W);

        final TransportFlights.Flight transportFlight = provideFlight().orElseThrow();

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
}
