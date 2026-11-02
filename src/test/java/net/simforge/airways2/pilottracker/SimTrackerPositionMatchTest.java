package net.simforge.airways2.pilottracker;

import net.simforge.airways2.pilottracker.track.TrackPosition;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.commons.misc.Geo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A report of the sim moves the aircraft of the flight only if it matches where the aircraft is.
 */
class SimTrackerPositionMatchTest {
    private static final Geo.Coords EGLL = Geo.coords(51.4706, -0.4619);
    private static final Geo.Coords EGLL_TAXIWAY = Geo.coords(51.4775, -0.4614);
    private static final Geo.Coords ANTARCTICA = Geo.coords(-77.85, 166.67);
    private static final Geo.Coords EGKK = Geo.coords(51.1481, -0.1903);

    @Test
    void beforeTakeoffTheSimMustShowTheAircraftPlace() {
        for (final FlightMissions.Status status : new FlightMissions.Status[]{
                FlightMissions.Status.Dispatched, FlightMissions.Status.Preflight, FlightMissions.Status.Departure, FlightMissions.Status.Postflight}) {
            assertTrue(SimTracker.positionMatchesAircraft(status, EGLL, 0, EGLL_TAXIWAY), status.name());
            assertFalse(SimTracker.positionMatchesAircraft(status, EGLL, 0, ANTARCTICA), status.name());
        }
    }

    @Test
    void aircraftWhichStoodForAWeekDoesNotAllowAFarPlace() {
        final int week = 7 * 24 * 3600;
        assertFalse(SimTracker.positionMatchesAircraft(FlightMissions.Status.Dispatched, EGLL, week, ANTARCTICA));
        assertFalse(SimTracker.positionMatchesAircraft(FlightMissions.Status.Dispatched, EGLL, week, EGKK));
    }

    @Test
    void inTheAirTheAircraftMayHaveFlownSinceTheLastReport() {
        // EGLL - EGKK is about 25 nm: not in 10 seconds, but in 5 minutes
        assertFalse(SimTracker.positionMatchesAircraft(FlightMissions.Status.Flying, EGLL, 10, EGKK));
        assertTrue(SimTracker.positionMatchesAircraft(FlightMissions.Status.Flying, EGLL, 300, EGKK));
        assertFalse(SimTracker.positionMatchesAircraft(FlightMissions.Status.Flying, EGLL, 300, ANTARCTICA));
    }

    @Test
    void aTeleportInFlightIsRefusedEvenAfterALongGap() {
        // 2 hours at 800 kt is 1600 nm, the Antarctica is 7000+ nm away
        assertFalse(SimTracker.positionMatchesAircraft(FlightMissions.Status.Flying, EGLL, 2 * 3600, ANTARCTICA));
    }

    @Test
    void aNegativeAgeIsTreatedAsNoTime() {
        assertTrue(SimTracker.positionMatchesAircraft(FlightMissions.Status.Flying, EGLL, -50, EGLL_TAXIWAY));
        assertFalse(SimTracker.positionMatchesAircraft(FlightMissions.Status.Flying, EGLL, -50, EGKK));
    }

    @Test
    void theStatusTellsThatTheLastReportWasRefused() {
        final SimTracker.Context believed = SimTracker.Context.forUser(1).toBuilder()
                .position(new TrackPosition(1L, true, EGLL, "EGLL", 0, 0)).build();
        assertFalse(SimTracker.UserStatus.from(believed).isPositionRejected());
        assertTrue(SimTracker.UserStatus.from(believed.toBuilder().positionRejected(true).build()).isPositionRejected());
    }
}
