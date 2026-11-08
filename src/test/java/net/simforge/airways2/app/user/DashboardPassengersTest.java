package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.FlightDashboardController.PassengerDto;
import net.simforge.airways2.app.user.FlightDashboardController.PassengersDto;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PassengerAvatars;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The row of the passengers on board: who is in it (the journeys which are in the aircraft), the avatars (from the id of the journey) and the order (from the id of the
 * transport flight).
 */
class DashboardPassengersTest {
    private static final List<String> POOL = List.of("a.png", "b.png", "c.png", "d.png", "e.png", "f.png");

    private World world;
    private Cities.City cityA;
    private Cities.City cityB;
    private FlightMissions.Mission mission;
    private TransportFlights.Flight tf;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final Countries.Country country = world.countries().create("TT", "Testland");
        cityA = world.cities().create(country.getId(), "Alpha City", 0, 0, 1_000_000);
        cityB = world.cities().create(country.getId(), "Bravo City", 0, 10, 1_000_000);
        final Airports.Airport a = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        final Airports.Airport b = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", a);
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, a, b, departure, departure + 2 * Time.ONE_HOUR);
        tf = world.transportFlightControl().createTransportFlight(mission);
    }

    private Journeys.Journey journey(final Journeys.Status status, final int groupSize) {
        final Journeys.Journey journey = world.journeys().create(status, cityA.getId(), cityB.getId(), groupSize, CabinLayout.Service.Y);
        journey.setTransportFlight1Id(tf.getId());
        return journey;
    }

    private PassengersDto row() {
        return FlightDashboardController.buildPassengers(world, mission, POOL);
    }

    @Test
    void theRowIsOnePassengerPerPlaceOfTheJourneysWhichAreInTheAircraft() {
        journey(Journeys.Status.OnBoard, 4);
        journey(Journeys.Status.WaitingForDeboarding, 2);
        // not in the aircraft: waiting for the boarding, too late, got off
        journey(Journeys.Status.WaitingForBoarding, 3);
        journey(Journeys.Status.WaitingForCheckIn, 5);
        journey(Journeys.Status.TooLateToBoard, 7);

        final PassengersDto row = row();

        assertEquals(6, row.getCount());
        assertEquals(6, row.getPassengers().size());
        assertTrue(row.getPassengers().stream().allMatch(p -> p.getKind().equals("pax")));
    }

    @Test
    void thePassengersAreTheAvatarsOfTheirJourneys() {
        final Journeys.Journey first = journey(Journeys.Status.OnBoard, 3);
        final Journeys.Journey second = journey(Journeys.Status.OnBoard, 2);

        final List<String> expected = new ArrayList<>();
        for (final Journeys.Journey j : List.of(first, second)) {
            PassengerAvatars.of(j.getId(), j.getGroupSize(), POOL).forEach(a -> expected.add(a.initials() + "/" + a.icon()));
        }
        final List<String> shown = row().getPassengers().stream().map(p -> p.getInitials() + "/" + p.getIcon()).toList();

        assertEquals(expected.stream().sorted().toList(), shown.stream().sorted().toList());
    }

    @Test
    void theOrderIsTheSameEveryTimeAndDoesNotDependOnTheOthersWhoAreAboard() {
        for (int i = 0; i < 6; i++) {
            journey(Journeys.Status.OnBoard, 3);
        }
        final List<PassengerDto> before = row().getPassengers();
        assertEquals(before, row().getPassengers());

        // a journey comes aboard: the ones who were there stay in the same order
        final Journeys.Journey late = journey(Journeys.Status.OnBoard, 2);
        final List<PassengerDto> after = row().getPassengers();
        final List<PassengerDto> withoutNew = new ArrayList<>(after);
        for (final PassengerAvatars.PassengerAvatar a : PassengerAvatars.of(late.getId(), 2, POOL)) {
            withoutNew.remove(new PassengerDto("pax", a.initials(), a.icon()));
        }
        assertEquals(before, withoutNew);
    }

    @Test
    void thePassengersOfAJourneyAreNotTogether() {
        final Map<Integer, Integer> sizes = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            final Journeys.Journey journey = journey(Journeys.Status.OnBoard, 4);
            sizes.put(journey.getId(), 4);
        }
        // the avatars of one journey are all different in the test pool often enough to see who is who
        final List<PassengerDto> row = row().getPassengers();
        assertEquals(120, row.size());

        // the places in the row of the passengers of the first journey: they are spread, not 4 in a row
        final Journeys.Journey first = world.journeys().allByTransportFlight1Id(tf.getId()).min(Comparator.comparingInt(Journeys.Journey::getId)).orElseThrow();
        final List<Integer> places = new ArrayList<>();
        for (final PassengerAvatars.PassengerAvatar a : PassengerAvatars.of(first.getId(), 4, POOL)) {
            places.add(row.indexOf(new PassengerDto("pax", a.initials(), a.icon())));
        }
        places.sort(Integer::compare);
        assertTrue(places.get(3) - places.get(0) > 8, "places " + places);
    }

    @Test
    void aFlightWithoutTheTransportFlightHasAnEmptyRow() {
        final FlightMissions.Mission ferry = world.flightMissions().createDispatchedMission(
                world.aircrafts().all().findFirst().orElseThrow(), world.airports().byIcao("AAAA").orElseThrow(), world.airports().byIcao("BBBB").orElseThrow(),
                world.getWorldTime() + 5 * Time.ONE_HOUR, world.getWorldTime() + 7 * Time.ONE_HOUR);

        final PassengersDto row = FlightDashboardController.buildPassengers(world, ferry, POOL);

        assertEquals(0, row.getCount());
        assertTrue(row.getPassengers().isEmpty());
    }

    @Test
    void anEmptyAircraftHasAnEmptyRow() {
        journey(Journeys.Status.WaitingForBoarding, 3);

        assertEquals(0, row().getCount());
    }
}
