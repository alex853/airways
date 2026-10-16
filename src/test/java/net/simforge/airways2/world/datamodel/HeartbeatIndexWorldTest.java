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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The heartbeat index of the storages which are processed by heartbeat: journeys, transport flights and flight missions.
 */
class HeartbeatIndexWorldTest {
    private World world;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Aircrafts.Aircraft aircraft;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
    }

    private Journeys.Journey journey(final int heartbeatTime) {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, 1, 2, 3, CabinLayout.Service.Y);
        journey.setHeartbeatTime(heartbeatTime);
        return journey;
    }

    private FlightMissions.Mission mission() {
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        return world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, departure, departure + 2 * Time.ONE_HOUR);
    }

    private TransportFlights.Flight flight(final int heartbeatTime) {
        final TransportFlights.Flight flight = world.transportFlights().create(mission(), null, CabinLayout.JY(8, 100));
        flight.setHeartbeatTime(heartbeatTime);
        return flight;
    }

    // ---- journeys

    @Test
    public void test__journeys_new_journey_has_no_heartbeat() {
        world.journeys().create(Journeys.Status.LookingForTickets, 1, 2, 3, CabinLayout.Service.Y);

        assertTrue(world.journeys().nextForHeartbeat(Integer.MAX_VALUE).isEmpty());
        assertEquals(List.of(), world.journeys().checkHeartbeatIndex());
    }

    @Test
    public void test__journeys_the_earliest_due_journey_goes_first() {
        final Journeys.Journey late = journey(300);
        final Journeys.Journey early = journey(100);
        final Journeys.Journey future = journey(9000);

        assertEquals(early.getId(), world.journeys().nextForHeartbeat(500).orElseThrow().getId());
        early.setHeartbeatTime(0);
        assertEquals(late.getId(), world.journeys().nextForHeartbeat(500).orElseThrow().getId());
        late.setHeartbeatTime(0);
        assertTrue(world.journeys().nextForHeartbeat(500).isEmpty());
        assertEquals(future.getId(), world.journeys().nextForHeartbeat(9000).orElseThrow().getId());
        assertEquals(List.of(), world.journeys().checkHeartbeatIndex());
    }

    @Test
    public void test__journeys_deleted_journey_is_not_returned() {
        final Journeys.Journey first = journey(100);
        final Journeys.Journey second = journey(200);

        world.journeys().deleteById(first.getId());

        assertEquals(second.getId(), world.journeys().nextForHeartbeat(500).orElseThrow().getId());
        assertEquals(List.of(), world.journeys().checkHeartbeatIndex());
    }

    @Test
    public void test__journeys_status_changes_do_not_break_the_index() {
        final Journeys.Journey journey = journey(100);

        journey.setStatus(Journeys.Status.WaitingForBoarding);
        journey.setLocationCityId(5);
        journey.setLastKnownLocationCityId(5);
        journey.setTransportFlight1Id(77);

        assertEquals(journey.getId(), world.journeys().nextForHeartbeat(100).orElseThrow().getId());
        assertEquals(List.of(), world.journeys().checkHeartbeatIndex());
    }

    @Test
    public void test__journeys_index_survives_the_save_and_load(@TempDir final Path dir) throws IOException {
        final Journeys.Journey due = journey(100);
        journey(0);
        final Journeys.Journey deleted = journey(50);
        world.journeys().deleteById(deleted.getId());
        world.journeys().save(dir);

        final Journeys loaded = new Journeys();
        loaded.loadIfExists(dir);

        assertEquals(due.getId(), loaded.nextForHeartbeat(100).orElseThrow().getId());
        assertTrue(loaded.nextForHeartbeat(99).isEmpty());
        assertEquals(List.of(), loaded.checkHeartbeatIndex());
    }

    // ---- transport flights

    @Test
    public void test__flights_the_earliest_due_flight_goes_first() {
        final TransportFlights.Flight late = flight(300);
        final TransportFlights.Flight early = flight(100);
        flight(0);

        assertEquals(early.getId(), world.transportFlights().nextForHeartbeat(500).orElseThrow().getId());
        early.setHeartbeatTime(1000);
        assertEquals(late.getId(), world.transportFlights().nextForHeartbeat(500).orElseThrow().getId());
        assertTrue(world.transportFlights().nextForHeartbeat(299).isEmpty());
        assertEquals(List.of(), world.transportFlights().checkHeartbeatIndex());
    }

    @Test
    public void test__flights_created_flight_has_no_heartbeat_until_it_is_set() {
        final TransportFlights.Flight flight = world.transportFlights().create(mission(), null, CabinLayout.JY(8, 100));

        assertTrue(world.transportFlights().nextForHeartbeat(Integer.MAX_VALUE).isEmpty());
        flight.setHeartbeatTime(777);
        assertEquals(Optional.of(flight.getId()), world.transportFlights().nextForHeartbeat(777).map(TransportFlights.Flight::getId));
    }

    @Test
    public void test__flights_deleted_flight_leaves_the_index() {
        final TransportFlights.Flight deleted = flight(100);
        final TransportFlights.Flight kept = flight(200);

        world.transportFlights().deleteById(deleted.getId());

        assertEquals(kept.getId(), world.transportFlights().nextForHeartbeat(500).orElseThrow().getId());
        assertEquals(List.of(), world.transportFlights().checkHeartbeatIndex());
    }

    @Test
    public void test__flights_index_survives_the_save_and_load(@TempDir final Path dir) throws IOException {
        final TransportFlights.Flight due = flight(100);
        flight(900);
        world.transportFlights().save(dir);

        final TransportFlights loaded = new TransportFlights();
        loaded.loadIfExists(dir);

        assertEquals(due.getId(), loaded.nextForHeartbeat(100).orElseThrow().getId());
        assertEquals(List.of(), loaded.checkHeartbeatIndex());
    }

    // ---- missions

    @Test
    public void test__missions_loading_an_empty_storage_does_not_fail(@TempDir final Path dir) throws IOException {
        final FlightMissions missions = new FlightMissions();

        missions.loadIfExists(dir); // nothing is saved there, there are no missions at all

        assertTrue(missions.nextForHeartbeat(Integer.MAX_VALUE).isEmpty());
        assertEquals(List.of(), missions.checkHeartbeatIndex());
    }

    @Test
    public void test__missions_loading_missions_without_heartbeat_does_not_fail(@TempDir final Path dir) throws IOException {
        mission(); // heartbeat time is zero
        world.flightMissions().save(dir);

        final FlightMissions missions = new FlightMissions();
        missions.loadIfExists(dir);

        assertTrue(missions.nextForHeartbeat(Integer.MAX_VALUE).isEmpty());
    }

    @Test
    public void test__missions_the_earliest_due_mission_goes_first() {
        final FlightMissions.Mission late = mission();
        final FlightMissions.Mission early = mission();
        final FlightMissions.Mission future = mission();
        late.setHeartbeatTime(300);
        early.setHeartbeatTime(100);
        future.setHeartbeatTime(9000);

        assertEquals(early.getId(), world.flightMissions().nextForHeartbeat(500).orElseThrow().getId());
        early.setHeartbeatTime(0);
        assertEquals(late.getId(), world.flightMissions().nextForHeartbeat(500).orElseThrow().getId());
        late.setHeartbeatTime(0);
        assertTrue(world.flightMissions().nextForHeartbeat(500).isEmpty());
        assertEquals(future.getId(), world.flightMissions().nextForHeartbeat(9000).orElseThrow().getId());
        assertEquals(List.of(), world.flightMissions().checkHeartbeatIndex());
    }

    @Test
    public void test__missions_deleted_mission_leaves_the_index() {
        final FlightMissions.Mission deleted = mission();
        final FlightMissions.Mission kept = mission();
        deleted.setHeartbeatTime(100);
        kept.setHeartbeatTime(200);

        world.flightMissions().deleteById(deleted.getId());

        assertEquals(kept.getId(), world.flightMissions().nextForHeartbeat(500).orElseThrow().getId());
        assertEquals(List.of(), world.flightMissions().checkHeartbeatIndex());
    }

    @Test
    public void test__missions_status_changes_do_not_break_the_index() {
        final FlightMissions.Mission mission = mission();
        mission.setHeartbeatTime(100);

        mission.setPlannedDepartureWorldTime(world.getWorldTime() + 2 * Time.ONE_HOUR);

        assertEquals(mission.getId(), world.flightMissions().nextForHeartbeat(100).orElseThrow().getId());
        assertEquals(List.of(), world.flightMissions().checkHeartbeatIndex());
    }

    @Test
    public void test__missions_index_survives_the_save_and_load(@TempDir final Path dir) throws IOException {
        final FlightMissions.Mission due = mission();
        final FlightMissions.Mission future = mission();
        mission();
        due.setHeartbeatTime(100);
        future.setHeartbeatTime(900);
        world.flightMissions().save(dir);

        final FlightMissions loaded = new FlightMissions();
        loaded.loadIfExists(dir);

        assertEquals(due.getId(), loaded.nextForHeartbeat(100).orElseThrow().getId());
        assertTrue(loaded.nextForHeartbeat(99).isEmpty());
        assertEquals(List.of(), loaded.checkHeartbeatIndex());
    }
}
