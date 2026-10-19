package net.simforge.airways2.world.datamodel;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The date as days from 2024-12-31 and the time as 12 bits of minutes of that date with the offset 1000.
 */
class DateMinutesTest {
    private static final LocalDate DATE = LocalDate.of(2025, 6, 21);

    private static int utc(final int day, final int hour, final int minute) {
        return (int) LocalDateTime.of(2025, 6, day, 0, 0).plusHours(hour).plusMinutes(minute).toEpochSecond(ZoneOffset.UTC);
    }

    @Test
    public void test__days_start_from_the_day_before_the_first_day() {
        assertEquals(1, DateMinutes.toDays(LocalDate.of(2025, 1, 1)));
        assertEquals(2, DateMinutes.toDays(LocalDate.of(2025, 1, 2)));
        assertEquals(172, DateMinutes.toDays(DATE));
        assertEquals(LocalDate.of(2025, 1, 1), DateMinutes.fromDays(1));
        assertEquals(DATE, DateMinutes.fromDays(172));
    }

    @Test
    public void test__no_date_is_zero() {
        assertEquals(0, DateMinutes.toDays(null));
        assertNull(DateMinutes.fromDays(0));
    }

    @Test
    public void test__two_bytes_of_days_are_about_179_years() {
        final LocalDate last = DateMinutes.fromDays(65535);
        assertEquals(65535, DateMinutes.toDays(last));
        assertEquals(2204, last.getYear());

        assertThrows(IllegalArgumentException.class, () -> DateMinutes.toDays(last.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> DateMinutes.toDays(LocalDate.of(2024, 12, 31)));
        assertThrows(IllegalArgumentException.class, () -> DateMinutes.toDays(LocalDate.of(2020, 1, 1)));
    }

    @Test
    public void test__a_time_is_minutes_of_the_date_plus_1000() {
        assertEquals(1000, DateMinutes.toStored(DATE, utc(21, 0, 0))); // the midnight
        assertEquals(1750, DateMinutes.toStored(DATE, utc(21, 12, 30)));
        assertEquals(1000 + 1439, DateMinutes.toStored(DATE, utc(21, 23, 59)));
        assertEquals(1000 + 1440, DateMinutes.toStored(DATE, utc(22, 0, 0))); // the next day
    }

    @Test
    public void test__a_time_goes_there_and_back_with_an_accuracy_of_a_minute() {
        final int time = utc(21, 12, 30) + 45; // 45 seconds are dropped
        assertEquals(utc(21, 12, 30), DateMinutes.fromStored(DATE, DateMinutes.toStored(DATE, time)));
    }

    @Test
    public void test__the_window_is_from_minus_16_hours_40_minutes_to_plus_49_hours_59_minutes() {
        assertEquals(1, DateMinutes.toStored(DATE, utc(21, 0, 0) - 999 * 60));
        assertEquals(3999, DateMinutes.toStored(DATE, utc(21, 0, 0) + 2999 * 60));

        assertThrows(IllegalArgumentException.class, () -> DateMinutes.toStored(DATE, utc(21, 0, 0) - 1000 * 60));
        assertThrows(IllegalArgumentException.class, () -> DateMinutes.toStored(DATE, utc(21, 0, 0) + 3000 * 60));
    }

    @Test
    public void test__no_date_or_no_time_is_zero_in_both_directions() {
        assertEquals(0, DateMinutes.toStored(null, utc(21, 12, 0)));
        assertEquals(0, DateMinutes.toStored(DATE, 0));
        assertEquals(0, DateMinutes.fromStored(null, 1750));
        assertEquals(0, DateMinutes.fromStored(DATE, 0));
    }

    @Test
    public void test__twelve_bits_hold_the_whole_window() {
        assertEquals(true, DateMinutes.toStored(DATE, utc(21, 0, 0) + 2999 * 60) < (1 << DateMinutes.TIME_BITS));
    }
}
