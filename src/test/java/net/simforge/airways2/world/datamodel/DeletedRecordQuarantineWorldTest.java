package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ids of deleted records of world objects are not reused at once, see docs/storage-quarantine.md.
 */
public class DeletedRecordQuarantineWorldTest {
    private World world;

    @BeforeEach
    public void setUp() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
    }

    private Journeys.Journey createJourney() {
        return world.journeys().create(Journeys.Status.LookingForTickets, 1, 2, 3, CabinLayout.Service.Y);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // journeys

    @Test
    public void journey_id_is_not_reused_after_deletion() {
        final Journeys.Journey first = createJourney();
        final int firstId = first.getId();
        world.journeys().deleteById(firstId);

        final Journeys.Journey second = createJourney();

        assertNotEquals(firstId, second.getId());
        assertTrue(world.journeys().byId(firstId).isEmpty());
        assertTrue(world.journeys().byId(second.getId()).isPresent());
    }

    @Test
    public void journey_ids_are_unique_in_many_create_delete_cycles() {
        final Set<Integer> issued = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            final int id = createJourney().getId();
            assertTrue(issued.add(id), "id " + id + " was issued twice");
            if (i % 2 == 0) {
                world.journeys().deleteById(id);
            }
        }

        assertEquals(200, issued.size());
    }

    @Test
    public void journey_created_after_deleting_the_last_one_has_a_new_id() {
        createJourney();
        final int last = createJourney().getId();
        world.journeys().deleteById(last);

        assertEquals(last + 1, createJourney().getId());
    }

    @Test
    public void journey_data_of_a_new_journey_is_clean_after_deleting_another() {
        final Journeys.Journey first = createJourney();
        first.setHeartbeatTime(123456);
        first.setTransportFlight1Id(77);
        world.journeys().deleteById(first.getId());

        final Journeys.Journey second = createJourney();

        assertEquals(0, second.getTransportFlight1Id());
        assertNotEquals(123456, second.getHeartbeatTime());
        assertEquals(Journeys.Status.LookingForTickets, second.getStatus());
    }

    @Test
    public void deleted_journey_is_not_found_by_any_filter() {
        final Journeys.Journey journey = createJourney();
        final int id = journey.getId();
        world.journeys().deleteById(id);

        assertTrue(world.journeys().filter(world.journeys().byStatus(Journeys.Status.LookingForTickets)).toList().isEmpty());
        assertTrue(world.journeys().byId(id).isEmpty());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // aircraft and flight missions

    @Test
    public void aircraft_id_is_not_reused_after_deletion() {
        final Airports.Airport airport = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        final AircraftTypes.AircraftType type = world.aircraftTypes().create("A320", "320");

        final Aircrafts.Aircraft first = world.aircrafts().create(type, "A-AAAA", airport);
        final int firstId = first.getId();
        world.aircrafts().deleteById(firstId);

        final Aircrafts.Aircraft second = world.aircrafts().create(type, "A-BBBB", airport);

        assertNotEquals(firstId, second.getId());
        assertTrue(world.aircrafts().byId(firstId).isEmpty());
    }

    @Test
    public void flight_mission_id_is_not_reused_after_deletion() {
        final Airports.Airport a = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        final Airports.Airport b = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        final AircraftTypes.AircraftType type = world.aircraftTypes().create("A320", "320");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(type, "A-AAAA", a);

        final int departure = Time.START_TIME_EPOCH_SECONDS + 2 * Time.ONE_HOUR;
        final int arrival = departure + 2 * Time.ONE_HOUR;

        final FlightMissions.Mission first = world.flightMissions().createDispatchedMission(aircraft, a, b, departure, arrival);
        final int firstId = first.getId();
        world.flightMissions().deleteById(firstId);

        final FlightMissions.Mission second = world.flightMissions().createDispatchedMission(aircraft, a, b, departure, arrival);

        assertNotEquals(firstId, second.getId());
        assertTrue(world.flightMissions().byId(firstId).isEmpty());
        assertTrue(world.flightMissions().byId(second.getId()).isPresent());
    }

    @Test
    public void flight_mission_ids_are_unique_in_many_cycles() {
        final Airports.Airport a = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        final Airports.Airport b = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        final AircraftTypes.AircraftType type = world.aircraftTypes().create("A320", "320");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(type, "A-AAAA", a);

        final int departure = Time.START_TIME_EPOCH_SECONDS + 2 * Time.ONE_HOUR;
        final int arrival = departure + 2 * Time.ONE_HOUR;

        final Set<Integer> issued = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            final int id = world.flightMissions().createDispatchedMission(aircraft, a, b, departure, arrival).getId();
            assertTrue(issued.add(id), "id " + id + " was issued twice");
            world.flightMissions().deleteById(id);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // events

    @Test
    public void event_id_is_not_reused_after_deletion() {
        final int time = Time.START_TIME_EPOCH_SECONDS;
        world.eventsToProcess().sendEvent(EventsToProcess.Type.PilotOnDuty, 1, time);
        final EventsToProcess.Event first = world.eventsToProcess()
                .findFirstActiveEvent(EventsToProcess.Type.PilotOnDuty, time).orElseThrow();
        final int firstId = first.getId();
        world.eventsToProcess().deleteById(firstId);

        world.eventsToProcess().sendEvent(EventsToProcess.Type.PilotOnDuty, 2, time);
        final EventsToProcess.Event second = world.eventsToProcess()
                .findFirstActiveEvent(EventsToProcess.Type.PilotOnDuty, time).orElseThrow();

        assertNotEquals(firstId, second.getId());
    }
}
