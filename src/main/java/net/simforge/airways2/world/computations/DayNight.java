package net.simforge.airways2.world.computations;

/**
 * Day or night at a point of the Earth at a moment of time, without any external data: sunrise and sunset are calculated from the
 * latitude, the longitude and the date (NOAA solar equations, the accuracy is a minute or two).
 * <p>
 * The night is the time from {@link #MARGIN_SECONDS} after the sunset till {@link #MARGIN_SECONDS} before the next sunrise. In the polar
 * day (the sun does not set) there is no night, in the polar night (the sun does not rise) it is night all the time.
 */
public final class DayNight {
    public static final int MARGIN_SECONDS = 30 * 60;

    private static final int DAY_SECONDS = 24 * 60 * 60;
    // the sun is at the horizon when its centre is 0.833 degrees below it (the radius of the disc and the refraction)
    private static final double HORIZON_ZENITH_DEGREES = 90.833;

    private DayNight() {
    }

    /**
     * @param epochSeconds Unix time, UTC
     */
    public static boolean isNight(final double latitude, final double longitude, final long epochSeconds) {
        final long day = Math.floorDiv(epochSeconds, DAY_SECONDS);

        final SunDay today = sunDay(latitude, longitude, day);
        if (today.polarNight) {
            return true;
        }
        if (today.polarDay) {
            return false;
        }

        // the night begins after a sunset of the day k and ends before the sunrise of the day k + 1, for the days around the moment
        for (long k = day - 1; k <= day; k++) {
            final SunDay current = sunDay(latitude, longitude, k);
            if (current.polarDay || current.polarNight) {
                continue;
            }
            final SunDay next = sunDay(latitude, longitude, k + 1);

            final double nightStart = current.sunset + MARGIN_SECONDS;
            final double nightEnd;
            if (next.polarNight) {
                nightEnd = Double.POSITIVE_INFINITY; // the sun does not rise tomorrow
            } else if (next.polarDay) {
                nightEnd = Double.NEGATIVE_INFINITY; // the sun does not set tomorrow: no night at all
            } else {
                nightEnd = next.sunrise - MARGIN_SECONDS;
            }

            if (nightStart < epochSeconds && epochSeconds < nightEnd) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sunrise and sunset of the UTC day (Unix time of the moments, they can be in the previous or the next UTC day, it depends on the
     * longitude), or the polar day or the polar night.
     */
    static SunDay sunDay(final double latitude, final double longitude, final long day) {
        // the solar parameters are taken for noon UTC of the day
        final double julianDay = (day * DAY_SECONDS + DAY_SECONDS / 2.0) / DAY_SECONDS + 2440587.5;
        final double t = (julianDay - 2451545.0) / 36525.0;

        final double meanLongitude = normalize360(280.46646 + t * (36000.76983 + t * 0.0003032));
        final double meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t);
        final double eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);

        final double m = Math.toRadians(meanAnomaly);
        final double equationOfCenter = Math.sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t))
                + Math.sin(2 * m) * (0.019993 - 0.000101 * t)
                + Math.sin(3 * m) * 0.000289;
        final double trueLongitude = meanLongitude + equationOfCenter;
        final double omega = 125.04 - 1934.136 * t;
        final double apparentLongitude = trueLongitude - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega));

        final double meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0;
        final double obliquity = meanObliquity + 0.00256 * Math.cos(Math.toRadians(omega));

        final double declination = Math.asin(Math.sin(Math.toRadians(obliquity)) * Math.sin(Math.toRadians(apparentLongitude)));

        final double y = Math.pow(Math.tan(Math.toRadians(obliquity) / 2.0), 2);
        final double l0 = Math.toRadians(meanLongitude);
        final double equationOfTimeMinutes = 4.0 * Math.toDegrees(
                y * Math.sin(2 * l0)
                        - 2 * eccentricity * Math.sin(m)
                        + 4 * eccentricity * y * Math.sin(m) * Math.cos(2 * l0)
                        - 0.5 * y * y * Math.sin(4 * l0)
                        - 1.25 * eccentricity * eccentricity * Math.sin(2 * m));

        final double latitudeRad = Math.toRadians(latitude);
        final double cosHourAngle = Math.cos(Math.toRadians(HORIZON_ZENITH_DEGREES)) / (Math.cos(latitudeRad) * Math.cos(declination))
                - Math.tan(latitudeRad) * Math.tan(declination);
        if (cosHourAngle > 1) {
            return SunDay.POLAR_NIGHT;
        }
        if (cosHourAngle < -1) {
            return SunDay.POLAR_DAY;
        }
        final double hourAngleDegrees = Math.toDegrees(Math.acos(cosHourAngle));

        final double solarNoonMinutes = 720.0 - 4.0 * longitude - equationOfTimeMinutes;
        final double dayStart = (double) day * DAY_SECONDS;
        final double sunrise = dayStart + (solarNoonMinutes - 4.0 * hourAngleDegrees) * 60.0;
        final double sunset = dayStart + (solarNoonMinutes + 4.0 * hourAngleDegrees) * 60.0;
        return new SunDay(false, false, sunrise, sunset);
    }

    private static double normalize360(final double degrees) {
        final double result = degrees % 360.0;
        return result < 0 ? result + 360.0 : result;
    }

    record SunDay(boolean polarDay, boolean polarNight, double sunrise, double sunset) {
        static final SunDay POLAR_DAY = new SunDay(true, false, Double.NaN, Double.NaN);
        static final SunDay POLAR_NIGHT = new SunDay(false, true, Double.NaN, Double.NaN);
    }
}
