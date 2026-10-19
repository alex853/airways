package net.simforge.airways2.world.computations;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Night is from 30 minutes after the sunset till 30 minutes before the sunrise. The known sunrise and sunset times of some places are
 * taken with a margin of ten minutes.
 */
class DayNightTest {
    private static long utc(final int year, final int month, final int day, final int hour, final int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).toEpochSecond(ZoneOffset.UTC);
    }

    // London: lat 51.5074, lon -0.1278
    private static boolean london(final long time) {
        return DayNight.isNight(51.5074, -0.1278, time);
    }

    @Test
    public void test__london_midsummer__sunrise_03_43_sunset_20_21_utc() {
        // night begins 30 minutes after the sunset, 20:51
        assertFalse(london(utc(2025, 6, 21, 12, 0)));
        assertFalse(london(utc(2025, 6, 21, 20, 40)));
        assertTrue(london(utc(2025, 6, 21, 21, 5)));
        assertTrue(london(utc(2025, 6, 21, 23, 30)));
        // night ends 30 minutes before the sunrise, 03:13
        assertTrue(london(utc(2025, 6, 21, 3, 0)));
        assertFalse(london(utc(2025, 6, 21, 3, 25)));
        assertFalse(london(utc(2025, 6, 21, 4, 30)));
    }

    @Test
    public void test__london_midwinter__sunrise_08_03_sunset_15_53_utc() {
        assertTrue(london(utc(2025, 12, 21, 6, 0)));
        assertTrue(london(utc(2025, 12, 21, 7, 20)));
        assertFalse(london(utc(2025, 12, 21, 7, 45)));
        assertFalse(london(utc(2025, 12, 21, 12, 0)));
        assertFalse(london(utc(2025, 12, 21, 16, 10)));
        assertTrue(london(utc(2025, 12, 21, 16, 35)));
        assertTrue(london(utc(2025, 12, 21, 22, 0)));
    }

    @Test
    public void test__equator_at_equinox__sunrise_06_07_sunset_18_09_utc() {
        assertTrue(DayNight.isNight(0, 0, utc(2025, 3, 20, 5, 30)));
        assertFalse(DayNight.isNight(0, 0, utc(2025, 3, 20, 5, 50)));
        assertFalse(DayNight.isNight(0, 0, utc(2025, 3, 20, 12, 0)));
        assertFalse(DayNight.isNight(0, 0, utc(2025, 3, 20, 18, 30)));
        assertTrue(DayNight.isNight(0, 0, utc(2025, 3, 20, 18, 50)));
    }

    @Test
    public void test__sydney_in_the_southern_summer__local_time_is_utc_plus_11() {
        // sunrise 05:41 local = 18:41 utc (the day before), sunset 20:05 local = 09:05 utc
        assertFalse(DayNight.isNight(-33.87, 151.21, utc(2025, 12, 21, 5, 0))); // 16:00 local
        assertTrue(DayNight.isNight(-33.87, 151.21, utc(2025, 12, 21, 10, 0))); // 21:00 local
        assertTrue(DayNight.isNight(-33.87, 151.21, utc(2025, 12, 21, 18, 0))); // 05:00 local
        assertFalse(DayNight.isNight(-33.87, 151.21, utc(2025, 12, 21, 19, 0))); // 06:00 local
        assertFalse(DayNight.isNight(-33.87, 151.21, utc(2025, 12, 21, 23, 0))); // 10:00 local
    }

    @Test
    public void test__oakland_in_the_northern_summer__local_time_is_utc_minus_7() {
        // sunrise 05:48 local = 12:48 utc, sunset 20:25 local = 03:25 utc (the next day)
        final double lat = 37.72;
        final double lon = -122.22;
        assertFalse(DayNight.isNight(lat, lon, utc(2025, 6, 21, 18, 0))); // 11:00 local
        assertFalse(DayNight.isNight(lat, lon, utc(2025, 6, 22, 3, 0))); // 20:00 local, before the sunset
        assertTrue(DayNight.isNight(lat, lon, utc(2025, 6, 22, 4, 30))); // 21:30 local
        assertTrue(DayNight.isNight(lat, lon, utc(2025, 6, 21, 9, 0))); // 02:00 local
        assertFalse(DayNight.isNight(lat, lon, utc(2025, 6, 21, 13, 30))); // 06:30 local
    }

    @Test
    public void test__polar_day_has_no_night_and_polar_night_is_all_night() {
        // Tromso: lat 69.65, lon 18.96
        for (int hour = 0; hour < 24; hour += 3) {
            assertFalse(DayNight.isNight(69.65, 18.96, utc(2025, 6, 21, hour, 0)), "midsummer, hour " + hour);
            assertTrue(DayNight.isNight(69.65, 18.96, utc(2025, 12, 21, hour, 0)), "midwinter, hour " + hour);
        }
        // the same in the southern hemisphere, Antarctica in the southern summer
        assertFalse(DayNight.isNight(-80, 0, utc(2025, 12, 21, 0, 0)));
        assertTrue(DayNight.isNight(-80, 0, utc(2025, 6, 21, 12, 0)));
    }

    @Test
    public void test__short_summer_night_at_high_latitude_is_shorter_than_the_margins_leave() {
        // Reykjavik at midsummer: the sun sets at about 00:03 and rises at about 02:56 (Iceland is on UTC), the night is 00:33 - 02:26
        final double lat = 64.13;
        final double lon = -21.9;
        assertFalse(DayNight.isNight(lat, lon, utc(2025, 6, 21, 12, 0)));
        assertFalse(DayNight.isNight(lat, lon, utc(2025, 6, 22, 0, 10)));
        assertTrue(DayNight.isNight(lat, lon, utc(2025, 6, 22, 1, 30)));
        assertFalse(DayNight.isNight(lat, lon, utc(2025, 6, 22, 2, 45)));
    }

    @Test
    public void test__no_flicker__at_most_one_night_per_day_in_mid_latitudes_for_different_places_and_seasons() {
        final double[][] places = {{51.5, -0.1}, {-33.9, 151.2}, {37.7, -122.2}, {35.7, 139.7}, {25.2, 55.3}, {-23.5, -46.6}, {60.2, 24.9}};
        final long[] starts = {utc(2025, 3, 20, 0, 0), utc(2025, 6, 21, 0, 0), utc(2025, 9, 22, 0, 0), utc(2025, 12, 21, 0, 0)};
        for (final double[] place : places) {
            for (final long start : starts) {
                int transitions = 0;
                boolean previous = DayNight.isNight(place[0], place[1], start);
                for (int minute = 10; minute <= 48 * 60; minute += 10) {
                    final boolean current = DayNight.isNight(place[0], place[1], start + minute * 60L);
                    if (current != previous) {
                        transitions++;
                    }
                    previous = current;
                }
                // 48 hours: two nights, four transitions at most (three or four depending on where the window starts)
                assertTrue(transitions >= 2 && transitions <= 4,
                        "place " + place[0] + "," + place[1] + ", start " + start + ", transitions " + transitions);
            }
        }
    }

    @Test
    public void test__the_night_of_a_place_moves_with_the_longitude_about_four_minutes_per_degree() {
        // the same instant is the evening at 0 degrees and the night 90 degrees to the east at the same latitude
        final long time = utc(2025, 3, 20, 17, 30);
        assertFalse(DayNight.isNight(0, 0, time));
        assertTrue(DayNight.isNight(0, 90, time));
        assertFalse(DayNight.isNight(0, -90, time));
    }

    @Test
    public void test__margin_is_30_minutes() {
        assertEquals(30 * 60, DayNight.MARGIN_SECONDS);
    }
}
