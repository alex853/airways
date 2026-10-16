package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.EventLog;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransportFlightDeboardingWindowTest {
    private World world;
    private TransportFlightControl tfControl;
    private TransportFlights.Flight transportFlight;

    @BeforeEach
    public void beforeEach() {
        final int startTime = Time.START_TIME_EPOCH_SECONDS;
        world = World.create(new InMemoryStorageStrategy(), startTime);

        final Airports.Airport airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        final Airports.Airport airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().create("A320", "320");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-BCDE", airportA);
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(
                aircraft, airportA, airportB, startTime + 2 * Time.ONE_HOUR, startTime + 4 * Time.ONE_HOUR);

        tfControl = world.transportFlightControl();
        transportFlight = tfControl.createTransportFlight(mission);
    }

    private void startDeboarding() {
        transportFlight.setStatus(TransportFlights.Status.WaitingForDeboarding);
        tfControl.startDeboarding(transportFlight);
    }

    private long deboardingStartedEvents() {
        return world.eventLog()
                .byObject(EventLog.ObjectType.TransportFlight.code(), transportFlight.getId())
                .filter(event -> event.getType() == EventLog.EventType.TransportFlightDeboardingStarted)
                .count();
    }

    @Test
    public void test__start_deboarding_writes_the_event() {
        startDeboarding();

        assertEquals(TransportFlights.Status.Deboarding, transportFlight.getStatus());
        assertEquals(1, deboardingStartedEvents());
    }

    @Test
    public void test__window_is_not_expired_right_after_the_start() {
        startDeboarding();

        assertFalse(tfControl.deboardingWindowExpired(transportFlight));
    }

    @Test
    public void test__window_is_not_expired_just_before_an_hour() {
        startDeboarding();

        world.setWorldTime(world.getWorldTime() + Time.ONE_HOUR - 1);

        assertFalse(tfControl.deboardingWindowExpired(transportFlight));
    }

    @Test
    public void test__window_is_expired_after_an_hour() {
        startDeboarding();

        world.setWorldTime(world.getWorldTime() + Time.ONE_HOUR);

        assertTrue(tfControl.deboardingWindowExpired(transportFlight));
    }

    @Test
    public void test__flight_without_the_start_event_is_expired() {
        // the deboarding was started before the event had been introduced
        transportFlight.setStatus(TransportFlights.Status.Deboarding);

        assertEquals(0, deboardingStartedEvents());
        assertTrue(tfControl.deboardingWindowExpired(transportFlight));
    }

    @Test
    public void test__start_time_is_found_in_the_event_log_when_the_cache_is_empty() {
        startDeboarding();
        // a new control instance has an empty cache, as after a restart of the application
        final TransportFlightControl restartedControl = new TransportFlightControl(world);

        assertFalse(restartedControl.deboardingWindowExpired(transportFlight));
        world.setWorldTime(world.getWorldTime() + Time.ONE_HOUR);
        assertTrue(restartedControl.deboardingWindowExpired(transportFlight));
    }

    @Test
    public void test__finish_with_pax_on_board_finishes_the_flight_and_zeroes_pax_on_board() {
        startDeboarding();
        transportFlight.setPaxOnBoard(7);

        tfControl.finish(transportFlight);

        assertEquals(TransportFlights.Status.Finished, transportFlight.getStatus());
        assertEquals(0, transportFlight.getPaxOnBoard());
    }

    @Test
    public void test__flight_with_pax_is_finished_by_the_processor_when_the_window_is_over() {
        startDeboarding();
        transportFlight.setPaxOnBoard(7);
        transportFlight.setHeartbeatTime(world.getWorldTime());

        TransportFlightProcessor.process(world);
        assertEquals(TransportFlights.Status.Deboarding, transportFlight.getStatus());

        world.setWorldTime(world.getWorldTime() + Time.ONE_HOUR);
        transportFlight.setHeartbeatTime(world.getWorldTime());
        TransportFlightProcessor.process(world);

        assertEquals(TransportFlights.Status.Finished, transportFlight.getStatus());
    }
}
