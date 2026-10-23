package net.simforge.airways2.world.computations;

/**
 * The awake credit of a pilot: how long the pilot can stay awake and active. Sleep fills it, being awake spends it, see docs/persons-pilots.md.
 * <p>
 * The credit is stored in minutes together with the moment it was fixed (when the pilot fell asleep or woke up). While the pilot is awake the credit
 * falls minute by minute, one to one with the time, and it may go below zero (the pilot is overtired). While the pilot sleeps it does not change; when the
 * pilot wakes up the sleep is added: every minute of sleep gives two minutes of the credit, but a sleep shorter than two hours gives nothing. There is no
 * maximum of the credit: nobody sleeps for long, the pilot wakes up by itself after 7.5 to 10 hours (see AUTO_WAKE_UP_MIN_MINUTES), so the most one sleep gives is
 * twenty hours. All the numbers are minutes, nothing here knows about the world.
 */
public final class PilotRest {
    // the credit of a pilot who starts: a rested one
    public static final int INITIAL_CREDIT_MINUTES = 16 * 60;
    // The pilot wakes up by itself: a random moment from 7.5 to 10 hours after falling asleep. The pilot is not told when it is, a usual sleep is 8 to 9 hours.
    public static final int AUTO_WAKE_UP_MIN_MINUTES = 450;
    public static final int AUTO_WAKE_UP_MAX_MINUTES = 600;
    // a shorter sleep is not a rest, it gives nothing
    public static final int MIN_SLEEP_MINUTES = 2 * 60;
    // A pilot who falls asleep in debt (a negative credit) sleeps longer: an hour of sleep more for every three hours of the debt, but only the first three
    // hours of the debt count, the rest of it adds nothing (so the most is an hour).
    public static final int EXTRA_SLEEP_DEBT_LIMIT_MINUTES = 3 * 60;
    public static final int DEBT_MINUTES_PER_EXTRA_SLEEP_MINUTE = 3;
    // after a wake up the credit above 18 hours is cut to a random value from 18 hours to the credit but not more than 22 hours, see capAfterWakeUp
    public static final int WAKE_UP_CAP_FROM_MINUTES = 18 * 60;
    public static final int WAKE_UP_CAP_TO_MINUTES = 22 * 60;
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
     * How much longer the sleep is for the debt the pilot falls asleep with: the debt (the credit below zero) up to three hours, a third of it. Three hours of the
     * debt give an hour, an hour and a half give half an hour, a debt of five hours gives the same hour. No debt (the credit is zero or more) gives nothing.
     */
    public static int extraSleepMinutes(final int creditAtSleepStart) {
        if (creditAtSleepStart >= 0) {
            return 0;
        }
        final int debt = Math.min(-creditAtSleepStart, EXTRA_SLEEP_DEBT_LIMIT_MINUTES);
        return debt / DEBT_MINUTES_PER_EXTRA_SLEEP_MINUTE;
    }

    /**
     * The credit after a sleep of the minutes: filled by two minutes for a minute; a short sleep leaves it as it was.
     */
    public static int creditAfterSleep(final int creditAtSleepStart, final int sleepMinutes) {
        if (sleepMinutes < MIN_SLEEP_MINUTES) {
            return creditAtSleepStart;
        }
        return clamp(creditAtSleepStart + CREDIT_PER_SLEEP_MINUTE * sleepMinutes);
    }

    /**
     * The random cap of the credit after a wake up: a credit above 18 hours becomes a random value from 18 hours to the credit but not more than 22 hours.
     * The credit up to 18 hours stays as it is. The random value is given by the caller (from 0 to 1, exclusive) so the computation stays testable.
     */
    public static int capAfterWakeUp(final int credit, final double random) {
        if (credit <= WAKE_UP_CAP_FROM_MINUTES) {
            return credit;
        }
        final int to = Math.min(credit, WAKE_UP_CAP_TO_MINUTES);
        return WAKE_UP_CAP_FROM_MINUTES + (int) (random * (to - WAKE_UP_CAP_FROM_MINUTES + 1));
    }

    /**
     * What the credit after a wake up is expected to be, for a preview: the credit itself up to 18 hours, above it the middle of the range of the random cap
     * (see capAfterWakeUp).
     */
    public static int expectedAfterWakeUp(final int credit) {
        if (credit <= WAKE_UP_CAP_FROM_MINUTES) {
            return credit;
        }
        return (WAKE_UP_CAP_FROM_MINUTES + Math.min(credit, WAKE_UP_CAP_TO_MINUTES)) / 2;
    }

    /**
     * The most the credit can be after a wake up: the credit itself up to 18 hours, above it the upper end of the random cap (22 hours at most).
     */
    public static int maxAfterWakeUp(final int credit) {
        return credit <= WAKE_UP_CAP_FROM_MINUTES ? credit : Math.min(credit, WAKE_UP_CAP_TO_MINUTES);
    }

    // the credit is stored in two bytes
    private static int clamp(final int minutes) {
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, minutes));
    }
}
