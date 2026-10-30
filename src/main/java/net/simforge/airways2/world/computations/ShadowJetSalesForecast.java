package net.simforge.airways2.world.computations;

/**
 * What the sales team of ShadowJet hopes to gather for a flight, by how long before the departure the flight is booked: the earlier, the more passengers. It is a
 * hope, not a promise: how many passengers the flight really has is not known until the end of the booking. The figures are of the sales team (2026-10-30), the
 * least and the most load in percents of the seats:
 * <ul>
 * <li>the departure within an hour: 20 to 25%;</li>
 * <li>within 6 hours: 40 to 50%;</li>
 * <li>within 12 hours: 70 to 80%;</li>
 * <li>within 24 hours: 80 to 100%, if such passengers exist.</li>
 * </ul>
 * Between these points the figures go linearly: the least load by its own line, the most load by its own. Nearer than an hour it is the figures of an hour, further than
 * a day the figures of a day (the page does not offer a departure further than a day).
 * <p>
 * This is the forecast which is told to the pilot, it is not what the flight gets: the passengers of a flight are gathered by ShadowJetLogic, and it is going to be changed
 * (the urgent boarding of a flight of a pilot gets 20 to 30% at once and 40 to 50% in the first 4 hours, the NPC flights have their own thresholds), see
 * docs/clients-and-flight-modes.md.
 */
public final class ShadowJetSalesForecast {
    private static final int HOUR = 60 * 60;

    // the points: how long before the departure, the least load and the most load
    private static final int[] HOURS = {1, 6, 12, 24};
    private static final int[] FROM_PERCENT = {20, 40, 70, 80};
    private static final int[] TO_PERCENT = {25, 50, 80, 100};

    private ShadowJetSalesForecast() {
    }

    /**
     * The expected load of a flight in percents of the seats.
     *
     * @param loadFromPercent the least load which the sales team hopes to gather
     * @param loadToPercent   the most
     */
    public record ExpectedLoad(int loadFromPercent, int loadToPercent) {
    }

    /**
     * The expected load of a flight which is booked secondsBeforeDeparture before its departure, the figures between the points are interpolated linearly and rounded.
     */
    public static ExpectedLoad expectedLoad(final long secondsBeforeDeparture) {
        if (secondsBeforeDeparture <= HOURS[0] * HOUR) {
            return new ExpectedLoad(FROM_PERCENT[0], TO_PERCENT[0]);
        }
        final int last = HOURS.length - 1;
        if (secondsBeforeDeparture >= HOURS[last] * HOUR) {
            return new ExpectedLoad(FROM_PERCENT[last], TO_PERCENT[last]);
        }
        int i = 0;
        while (secondsBeforeDeparture > HOURS[i + 1] * HOUR) {
            i++;
        }
        final double share = (secondsBeforeDeparture - HOURS[i] * (double) HOUR) / ((HOURS[i + 1] - HOURS[i]) * (double) HOUR);
        return new ExpectedLoad(
                (int) Math.round(FROM_PERCENT[i] + (FROM_PERCENT[i + 1] - FROM_PERCENT[i]) * share),
                (int) Math.round(TO_PERCENT[i] + (TO_PERCENT[i + 1] - TO_PERCENT[i]) * share));
    }
}
