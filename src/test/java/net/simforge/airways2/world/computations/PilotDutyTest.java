package net.simforge.airways2.world.computations;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The duty in the sliding window of 24 hours.
 */
class PilotDutyTest {
    private static final int HOUR = 3600;
    private static final int NOW = 1_800_000_000;

    private static int[] period(final int startHoursAgo, final int endHoursAgo) {
        return new int[]{NOW - startHoursAgo * HOUR, NOW - endHoursAgo * HOUR};
    }

    @Test
    void noPeriodsNoDuty() {
        assertEquals(0, PilotDuty.dutyMinutesInWindow(List.of(), NOW));
    }

    @Test
    void periodsInTheWindowAreAddedUp() {
        // 3 hours and 2 hours of duty
        assertEquals(5 * 60, PilotDuty.dutyMinutesInWindow(List.of(period(10, 7), period(5, 3)), NOW));
    }

    @Test
    void aPeriodWhichBeganBeforeTheWindowCountsFromTheBeginningOfTheWindow() {
        // from 26 hours ago to 22 hours ago: only the last two hours are in the window
        assertEquals(2 * 60, PilotDuty.dutyMinutesInWindow(List.of(period(26, 22)), NOW));
    }

    @Test
    void aPeriodBeforeTheWindowIsNotCounted() {
        assertEquals(0, PilotDuty.dutyMinutesInWindow(List.of(period(40, 30)), NOW));
    }

    @Test
    void theWindowSlidesItIsNotTheCalendarDay() {
        // the same four hours of duty: in the window now, out of it 25 hours later
        final List<int[]> periods = List.of(period(6, 2));
        assertEquals(4 * 60, PilotDuty.dutyMinutesInWindow(periods, NOW));
        assertEquals(0, PilotDuty.dutyMinutesInWindow(periods, NOW + 25 * HOUR));
        // and 21 hours later one hour of it is still in the window
        assertEquals(60, PilotDuty.dutyMinutesInWindow(periods, NOW + 21 * HOUR));
    }

    @Test
    void aFlightInProgressCountsUpToNow() {
        // started 3 hours ago, not finished: its end is now
        assertEquals(3 * 60, PilotDuty.dutyMinutesInWindow(List.of(new int[]{NOW - 3 * HOUR, NOW}), NOW));
    }

    @Test
    void onlyWholeMinutesAreCounted() {
        assertEquals(1, PilotDuty.dutyMinutesInWindow(List.of(new int[]{NOW - 119, NOW}), NOW));
    }

    @Test
    void theLimitIsFourteenHours() {
        assertEquals(14 * 60, PilotDuty.LIMIT_MINUTES);
    }
}
