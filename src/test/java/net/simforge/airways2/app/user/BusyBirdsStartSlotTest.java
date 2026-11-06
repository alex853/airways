package net.simforge.airways2.app.user;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The start of a BusyBirds mission is booked into the roster of the user: not too soon, not later than 7 days from now and not closer than 30 minutes to another flight of the
 * pilot, the whole block of the legs counts as one interval.
 */
class BusyBirdsStartSlotTest {
    private static final int USER_ID = 7;
    private static final long MINUTE = 60 * 1000L;
    private static final long HOUR = 60 * MINUTE;

    private World world;
    private int now;
    private FlightMissions.Mission flight;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        now = world.getWorldTime();

        final Airports.Airport from = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        final Airports.Airport to = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");
        final AircraftTypes.AircraftType type = world.aircraftTypes().create("C25C", "CNJ");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(type, "BB-CJA", from);

        // the flight of the pilot in a day: from 12 hours to 15 hours from now
        flight = world.flightMissions().createDispatchedMission(aircraft, from, to, now + 12 * 3600, now + 15 * 3600);
        flight.setUserId(USER_ID);
    }

    private long ms(final long hoursFromNow, final long minutes) {
        return now * 1000L + hoursFromNow * HOUR + minutes * MINUTE;
    }

    private BusyBirdsController.StartSlotDto check(final long startMillis, final long endMillis) {
        return BusyBirdsController.checkStartSlot(world, USER_ID, startMillis, endMillis, now);
    }

    @Test
    void aMissionApartFromTheFlightsIsFree() {
        final BusyBirdsController.StartSlotDto slot = check(ms(2, 0), ms(8, 0));

        assertTrue(slot.isFree());
        assertFalse(slot.isTooSoon());
        assertFalse(slot.isTooLate());
        assertNull(slot.getConflict());
    }

    @Test
    void theWholeBlockCountsNotOnlyItsStart() {
        // it starts before the flight but its last leg lands in the middle of the flight
        final BusyBirdsController.StartSlotDto slot = check(ms(6, 0), ms(13, 0));

        assertFalse(slot.isFree());
        assertNotNull(slot.getConflict());
    }

    @Test
    void thirtyMinutesBeforeAndAfterTheFlightAreKept() {
        // lands 29 minutes before the departure: not enough
        assertFalse(check(ms(8, 0), ms(11, 31)).isFree());
        // lands 30 minutes before the departure: enough
        assertTrue(check(ms(8, 0), ms(11, 30)).isFree());
        // starts 29 minutes after the arrival: not enough
        assertFalse(check(ms(15, 29), ms(20, 0)).isFree());
        // starts 30 minutes after the arrival: enough
        assertTrue(check(ms(15, 30), ms(20, 0)).isFree());
    }

    @Test
    void aFinishedFlightIsNotInTheWay() {
        flight.setStatus(FlightMissions.Status.Finished);

        assertTrue(check(ms(12, 0), ms(14, 0)).isFree());
    }

    @Test
    void theFlightsOfAnotherUserAreNotInTheWay() {
        final BusyBirdsController.StartSlotDto slot = BusyBirdsController.checkStartSlot(world, USER_ID + 1, ms(12, 0), ms(14, 0), now);

        assertTrue(slot.isFree());
    }

    @Test
    void tooSoonAndTooLate() {
        // 30 minutes from now is the earliest, the clocks may differ by 5 minutes
        assertFalse(check(ms(0, 10), ms(3, 0)).isFree());
        assertTrue(check(ms(0, 10), ms(3, 0)).isTooSoon());
        assertTrue(check(ms(0, 30), ms(3, 0)).isFree());
        assertTrue(check(ms(0, 26), ms(3, 0)).isFree());

        // 7 days from now is the latest start
        assertTrue(check(ms(7 * 24, 0), ms(7 * 24 + 5, 0)).isFree());
        final BusyBirdsController.StartSlotDto late = check(ms(7 * 24 + 1, 0), ms(7 * 24 + 6, 0));
        assertFalse(late.isFree());
        assertTrue(late.isTooLate());
    }

    @Test
    void theProblemsAreToldInWords() {
        final BusyBirdsController.StartSlotDto slot = check(ms(12, 0), ms(14, 0));

        final String text = String.join(" ", BusyBirdsController.startSlotProblems(slot));
        assertTrue(text.contains("conflicts with your flight EGLF - EDDB"), text);
        assertEquals(1, BusyBirdsController.startSlotProblems(slot).size());
    }
}
