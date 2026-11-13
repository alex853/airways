package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.FlightDashboardController.PassengerRowDto;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The row of the passengers on board, a part of the status: who is in it (the journeys which are in the aircraft and the first passengers of the journey which is boarding, as
 * many as the tile counts), the order (the queue of the boarding), the avatars (from the id of the journey) and the compact form.
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
        tf.setStatus(TransportFlights.Status.Flying);
    }

    private Journeys.Journey journey(final Journeys.Status status, final int groupSize) {
        final Journeys.Journey journey = world.journeys().create(status, cityA.getId(), cityB.getId(), groupSize, CabinLayout.Service.Y);
        journey.setTransportFlight1Id(tf.getId());
        return journey;
    }

    private PassengerRowDto row() {
        return FlightDashboardController.buildPassengerRow(world, mission, POOL);
    }

    // the places of the passengers in the row as the journey and the number: 12345/2
    private static List<String> places(final PassengerRowDto row) {
        final List<String> result = new ArrayList<>();
        for (final long id : row.getIds()) {
            result.add((id / 4096) + "/" + (id % 4096));
        }
        return result;
    }

    @Test
    void theRowIsOnePassengerPerPlaceOfTheJourneysWhichAreInTheAircraft() {
        journey(Journeys.Status.OnBoard, 4);
        journey(Journeys.Status.WaitingForDeboarding, 2);
        // not in the aircraft: waiting for the boarding, too late
        journey(Journeys.Status.WaitingForBoarding, 3);
        journey(Journeys.Status.WaitingForCheckIn, 5);
        journey(Journeys.Status.TooLateToBoard, 7);

        final PassengerRowDto row = row();

        assertEquals(6, row.getCount());
        assertEquals(6, row.getIds().length);
        assertEquals(12, row.getInitials().length());
        assertEquals("xxxxxx", row.getKinds());
        assertEquals(6, row.getIcons().length);
    }

    @Test
    void thereIsNoRowBeforeTheBoardingAndAfterTheDeboarding() {
        journey(Journeys.Status.OnBoard, 3);
        for (final TransportFlights.Status status : List.of(TransportFlights.Status.Scheduled, TransportFlights.Status.CheckIn,
                TransportFlights.Status.WaitingForBoarding, TransportFlights.Status.Finished)) {
            tf.setStatus(status);
            assertNull(row(), status.name());
        }
        for (final TransportFlights.Status status : List.of(TransportFlights.Status.WaitingForDeparture, TransportFlights.Status.Departure, TransportFlights.Status.Flying,
                TransportFlights.Status.Arrival, TransportFlights.Status.WaitingForDeboarding, TransportFlights.Status.Deboarding)) {
            tf.setStatus(status);
            assertNotNull(row(), status.name());
        }
    }

    @Test
    void thePassengersAreTheAvatarsOfTheirJourneys() {
        final Journeys.Journey first = journey(Journeys.Status.OnBoard, 3);
        final Journeys.Journey second = journey(Journeys.Status.OnBoard, 2);

        final PassengerRowDto row = row();

        int position = 0;
        for (final Journeys.Journey j : List.of(first, second)) {
            final List<PassengerAvatars.PassengerAvatar> avatars = PassengerAvatars.of(j.getId(), j.getGroupSize(), POOL);
            for (final PassengerAvatars.PassengerAvatar avatar : avatars) {
                assertEquals(avatar.initials(), row.getInitials().substring(position * 2, position * 2 + 2));
                assertEquals(avatar.icon(), row.getIconTable().get(row.getIcons()[position]));
                position++;
            }
        }
        assertEquals(5, position);
    }

    @Test
    void theRowIsTheQueueOfTheBoardingAndAJourneyWhichComesLaterJoinsAtTheEnd() {
        final Journeys.Journey first = journey(Journeys.Status.OnBoard, 2);
        final Journeys.Journey second = journey(Journeys.Status.OnBoard, 3);
        tf.setStatus(TransportFlights.Status.WaitingForDeparture);
        final List<String> before = places(row());

        assertEquals(List.of(first.getId() + "/0", first.getId() + "/1", second.getId() + "/0", second.getId() + "/1", second.getId() + "/2"), before);

        final Journeys.Journey late = journey(Journeys.Status.OnBoard, 2);
        final List<String> after = places(row());
        assertEquals(before, after.subList(0, 5));
        assertEquals(List.of(late.getId() + "/0", late.getId() + "/1"), after.subList(5, 7));
    }

    @Test
    void theJourneyWhichIsBoardingAddsItsFirstPassengersUpToTheNumberOfTheTile() {
        journey(Journeys.Status.OnBoard, 3);
        final Journeys.Journey boarding = journey(Journeys.Status.WaitingForBoarding, 4);
        final Journeys.Journey waiting = journey(Journeys.Status.WaitingForBoarding, 2);
        tf.setStatus(TransportFlights.Status.Boarding);
        tf.setPaxOnBoard(5); // 3 on board and 2 of the journey which is boarding

        final PassengerRowDto row = row();

        assertEquals(5, row.getCount());
        final List<String> places = places(row);
        assertEquals(boarding.getId() + "/0", places.get(3));
        assertEquals(boarding.getId() + "/1", places.get(4));
        assertTrue(places.stream().noneMatch(p -> p.startsWith(waiting.getId() + "/")));

        // the counter goes on: the next passenger of the same journey, the earlier ones do not move
        tf.setPaxOnBoard(6);
        final List<String> next = places(row());
        assertEquals(places, next.subList(0, 5));
        assertEquals(boarding.getId() + "/2", next.get(5));
    }

    @Test
    void whenTheJourneyHasBoardedTheOnesWhoWereInTheRowStayWhereTheyAre() {
        journey(Journeys.Status.OnBoard, 3);
        final Journeys.Journey boarding = journey(Journeys.Status.WaitingForBoarding, 2);
        tf.setStatus(TransportFlights.Status.Boarding);
        tf.setPaxOnBoard(4);
        final List<String> whileBoarding = places(row());

        boarding.setStatus(Journeys.Status.OnBoard);

        // the whole journey is aboard now (5), the four who were there are in their places and the last one joins
        final List<String> boarded = places(row());
        assertEquals(5, boarded.size());
        assertEquals(whileBoarding, boarded.subList(0, 4));
    }

    @Test
    void theRowIsNotCompletedWhenTheTileHasNoMorePassengersThanTheJourneysOnBoard() {
        journey(Journeys.Status.OnBoard, 3);
        journey(Journeys.Status.WaitingForBoarding, 4);
        tf.setStatus(TransportFlights.Status.Boarding);

        tf.setPaxOnBoard(3);
        assertEquals(3, row().getCount());
        tf.setPaxOnBoard(0);
        assertEquals(3, row().getCount());
    }

    @Test
    void thePassengersWhoAreWaitingAreNotInTheRowOutsideTheBoarding() {
        journey(Journeys.Status.OnBoard, 2);
        journey(Journeys.Status.WaitingForBoarding, 4);
        tf.setStatus(TransportFlights.Status.Flying);
        tf.setPaxOnBoard(6);

        assertEquals(2, row().getCount());
    }

    @Test
    void theFirstOnesLeaveFirstAtTheDeboarding() {
        final Journeys.Journey first = journey(Journeys.Status.WaitingForDeboarding, 2);
        final Journeys.Journey second = journey(Journeys.Status.WaitingForDeboarding, 3);
        tf.setStatus(TransportFlights.Status.Deboarding);
        final List<String> before = places(row());
        assertEquals(5, before.size());

        first.setStatus(Journeys.Status.Finished);

        // the journey which got off is gone from the beginning of the row, the rest stay in their order
        assertEquals(before.subList(2, 5), places(row()));
        assertEquals(second.getId() + "/0", places(row()).get(0));
    }

    @Test
    void theIconsAreInATableAndEveryPassengerHasTheNumberOfItsIcon() {
        for (int i = 0; i < 30; i++) {
            journey(Journeys.Status.OnBoard, 4);
        }

        final PassengerRowDto row = row();

        assertEquals(120, row.getCount());
        assertEquals(new java.util.HashSet<>(row.getIconTable()).size(), row.getIconTable().size()); // every file once
        // the icons are of the pool, and of the fixed avatars of the special groups (the journeys of the first ids)
        final java.util.Set<String> fixedIcons = new java.util.HashSet<>();
        for (int journeyId = 1; journeyId <= 100; journeyId++) {
            final var group = net.simforge.airways2.world.computations.SpecialGroupAvatars.of(journeyId);
            if (group != null) {
                group.forEach(avatar -> fixedIcons.add(avatar.icon()));
            }
        }
        final java.util.Set<String> allowed = new java.util.HashSet<>(POOL);
        allowed.addAll(fixedIcons);
        assertTrue(allowed.containsAll(row.getIconTable()));
        for (final int number : row.getIcons()) {
            assertTrue(number >= 0 && number < row.getIconTable().size());
        }
        // the table is short, so the row is compact: not a file name for every passenger
        assertTrue(row.getIconTable().size() <= allowed.size());
    }

    @Test
    void aFlightWithoutTheTransportFlightHasNoRow() {
        final FlightMissions.Mission ferry = world.flightMissions().createDispatchedMission(
                world.aircrafts().all().findFirst().orElseThrow(), world.airports().byIcao("AAAA").orElseThrow(), world.airports().byIcao("BBBB").orElseThrow(),
                world.getWorldTime() + 5 * Time.ONE_HOUR, world.getWorldTime() + 7 * Time.ONE_HOUR);

        assertNull(FlightDashboardController.buildPassengerRow(world, ferry, POOL));
    }

    @Test
    void anEmptyAircraftHasNoRow() {
        journey(Journeys.Status.WaitingForBoarding, 3);
        tf.setStatus(TransportFlights.Status.Boarding);
        tf.setPaxOnBoard(0);

        assertNull(row());
    }
}
