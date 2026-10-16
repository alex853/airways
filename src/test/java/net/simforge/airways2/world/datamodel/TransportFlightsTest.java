package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportFlightsTest {
    private World world;
    private TransportFlights transportFlights;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Aircrafts.Aircraft aircraft;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        transportFlights = world.transportFlights();

        airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        final AircraftTypes.AircraftType type = world.aircraftTypes().create("A320", "320");
        aircraft = world.aircrafts().create(type, "A-BCDE", airportA);
    }

    private FlightMissions.Mission mission() {
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        return world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, departure, departure + 2 * Time.ONE_HOUR);
    }

    private TransportFlights.Flight flightFor(final FlightMissions.Mission mission) {
        return transportFlights.create(mission, null, CabinLayout.JY(8, 100));
    }

    @Test
    public void test__flight_is_found_by_its_mission() {
        final FlightMissions.Mission first = mission();
        final FlightMissions.Mission second = mission();
        final TransportFlights.Flight firstFlight = flightFor(first);
        final TransportFlights.Flight secondFlight = flightFor(second);

        assertEquals(firstFlight.getId(), transportFlights.byFlightMissionId(first.getId()).orElseThrow().getId());
        assertEquals(secondFlight.getId(), transportFlights.byFlightMissionId(second.getId()).orElseThrow().getId());
    }

    @Test
    public void test__mission_without_a_flight_has_nothing() {
        final FlightMissions.Mission withFlight = mission();
        final FlightMissions.Mission withoutFlight = mission();
        flightFor(withFlight);

        assertTrue(transportFlights.byFlightMissionId(withoutFlight.getId()).isEmpty());
        assertTrue(transportFlights.byFlightMissionId(0).isEmpty());
        assertTrue(transportFlights.byFlightMissionId(123456).isEmpty());
    }

    @Test
    public void test__deleted_flight_is_not_found() {
        final FlightMissions.Mission mission = mission();
        final TransportFlights.Flight flight = flightFor(mission);

        transportFlights.deleteById(flight.getId());

        assertTrue(transportFlights.byFlightMissionId(mission.getId()).isEmpty());
        assertTrue(transportFlights.byId(flight.getId()).isEmpty());
    }

    @Test
    public void test__deleting_a_flight_does_not_touch_the_flights_of_other_missions() {
        final FlightMissions.Mission first = mission();
        final FlightMissions.Mission second = mission();
        final TransportFlights.Flight firstFlight = flightFor(first);
        final TransportFlights.Flight secondFlight = flightFor(second);

        transportFlights.deleteById(firstFlight.getId());

        assertTrue(transportFlights.byFlightMissionId(first.getId()).isEmpty());
        assertEquals(secondFlight.getId(), transportFlights.byFlightMissionId(second.getId()).orElseThrow().getId());
    }

    @Test
    public void test__new_flight_for_the_mission_after_the_deletion() {
        final FlightMissions.Mission mission = mission();
        transportFlights.deleteById(flightFor(mission).getId());

        final TransportFlights.Flight another = flightFor(mission);

        assertEquals(another.getId(), transportFlights.byFlightMissionId(mission.getId()).orElseThrow().getId());
    }

    @Test
    public void test__deleting_a_deleted_flight_fails_and_keeps_the_index() {
        final FlightMissions.Mission mission = mission();
        final TransportFlights.Flight flight = flightFor(mission);
        transportFlights.deleteById(flight.getId());
        final TransportFlights.Flight another = flightFor(mission);

        assertThrows(IllegalArgumentException.class, () -> transportFlights.deleteById(flight.getId()));

        assertEquals(another.getId(), transportFlights.byFlightMissionId(mission.getId()).orElseThrow().getId());
    }

    @Test
    public void test__first_flight_of_the_mission_is_found_when_there_are_several() {
        final FlightMissions.Mission mission = mission();
        final TransportFlights.Flight first = flightFor(mission);
        final TransportFlights.Flight second = flightFor(mission);

        assertTrue(first.getId() < second.getId());
        assertEquals(first.getId(), transportFlights.byFlightMissionId(mission.getId()).orElseThrow().getId());
    }

    @Test
    public void test__second_flight_becomes_the_first_when_the_first_one_is_deleted() {
        final FlightMissions.Mission mission = mission();
        final TransportFlights.Flight first = flightFor(mission);
        final TransportFlights.Flight second = flightFor(mission);

        transportFlights.deleteById(first.getId());

        assertEquals(second.getId(), transportFlights.byFlightMissionId(mission.getId()).orElseThrow().getId());
    }

    @Test
    public void test__deleting_the_second_flight_keeps_the_first_one() {
        final FlightMissions.Mission mission = mission();
        final TransportFlights.Flight first = flightFor(mission);
        final TransportFlights.Flight second = flightFor(mission);

        transportFlights.deleteById(second.getId());

        assertEquals(first.getId(), transportFlights.byFlightMissionId(mission.getId()).orElseThrow().getId());
    }

    @Test
    public void test__index_is_rebuilt_after_the_loading(@TempDir final Path dir) throws IOException {
        final FlightMissions.Mission first = mission();
        final FlightMissions.Mission second = mission();
        final FlightMissions.Mission third = mission();
        final TransportFlights.Flight firstFlight = flightFor(first);
        final TransportFlights.Flight secondFlight = flightFor(second);
        flightFor(third);
        transportFlights.deleteById(secondFlight.getId());
        transportFlights.save(dir);

        final TransportFlights loaded = new TransportFlights();
        loaded.loadIfExists(dir);

        assertEquals(firstFlight.getId(), loaded.byFlightMissionId(first.getId()).orElseThrow().getId());
        assertTrue(loaded.byFlightMissionId(second.getId()).isEmpty());
        assertTrue(loaded.byFlightMissionId(third.getId()).isPresent());
    }

    @Test
    public void test__loading_without_a_file_clears_the_index(@TempDir final Path dir) throws IOException {
        final FlightMissions.Mission mission = mission();
        flightFor(mission);

        transportFlights.loadIfExists(dir); // nothing is saved there

        assertTrue(transportFlights.byFlightMissionId(mission.getId()).isEmpty());
    }

    @Test
    public void test__first_flight_is_found_after_the_loading_when_there_are_several(@TempDir final Path dir) throws IOException {
        final FlightMissions.Mission mission = mission();
        final TransportFlights.Flight first = flightFor(mission);
        flightFor(mission);
        transportFlights.save(dir);

        final TransportFlights loaded = new TransportFlights();
        loaded.loadIfExists(dir);

        assertEquals(first.getId(), loaded.byFlightMissionId(mission.getId()).orElseThrow().getId());
    }

    @Test
    public void test__pax_carried_is_independent_of_neighbour_counters() {
        final TransportFlights.Flight flight = flightFor(mission());

        flight.setPaxCheckedIn(1023);
        flight.setPaxOnBoard(777);
        flight.setPaxCarried(555);

        assertEquals(1023, flight.getPaxCheckedIn());
        assertEquals(777, flight.getPaxOnBoard());
        assertEquals(555, flight.getPaxCarried());

        flight.setPaxCheckedIn(0);
        flight.setPaxOnBoard(0);
        assertEquals(555, flight.getPaxCarried());

        flight.setPaxCarried(1023);
        flight.setPaxCheckedIn(5);
        flight.setPaxOnBoard(7);
        assertEquals(1023, flight.getPaxCarried());
        assertEquals(5, flight.getPaxCheckedIn());
        assertEquals(7, flight.getPaxOnBoard());
    }

    @Test
    public void test__pax_carried_is_zero_by_default_and_refuses_wrong_values() {
        final TransportFlights.Flight flight = flightFor(mission());

        assertEquals(0, flight.getPaxCarried());
        assertThrows(IllegalArgumentException.class, () -> flight.setPaxCarried(-1));
        assertThrows(IllegalArgumentException.class, () -> flight.setPaxCarried(1024));
        assertEquals(0, flight.getPaxCarried());
    }

    @Test
    public void test__pax_carried_survives_the_save_and_load(@TempDir final Path dir) throws IOException {
        final TransportFlights.Flight flight = flightFor(mission());
        flight.setPaxOnBoard(120);
        flight.setPaxCarried(118);
        transportFlights.save(dir);

        final TransportFlights loaded = new TransportFlights();
        loaded.loadIfExists(dir);

        assertEquals(118, loaded.byId(flight.getId()).orElseThrow().getPaxCarried());
        assertEquals(120, loaded.byId(flight.getId()).orElseThrow().getPaxOnBoard());
    }
}
