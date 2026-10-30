package net.simforge.airways2.world.computations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JourneyWakeCurveTest {
    @Test
    void theCurveStartsAtZeroAndNeverFalls() {
        assertEquals(0, JourneyWakeCurve.loadAfter(0), 1e-9);
        assertEquals(0, JourneyWakeCurve.loadAfter(-5), 1e-9);
        double previous = 0;
        for (int minutes = 0; minutes <= 3000; minutes++) {
            final double load = JourneyWakeCurve.loadAfter(minutes);
            assertTrue(load >= previous, "falls at " + minutes);
            previous = load;
        }
        assertEquals(1.0, JourneyWakeCurve.loadAfter(100_000), 1e-9);
    }

    @Test
    void theFirstMinutesAreFast() {
        // about a tenth in the first minute, then slower and slower
        assertEquals(0.08, JourneyWakeCurve.loadAfter(1), 1e-9);
        assertTrue(JourneyWakeCurve.loadAfter(5) - JourneyWakeCurve.loadAfter(1) < 0.08);
        assertTrue(JourneyWakeCurve.loadAfter(60) - JourneyWakeCurve.loadAfter(30) < JourneyWakeCurve.loadAfter(5) - JourneyWakeCurve.loadAfter(1) + 0.1);
    }

    @Test
    void minutesToReachIsTheInverseOfLoadAfter() {
        for (int minutes = 1; minutes < 2400; minutes += 7) {
            assertEquals(minutes, JourneyWakeCurve.minutesToReach(JourneyWakeCurve.loadAfter(minutes)), 1e-6, "minutes " + minutes);
        }
        assertEquals(0, JourneyWakeCurve.minutesToReach(0), 1e-9);
        assertEquals(2400, JourneyWakeCurve.minutesToReach(1.0), 1e-9);
        assertEquals(2400, JourneyWakeCurve.minutesToReach(1.3), 1e-9);
    }

    // The curve is calibrated to the figures of the page of the booking: the flight is booked a time before the departure, the boarding starts at the departure minus
    // 30 minutes, and what the curve gives for the time between the booking and that moment is inside the figures of the page (from the least to the most).
    @Test
    void theCurveIsInsideTheForecastOfThePage() {
        for (final int leadHours : new int[]{1, 6, 12, 24}) {
            final ShadowJetSalesForecast.ExpectedLoad forecast = ShadowJetSalesForecast.expectedLoad(leadHours * 3600L);
            final double curve = JourneyWakeCurve.loadAfter(leadHours * 60 - 30) * 100;
            assertTrue(curve >= forecast.loadFromPercent() && curve <= forecast.loadToPercent(),
                    "booked " + leadHours + " h before the departure: the curve gives " + curve + ", the page says " + forecast.loadFromPercent() + " to " + forecast.loadToPercent());
        }
    }

    // the speed of the booking only goes down: every next minute brings not more than the one before
    @Test
    void theSpeedOfTheBookingNeverGrows() {
        double previousSpeed = Double.MAX_VALUE;
        for (int minutes = 0; minutes < 3000; minutes++) {
            final double speed = JourneyWakeCurve.loadAfter(minutes + 1) - JourneyWakeCurve.loadAfter(minutes);
            assertTrue(speed <= previousSpeed + 1e-12, "the speed grows at the minute " + minutes);
            previousSpeed = speed;
        }
    }
}
