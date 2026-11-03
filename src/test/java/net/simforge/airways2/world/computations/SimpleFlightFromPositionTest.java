package net.simforge.airways2.world.computations;

import net.simforge.commons.misc.Geo;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flight from a point in the air to a target (SimpleFlight.fromPosition): the speed grows with the altitude, the climb is what is left to the altitude of the flight.
 */
class SimpleFlightFromPositionTest {
    // an airliner: cruise at 35000 feet and 450 knots, climb 2000 and descent 1500 feet a minute, takeoff 140 and landing 130 knots
    private static final AircraftPerformanceData AIRLINER = new AircraftPerformanceData() {
        @Override
        public Integer getTypicalCruiseAltitude() {
            return 35000;
        }

        @Override
        public Integer getTypicalCruiseSpeed() {
            return 450;
        }

        @Override
        public Integer getClimbVerticalSpeed() {
            return 2000;
        }

        @Override
        public Integer getDescentVerticalSpeed() {
            return 1500;
        }

        @Override
        public Integer getTakeoffSpeed() {
            return 140;
        }

        @Override
        public Integer getLandingSpeed() {
            return 130;
        }
    };

    private static final Geo.Coords FROM = Geo.coords(0, 0);

    // on the equator one degree of longitude is about 60 nautical miles
    private static Geo.Coords eastBy(final double nauticalMiles) {
        return Geo.coords(0, nauticalMiles / 60.0);
    }

    private static SimpleFlight from(final double altitude, final double distanceNm) {
        return SimpleFlight.fromPosition(FROM, altitude, eastBy(distanceNm), AIRLINER);
    }

    @Test
    void anAircraftInTheCruiseHasNoClimbAndTheDescentOnly() {
        final SimpleFlight flight = from(35000, 600);

        assertEquals(Duration.ZERO, flight.getClimbTime());
        assertEquals(35000, flight.getCruiseAltitude());
        assertEquals(450, flight.getCruiseSpeed(), 1);
        // the descent from 35000 feet at 1500 feet a minute is 23 minutes
        assertEquals(23, flight.getDescentTime().toMinutes(), 1);
        // the whole way is the cruise and the descent, so it is not more than at the cruise speed all the way and not less than at the speed of the descent
        final double hoursAtCruiseSpeed = 600 / 450.0;
        assertTrue(flight.getTotalTime().toMinutes() <= hoursAtCruiseSpeed * 60 + 10);
        assertTrue(flight.getTotalTime().toMinutes() > 600 / 450.0 * 60 - 15);
    }

    @Test
    void theHigherTheAircraftIsTheSoonerItGetsThere() {
        final Duration onTheGround = from(0, 600).getTotalTime();
        final Duration at10000 = from(10000, 600).getTotalTime();
        final Duration at35000 = from(35000, 600).getTotalTime();

        assertTrue(at35000.compareTo(at10000) < 0);
        assertTrue(at10000.compareTo(onTheGround) < 0);
    }

    @Test
    void anAircraftOnTheGroundIsAboutTheFlightFromTheAirport() {
        final double fromTheAirport = SimpleFlight.forRoute(FROM, eastBy(600), AIRLINER).getTotalTime().toMinutes();
        final double fromTheGround = from(0, 600).getTotalTime().toMinutes();

        assertEquals(fromTheAirport, fromTheGround, fromTheAirport * 0.15);
    }

    @Test
    void anAircraftBelowTheAltitudeOfTheFlightClimbsToItFirst() {
        final SimpleFlight flight = from(10000, 600);

        // 25000 feet to climb at 2000 feet a minute
        assertEquals(12, flight.getClimbTime().toMinutes(), 1);
        assertTrue(flight.getCruiseAltitude() > 10000);
    }

    @Test
    void aTargetWhichIsTooCloseForTheClimbGetsNoClimb() {
        final SimpleFlight flight = from(5000, 15);

        assertEquals(Duration.ZERO, flight.getClimbTime());
        assertEquals(5000, flight.getCruiseAltitude());
    }

    @Test
    void aTargetCloserThanTheDescentNeedsIsOneDescentAtTheAverageSpeed() {
        // 20 nautical miles from the cruise altitude: the descent of 23 minutes does not fit, the aircraft goes straight down, steeper
        final SimpleFlight flight = from(35000, 20);

        assertEquals(Duration.ZERO, flight.getCruiseTime());
        final double averageKnots = (450 + 130) / 2.0;
        assertEquals(20 / averageKnots * 60, flight.getTotalTime().toSeconds() / 60.0, 0.5);
    }

    @Test
    void anAltitudeAboveTheTypicalOneIsTheTypicalOne() {
        assertEquals(from(35000, 600).getTotalTime(), from(41000, 600).getTotalTime());
    }
}
