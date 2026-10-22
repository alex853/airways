package net.simforge.airways2.world.computations;

/**
 * The awake credit of a pilot: how long the pilot can stay awake and active. Sleep fills it, being awake spends it, see docs/persons-pilots.md.
 * <p>
 * The credit is stored in minutes together with the moment it was fixed (when the pilot fell asleep or woke up). While the pilot is awake the credit
 * falls minute by minute, one to one with the time, and it may go below zero (the pilot is overtired). While the pilot sleeps it does not change; when the
 * pilot wakes up the sleep is added: every minute of sleep gives two minutes of the credit, but a sleep shorter than two hours gives nothing, and the
 * credit is not more than sixteen hours. All the numbers are minutes, nothing here knows about the world.
 */
public final class PilotRest {
    // the credit of a pilot who starts: a rested one
    public static final int INITIAL_CREDIT_MINUTES = 16 * 60;
    // one cannot sleep in advance for more
    public static final int MAX_CREDIT_MINUTES = 16 * 60;
    // a shorter sleep is not a rest, it gives nothing
    public static final int MIN_SLEEP_MINUTES = 2 * 60;
    // minutes of the credit for a minute of sleep
    public static final int CREDIT_PER_SLEEP_MINUTE = 2;

    private PilotRest() {
    }

    /**
     * Whole minutes between two moments (seconds), what is not a whole minute is not counted.
     */
    public static int minutesBetween(final int fromTime, final int toTime) {
        return Math.max(0, toTime - fromTime) / 60;
    }

    /**
     * The credit of an awake pilot at the moment: it has fallen by the minutes which have passed since it was fixed. It may be negative.
     */
    public static int creditNow(final int creditMinutes, final int creditTime, final int now) {
        return clamp(creditMinutes - minutesBetween(creditTime, now));
    }

    /**
     * The credit after a sleep of the minutes: filled by two minutes for a minute, not more than the maximum; a short sleep leaves it as it was.
     */
    public static int creditAfterSleep(final int creditAtSleepStart, final int sleepMinutes) {
        if (sleepMinutes < MIN_SLEEP_MINUTES) {
            return creditAtSleepStart;
        }
        return clamp(Math.min(MAX_CREDIT_MINUTES, creditAtSleepStart + CREDIT_PER_SLEEP_MINUTE * sleepMinutes));
    }

    // the credit is stored in two bytes
    private static int clamp(final int minutes) {
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, minutes));
    }
}
