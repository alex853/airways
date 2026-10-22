package net.simforge.airways2.world.computations;

import java.util.List;

/**
 * The duty time of a pilot: the time from Start flight to Finish flight (the preflight and the postflight are in it, so it is more than the time from
 * blocks off to blocks on). The limit is fourteen hours of duty in any sliding window of 24 hours, the window is the 24 hours before now, it does not
 * start at midnight. Times are world time in seconds, nothing here knows about the world.
 */
public final class PilotDuty {
    // the length of the sliding window
    public static final int WINDOW_SECONDS = 24 * 3600;
    // the most of duty in the window
    public static final int LIMIT_MINUTES = 14 * 60;

    private PilotDuty() {
    }

    /**
     * The duty in the last 24 hours before now, in whole minutes: every period of duty counts as much of it as is in the window (a period which began
     * before the window is counted from the beginning of the window). A period is [start, end]; a period which is not finished has end = now.
     */
    public static int dutyMinutesInWindow(final List<int[]> periods, final int now) {
        final int windowStart = now - WINDOW_SECONDS;
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
