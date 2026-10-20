package net.simforge.airways2.world.computations;

/**
 * The night time of a flight from blocks off to blocks on, as the flight mission control measures it from the positions of the aircraft (whoever sets
 * them: the sim tracker, the VATSIM tracker or the simple flight of the world). The control keeps it in memory (the exact value, seconds as a double)
 * and only writes the current value to the flight mission, to restore it if the application is restarted: after the restore the value is as exact as
 * the flight mission keeps it (a half float of minutes, a few seconds), and the measurement goes on.
 * <p>
 * The meter is immutable. A report adds the time since the previous report if it is night at the reported position; the time of one report is limited
 * by {@link #MAX_PIECE_SECONDS}: a tracker could be offline, nobody knows what happened there.
 */
public final class NightTimeMeter {
    public static final int MAX_PIECE_SECONDS = 15 * 60;

    private final double nightSeconds;
    // world time (seconds) the night time is counted up to, zero if the meter is not counting now
    private final int countedAt;

    public NightTimeMeter(final double nightSeconds, final int countedAt) {
        this.nightSeconds = nightSeconds;
        this.countedAt = countedAt;
    }

    /**
     * The meter which is raised from a flight mission: the night time is what the flight mission has kept, the counting starts at the next report.
     */
    public static NightTimeMeter restoredFromMission(final float nightMinutesOfMission) {
        return new NightTimeMeter(nightMinutesOfMission * 60.0, 0);
    }

    public double getNightSeconds() {
        return nightSeconds;
    }

    /**
     * The value for the flight mission.
     */
    public float getNightMinutes() {
        return (float) (nightSeconds / 60.0);
    }

    public int getCountedAt() {
        return countedAt;
    }

    public boolean isCounting() {
        return countedAt != 0;
    }

    /**
     * The position report of a mission. If the flight is between blocks off and blocks on ({@code counting}), the time since the previous report goes to
     * the night time when it is night at the reported place now. The first report of a counting flight (after a restart) only starts the counting.
     */
    public NightTimeMeter report(final boolean counting, final double latitude, final double longitude, final int worldTime) {
        if (!counting) {
            return stop();
        }
        if (countedAt == 0) {
            return startAt(worldTime);
        }
        if (worldTime <= countedAt) {
            return this; // the same moment or the clock went back: nothing to add
        }
        final double added = DayNight.isNight(latitude, longitude, worldTime)
                ? Math.min(worldTime - countedAt, MAX_PIECE_SECONDS)
                : 0;
        return new NightTimeMeter(nightSeconds + added, worldTime);
    }

    /**
     * Blocks off: the counting starts now.
     */
    public NightTimeMeter startAt(final int worldTime) {
        return new NightTimeMeter(nightSeconds, worldTime);
    }

    /**
     * Blocks on or the end of the flight: the counting is over, the night time stays.
     */
    public NightTimeMeter stop() {
        return countedAt == 0 ? this : new NightTimeMeter(nightSeconds, 0);
    }

    @Override
    public String toString() {
        return "NightTimeMeter{night " + Math.round(nightSeconds) + " s, counted at " + countedAt + '}';
    }
}
