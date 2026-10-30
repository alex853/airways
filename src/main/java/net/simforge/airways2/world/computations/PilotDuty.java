package net.simforge.airways2.world.computations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The limits of the work of a pilot. The duty time of a pilot: the time from Start flight to Finish flight (the preflight and the postflight are in it, so it is more than the time from
 * blocks off to blocks on). The limit is fourteen hours of duty in any sliding window of 24 hours, the window is the 24 hours before now, it does not
 * start at midnight. The flight time (from blocks off to blocks on) is limited by 80 hours in 30 days and by 800 hours in 365 days, both are sliding windows
 * too. Times are world time in seconds, nothing here knows about the world.
 */
public final class PilotDuty {
    // the length of the sliding window
    public static final int WINDOW_SECONDS = 24 * 3600;
    // the most of duty in the window
    public static final int LIMIT_MINUTES = 14 * 60;

    // A chain of flights: when the next flight starts less than this long after the finish of the previous one, the time between them is duty too (the pilot did not go off
    // duty), and the two periods are one. Periods which overlap are one as well (decided 2026-10-30).
    public static final int CHAIN_GAP_SECONDS = 2 * 3600;

    // the flight time: the windows and the most of it in them
    public static final int WINDOW_30_DAYS_SECONDS = 30 * 24 * 3600;
    public static final int LIMIT_30_DAYS_MINUTES = 80 * 60;
    public static final int WINDOW_365_DAYS_SECONDS = 365 * 24 * 3600;
    public static final int LIMIT_365_DAYS_MINUTES = 800 * 60;

    private PilotDuty() {
    }

    /**
     * The duty in the last 24 hours before now, in whole minutes: every period of duty counts as much of it as is in the window (a period which began
     * before the window is counted from the beginning of the window). A period is [start, end]; a period which is not finished has end = now.
     * <p>
     * Before that the periods of a chain are joined (see {@link #CHAIN_GAP_SECONDS}): the gap between the finish of a flight and the start of the next one, if it is less
     * than two hours, counts as duty, and the periods which overlap are counted once.
     */
    public static int dutyMinutesInWindow(final List<int[]> periods, final int now) {
        return minutesInWindow(joinChains(periods), now, WINDOW_SECONDS);
    }

    /**
     * The periods in the order of their start, the neighbours which overlap or have a gap of less than CHAIN_GAP_SECONDS between them are joined into one.
     */
    static List<int[]> joinChains(final List<int[]> periods) {
        final List<int[]> sorted = new ArrayList<>(periods);
        sorted.sort(Comparator.comparingInt(period -> period[0]));
        final List<int[]> joined = new ArrayList<>();
        for (final int[] period : sorted) {
            final int[] last = joined.isEmpty() ? null : joined.get(joined.size() - 1);
            if (last != null && period[0] - last[1] < CHAIN_GAP_SECONDS) {
                last[1] = Math.max(last[1], period[1]);
            } else {
                joined.add(new int[]{period[0], period[1]});
            }
        }
        return joined;
    }

    /**
     * The same for any window: the minutes of the periods which are in the last windowSeconds before now (flight time in 30 or 365 days).
     */
    public static int minutesInWindow(final List<int[]> periods, final int now, final int windowSeconds) {
        final int windowStart = now - windowSeconds;
        long seconds = 0;
        for (final int[] period : periods) {
            final int from = Math.max(period[0], windowStart);
            final int to = Math.min(period[1], now);
            if (to > from) {
                seconds += to - from;
            }
        }
        return (int) (seconds / 60);
    }
}
