package net.simforge.airways2.world.computations;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * London, midsummer: the night is from 20:51 to 03:13 UTC.
 */
class NightTimeMeterTest {
    private static final double LAT = 51.5;
    private static final double LON = -0.1;

    private static int utc(final int hour, final int minute) {
        return (int) LocalDateTime.of(2025, 6, 21, 0, 0).plusHours(hour).plusMinutes(minute).toEpochSecond(ZoneOffset.UTC);
    }

    private static NightTimeMeter counting(final int startHour, final int startMinute) {
        return new NightTimeMeter(0.0, 0).startAt(utc(startHour, startMinute));
    }

    @Test
    public void test__a_new_meter_is_not_counting_and_has_no_night() {
        final NightTimeMeter meter = new NightTimeMeter(0.0, 0);

        assertFalse(meter.isCounting());
        assertEquals(0.0, meter.getNightSeconds(), 0.0);
        assertEquals(0.0f, meter.getNightMinutes(), 0.0f);
    }

    @Test
    public void test__it_is_raised_from_the_flight_mission__the_value_is_restored_and_the_counting_starts_at_the_next_report() {
        final NightTimeMeter meter = NightTimeMeter.restoredFromMission(12.5f);

        assertEquals(750.0, meter.getNightSeconds(), 0.0);
        assertFalse(meter.isCounting());

        final NightTimeMeter first = meter.report(true, LAT, LON, utc(23, 0));
        assertTrue(first.isCounting());
        assertEquals(750.0, first.getNightSeconds(), 0.0); // the first report only starts the counting, the time of the restart is lost

        final NightTimeMeter second = first.report(true, LAT, LON, utc(23, 1));
        assertEquals(810.0, second.getNightSeconds(), 0.0);
    }

    @Test
    public void test__a_report_at_night_adds_the_time_since_the_previous_report() {
        NightTimeMeter meter = counting(22, 0);

        meter = meter.report(true, LAT, LON, utc(22, 10));
        assertEquals(600.0, meter.getNightSeconds(), 0.0);

        meter = meter.report(true, LAT, LON, utc(22, 12));
        assertEquals(720.0, meter.getNightSeconds(), 0.0);
        assertEquals(12.0f, meter.getNightMinutes(), 0.0f);
    }

    @Test
    public void test__a_report_at_day_adds_nothing_but_moves_the_counted_time() {
        NightTimeMeter meter = counting(12, 0);

        meter = meter.report(true, LAT, LON, utc(12, 10));

        assertEquals(0.0, meter.getNightSeconds(), 0.0);
        assertEquals(utc(12, 10), meter.getCountedAt());
    }

    @Test
    public void test__the_taxi_counts_like_the_flight__every_report_between_blocks_off_and_blocks_on() {
        // blocks off at 22:00, a report every 30 seconds, blocks on at 00:12: 2 hours 12 minutes
        NightTimeMeter meter = counting(22, 0);
        for (int time = utc(22, 0) + 30; time <= utc(24, 12); time += 30) {
            meter = meter.report(true, LAT, LON, time);
        }

        assertEquals(132 * 60.0, meter.getNightSeconds(), 0.0);
        assertEquals(132.0f, meter.getNightMinutes(), 0.0f);
    }

    @Test
    public void test__many_small_pieces_do_not_lose_anything__the_exact_value_is_a_double_in_the_context() {
        // 5 hours of night (22:00 - 03:00) in pieces of 7 seconds
        NightTimeMeter meter = counting(22, 0);
        int time = utc(22, 0);
        for (int i = 0; i < 18000 / 7; i++) {
            time += 7;
            meter = meter.report(true, LAT, LON, time);
        }

        assertEquals((18000 / 7) * 7.0, meter.getNightSeconds(), 0.0);
    }

    @Test
    public void test__one_piece_is_limited_by_15_minutes_a_gap_means_the_client_was_offline() {
        NightTimeMeter meter = counting(22, 0);

        meter = meter.report(true, LAT, LON, utc(23, 30)); // 90 minutes without a report

        assertEquals(NightTimeMeter.MAX_PIECE_SECONDS, meter.getNightSeconds(), 0.0);
        assertEquals(utc(23, 30), meter.getCountedAt());
    }

    @Test
    public void test__the_position_of_the_report_decides__far_to_the_west_it_is_still_day() {
        NightTimeMeter meter = counting(23, 0);

        meter = meter.report(true, LAT, -100.0, utc(23, 10)); // the afternoon at 100 degrees to the west

        assertEquals(0.0, meter.getNightSeconds(), 0.0);
    }

    @Test
    public void test__not_counting_means_stopped__blocks_on__the_night_time_stays() {
        NightTimeMeter meter = counting(22, 0).report(true, LAT, LON, utc(22, 10));

        meter = meter.report(false, LAT, LON, utc(22, 20));

        assertFalse(meter.isCounting());
        assertEquals(600.0, meter.getNightSeconds(), 0.0);
        // the reports after the stop add nothing
        meter = meter.report(false, LAT, LON, utc(22, 30));
        assertEquals(600.0, meter.getNightSeconds(), 0.0);
    }

    @Test
    public void test__stop_and_start_again__the_time_between_is_not_counted() {
        NightTimeMeter meter = counting(22, 0).report(true, LAT, LON, utc(22, 10)).stop();

        meter = meter.startAt(utc(23, 0)).report(true, LAT, LON, utc(23, 5));

        assertEquals(600.0 + 300.0, meter.getNightSeconds(), 0.0);
    }

    @Test
    public void test__a_report_at_the_same_second_or_in_the_past_changes_nothing() {
        final NightTimeMeter meter = counting(22, 0).report(true, LAT, LON, utc(22, 10));

        assertSame(meter, meter.report(true, LAT, LON, utc(22, 10)));
        assertSame(meter, meter.report(true, LAT, LON, utc(22, 5)));
    }

    @Test
    public void test__the_meter_is_immutable() {
        final NightTimeMeter meter = counting(22, 0);

        final NightTimeMeter later = meter.report(true, LAT, LON, utc(22, 10));

        assertEquals(0.0, meter.getNightSeconds(), 0.0);
        assertEquals(600.0, later.getNightSeconds(), 0.0);
    }
}
