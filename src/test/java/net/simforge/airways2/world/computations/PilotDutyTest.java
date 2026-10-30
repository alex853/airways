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
    void aGapOfLessThanTwoHoursBetweenFlightsIsDuty() {
        // 3 hours of duty, a gap of 30 minutes, 2 hours of duty: the pilot did not go off duty, it is 5.5 hours
        assertEquals(5 * 60 + 30, PilotDuty.dutyMinutesInWindow(List.of(period(10, 7), new int[]{NOW - 7 * HOUR + 30 * 60, NOW - 5 * HOUR + 30 * 60}), NOW));
        // the same just under two hours
        assertEquals(3 * 60 + 119 + 2 * 60, PilotDuty.dutyMinutesInWindow(List.of(period(10, 7), new int[]{NOW - 7 * HOUR + 119 * 60, NOW - 5 * HOUR + 119 * 60}), NOW));
    }

    @Test
    void aGapOfTwoHoursOrMoreIsNotDuty() {
        // exactly two hours: not less than two, so they are two periods (3 + 2 hours)
        assertEquals(5 * 60, PilotDuty.dutyMinutesInWindow(List.of(period(10, 7), period(5, 3)), NOW));
        // three hours
        assertEquals(5 * 60, PilotDuty.dutyMinutesInWindow(List.of(period(10, 7), period(4, 2)), NOW));
    }

    @Test
    void periodsWhichOverlapAreCountedOnce() {
        // the second flight starts before the first one is finished: 10 to 6 hours ago and 8 to 3 hours ago make one period of 7 hours
        assertEquals(7 * 60, PilotDuty.dutyMinutesInWindow(List.of(period(10, 6), period(8, 3)), NOW));
        // a period inside another one adds nothing
        assertEquals(4 * 60, PilotDuty.dutyMinutesInWindow(List.of(period(10, 6), period(9, 7)), NOW));
    }

    @Test
    void theOrderOfThePeriodsDoesNotMatter() {
        assertEquals(5 * 60 + 30, PilotDuty.dutyMinutesInWindow(List.of(new int[]{NOW - 7 * HOUR + 30 * 60, NOW - 5 * HOUR + 30 * 60}, period(10, 7)), NOW));
    }

    @Test
    void aChainWhichBeganBeforeTheWindowIsCountedFromTheBeginningOfTheWindow() {
        // two flights with a gap of one hour: 28 to 25 hours ago and 24 to 22 hours ago: the whole chain from 28 to 22, the window takes the last 2 hours
        assertEquals(2 * 60, PilotDuty.dutyMinutesInWindow(List.of(period(28, 25), period(24, 22)), NOW));
        // the gap before the window start counts too: 26 to 25.5 and 24.5 to 22: one chain from 26 to 22, the window (24 hours) takes 2 hours
        assertEquals(2 * 60, PilotDuty.dutyMinutesInWindow(List.of(new int[]{NOW - 26 * HOUR, NOW - 25 * HOUR - 1800}, new int[]{NOW - 24 * HOUR - 1800, NOW - 22 * HOUR}), NOW));
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

    @Test
    void flightTimeLimitsAre80HoursIn30DaysAnd800HoursIn365Days() {
        assertEquals(80 * 60, PilotDuty.LIMIT_30_DAYS_MINUTES);
        assertEquals(800 * 60, PilotDuty.LIMIT_365_DAYS_MINUTES);
        assertEquals(30 * 24 * HOUR, PilotDuty.WINDOW_30_DAYS_SECONDS);
        assertEquals(365 * 24 * HOUR, PilotDuty.WINDOW_365_DAYS_SECONDS);
    }

    @Test
    void theSameFlightsAreInTheYearButNotAlwaysInTheMonth() {
        // 5 hours ten days ago and 6 hours 100 days ago
        final List<int[]> flights = List.of(period(10 * 24 + 5, 10 * 24), period(100 * 24 + 6, 100 * 24));

        assertEquals(5 * 60, PilotDuty.minutesInWindow(flights, NOW, PilotDuty.WINDOW_30_DAYS_SECONDS));
        assertEquals(11 * 60, PilotDuty.minutesInWindow(flights, NOW, PilotDuty.WINDOW_365_DAYS_SECONDS));
    }

    @Test
    void aFlightWhichBeganBeforeTheMonthCountsFromTheBeginningOfTheWindow() {
        // from 30 days and 3 hours ago to 30 days ago: three hours are out of the window
        final List<int[]> flights = List.of(period(30 * 24 + 3, 30 * 24 - 2));

        assertEquals(2 * 60, PilotDuty.minutesInWindow(flights, NOW, PilotDuty.WINDOW_30_DAYS_SECONDS));
    }
}
