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
    // load factor is random from 0.40 to 0.60, the max count of the journeys is seats * load factor / 5 PAX per journey
    private static final int MIN_BOOKED_JOURNEYS = (int) (SEATS * 0.40 / 5);
    private static final int MAX_BOOKED_JOURNEYS = (int) (SEATS * 0.60 / 5);

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

    private static boolean isBooked(final Journeys.Journey journey) {
        return journey.getStatus() == Journeys.Status.WaitingForBoarding && journey.getTransportFlight1Id() > 0;
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
    public void test__booked_journeys_are_checked_in_and_the_boarding_starts() {
        final List<Journeys.Journey> journeys = List.of(journey(3), journey(5), journey(2));

        final TransportFlights.Flight transportFlight = provideFlight().orElseThrow();

        assertEquals(TransportFlights.Status.Boarding, transportFlight.getStatus());
        journeys.forEach(j -> {
            assertEquals(Journeys.Status.WaitingForBoarding, j.getStatus());
            assertEquals(transportFlight.getId(), j.getTransportFlight1Id());
            assertEquals(0, j.getLocationCityId()); // is in the flight
            assertEquals(cityA.getId(), j.getLastKnownLocationCityId());
            assertEquals(CabinLayout.Service.Y, j.getBookedCabinService());
        });
        assertEquals(10, transportFlight.getPaxCheckedIn());
        assertEquals(SEATS - 10, transportFlight.getRemainedTickets().getTotal());
    }

    @Test
    public void test__load_factor_limits_the_number_of_booked_journeys() {
        final List<Journeys.Journey> journeys = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            journeys.add(journey(1));
        }

        provideFlight();

        final long booked = journeys.stream().filter(ShadowJetLogicTest::isBooked).count();
        assertTrue(booked >= MIN_BOOKED_JOURNEYS && booked <= MAX_BOOKED_JOURNEYS,
                "booked " + booked + ", expected from " + MIN_BOOKED_JOURNEYS + " to " + MAX_BOOKED_JOURNEYS);
    }

    @Test
    public void test__some_not_booked_journeys_are_awaken_within_five_minutes() {
        final List<Journeys.Journey> journeys = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            journeys.add(journey(1));
        }
        final int now = world.getWorldTime();

        provideFlight();

        final List<Journeys.Journey> awaken = journeys.stream()
                .filter(j -> j.getStatus() == Journeys.Status.LookingForTickets && j.getHeartbeatTime() > 0)
                .toList();
        assertTrue(awaken.size() >= 10 && awaken.size() <= 20, "awaken " + awaken.size());
        awaken.forEach(j -> assertTrue(j.getHeartbeatTime() >= now && j.getHeartbeatTime() <= now + 5 * Time.ONE_MINUTE));
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
