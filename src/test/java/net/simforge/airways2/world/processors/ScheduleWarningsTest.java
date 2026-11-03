package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the dashboard tells the pilot about his next flights: the conflict of a late flight, and the divert with passengers.
 */
class ScheduleWarningsTest {
    private static final int USER_ID = 7;

    private World world;
    private Airports.Airport departure;
    private Airports.Airport destination;
    private Airports.Airport divertAirport;
    private Aircrafts.Aircraft aircraft;
    private FlightMissions.Mission mission;
    private int departureTime;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        departure = world.airports().create(51.5, -0.1, "AAA", "AAAA", "Alpha");
        destination = world.airports().create(51.5, 4.0, "BBB", "BBBB", "Bravo");
        divertAirport = world.airports().create(51.5, 1.0, "CCC", "CCCC", "Charlie");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", departure);
        departureTime = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, departure, destination, departureTime, departureTime + Time.ONE_HOUR);
        mission.setUserId(USER_ID);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
    }

    // the next flight of the pilot, it departs the given time after the planned departure of the first one
    private FlightMissions.Mission nextFlight(final int minutesAfterDeparture) {
        final int departure = departureTime + minutesAfterDeparture * Time.ONE_MINUTE;
        final FlightMissions.Mission next = world.flightMissions().createDispatchedMission(aircraft, destination, this.departure, departure, departure + Time.ONE_HOUR);
        next.setUserId(USER_ID);
        next.setCharacterMode(FlightMissions.CharacterMode.PC);
        return next;
    }

    private Optional<ScheduleWarnings.Warning> warning() {
        return ScheduleWarnings.of(world, mission);
    }

    // the flight is in the air and diverted to the airport, with passengers on board or without
    private void flyingDivertedTo(final Airports.Airport airport, final int passengers) {
        final FlightMissionControl control = world.flightMissionControl();
        assertTrue(control.start(mission).started());
        control.setTrackingMode(mission, FlightMissions.TrackingMode.Manual);
        control.blocksOff(mission);
        control.takeoff(mission);
        mission.setDivertAirportId(airport.getId());
        final TransportFlights.Flight tf = world.transportFlightControl().createTransportFlight(mission, CabinLayout.Y(99));
        tf.setStatus(TransportFlights.Status.Flying);
        tf.setPaxOnBoard(passengers);
    }

    @Test
    void aLateFlightWhichMakesTheNextOneImpossibleIsAConflict() {
        // 90 minutes after the planned departure the flight has not left: it lands at 2:30 after the planned departure, the next flight starts at 2:45, 15 minutes are not enough
        world.setWorldTime(departureTime + 90 * Time.ONE_MINUTE);
        final FlightMissions.Mission next = nextFlight(165);

        final ScheduleWarnings.Warning warning = warning().orElseThrow();

        assertEquals(ScheduleWarnings.Kind.LATE, warning.kind());
        assertTrue(warning.conflict());
        assertEquals(next.getId(), warning.nextFlight().getId());
        assertEquals(15, warning.shortfallMinutes());
        assertEquals(departureTime + 150 * Time.ONE_MINUTE, warning.earliestBlocksOn());
    }

    @Test
    void aLateFlightWhichLeavesTheGapOfThirtyMinutesIsNotAConflict() {
        world.setWorldTime(departureTime + 90 * Time.ONE_MINUTE);
        nextFlight(180);

        assertTrue(warning().isEmpty());
    }

    @Test
    void aLateFlightWithTheNextOneFarAwayOrWithoutItHasNoWarning() {
        world.setWorldTime(departureTime + 90 * Time.ONE_MINUTE);
        assertTrue(warning().isEmpty());

        nextFlight(25 * 60);
        assertTrue(warning().isEmpty());
    }

    @Test
    void aFlightWhichIsOverIsNotTheNextOne() {
        world.setWorldTime(departureTime + 90 * Time.ONE_MINUTE);
        nextFlight(165).setStatus(FlightMissions.Status.Cancelled);

        assertTrue(warning().isEmpty());
    }

    @Test
    void aDivertWithPassengersIsAReminderWhenThereIsNoNextFlight() {
        flyingDivertedTo(divertAirport, 50);

        final ScheduleWarnings.Warning warning = warning().orElseThrow();

        assertEquals(ScheduleWarnings.Kind.DIVERT, warning.kind());
        assertFalse(warning.conflict());
        assertEquals(destination.getId(), warning.destination().getId());
        assertNull(warning.nextFlight());
        assertEquals(0, warning.shortfallMinutes());
    }

    @Test
    void aDivertWithPassengersAndANextFlightFarAwayIsNotAConflict() {
        flyingDivertedTo(divertAirport, 50);
        nextFlight(20 * 60);

        final ScheduleWarnings.Warning warning = warning().orElseThrow();

        assertEquals(ScheduleWarnings.Kind.DIVERT, warning.kind());
        assertFalse(warning.conflict());
        assertTrue(warning.nextFlight() != null);
    }

    @Test
    void aDivertWithPassengersIsAConflictForTheNextFlightWhichStartsBeforeTheEarliestArrival() {
        flyingDivertedTo(divertAirport, 50);
        // the next flight starts 90 minutes after the planned departure, that is 30 minutes after the takeoff: an hour on the ground and an hour of the turnaround are not possible
        final FlightMissions.Mission next = nextFlight(90);

        final ScheduleWarnings.Warning warning = warning().orElseThrow();

        assertTrue(warning.conflict());
        assertEquals(next.getId(), warning.nextFlight().getId());
        // the earliest blocks on is more than an hour from now (the hour on the ground), and the next flight must start an hour after it
        assertTrue(warning.earliestBlocksOn() > world.getWorldTime() + DIVERT_GROUND_TIME());
        assertEquals((warning.earliestBlocksOn() + ScheduleWarnings.DIVERT_TURNAROUND_TIME - next.getPlannedDepartureWorldTime() + 59) / 60, warning.shortfallMinutes());
    }

    @Test
    void aDivertOfATypeWithoutPerformanceDataIsTheReminderOnly() {
        aircraft = world.aircrafts().create(world.aircraftTypes().create("ZZZ9", "ZZ9"), "Z-AAAA", departure);
        mission = world.flightMissions().createDispatchedMission(aircraft, departure, destination, departureTime, departureTime + Time.ONE_HOUR);
        mission.setUserId(USER_ID);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        flyingDivertedTo(divertAirport, 50);
        nextFlight(90);

        final ScheduleWarnings.Warning warning = warning().orElseThrow();

        assertEquals(ScheduleWarnings.Kind.DIVERT, warning.kind());
        assertFalse(warning.conflict());
    }

    @Test
    void aDivertWithoutPassengersHasNoWarning() {
        flyingDivertedTo(divertAirport, 0);
        nextFlight(90);

        assertTrue(warning().isEmpty());
    }

    @Test
    void aDivertedFlightIsNotAlsoALateFlight() {
        flyingDivertedTo(divertAirport, 0);
        world.setWorldTime(departureTime + 5 * Time.ONE_HOUR);
        nextFlight(6 * 60);

        assertTrue(warning().isEmpty());
    }

    private static int DIVERT_GROUND_TIME() {
        return ScheduleWarnings.DIVERT_GROUND_TIME;
    }
}
