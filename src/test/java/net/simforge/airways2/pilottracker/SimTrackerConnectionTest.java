package net.simforge.airways2.pilottracker;

import net.simforge.airways2.pilottracker.track.TrackPosition;
import net.simforge.commons.misc.Geo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The user is connected to the sim tracker while the reports come in: 3 minutes without a report drop the connection, 10 minutes if the user has a
 * started flight.
 */
class SimTrackerConnectionTest {
    private static final long NOW = 1_000_000_000L;

    private static SimTracker.Context contextWithReportAt(final long reportMillis, final boolean activeFlight) {
        return SimTracker.Context.forUser(1).toBuilder()
                .position(new TrackPosition(reportMillis, true, Geo.coords(51.28, -0.76), "EGLF", 0, 0))
                .activeFlight(activeFlight)
                .build();
    }

    @Test
    void noContextNoPositionNotConnected() {
        assertFalse(SimTracker.isConnected(null, NOW));
        assertFalse(SimTracker.isConnected(SimTracker.Context.forUser(1), NOW));
    }

    @Test
    void aFreshReportIsConnected() {
        assertTrue(SimTracker.isConnected(contextWithReportAt(NOW - 10_000, false), NOW));
        assertTrue(SimTracker.isConnected(contextWithReportAt(NOW - 10_000, true), NOW));
    }

    @Test
    void withoutAStartedFlightThreeMinutesWithoutReportsDropTheConnection() {
        assertTrue(SimTracker.isConnected(contextWithReportAt(NOW - SimTracker.CONNECTED_IDLE_MILLIS, false), NOW));
        assertFalse(SimTracker.isConnected(contextWithReportAt(NOW - SimTracker.CONNECTED_IDLE_MILLIS - 1, false), NOW));
    }

    @Test
    void withAStartedFlightTheConnectionLivesTenMinutes() {
        assertTrue(SimTracker.isConnected(contextWithReportAt(NOW - 9 * 60 * 1000, true), NOW));
        assertTrue(SimTracker.isConnected(contextWithReportAt(NOW - SimTracker.CONNECTED_ACTIVE_FLIGHT_MILLIS, true), NOW));
        assertFalse(SimTracker.isConnected(contextWithReportAt(NOW - SimTracker.CONNECTED_ACTIVE_FLIGHT_MILLIS - 1, true), NOW));
    }
}
