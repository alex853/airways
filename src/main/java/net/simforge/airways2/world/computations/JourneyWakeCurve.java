package net.simforge.airways2.world.computations;

/**
 * How fast the journeys find a flight which has just been announced (decided 2026-10-30): the share of the seats which is booked by such a minute after the announcement of
 * the flight. The curve is the same for every flight: the longer the flight stays open for the tickets, the more it gets. It does not depend on the planned departure:
 * the flight of a pilot is open until the boarding is over, and the NPC flight (tracked via VATSIM) has only a planned time, the real boarding ends when it moves.
 * <p>
 * The flight is provided with a pack of journeys (ShadowJetLogic), every journey of the pack wakes up at the minute where the curve reaches its place in the pack, looks for
 * tickets (JourneyProcessor.lookingForTickets) and books the flight by itself. The curve is calibrated to the figures which the pilot sees on the page of the booking
 * (ShadowJetSalesForecast): the pilot books a flight a time before its departure, the boarding starts at the departure minus 30 minutes or later, and the load which the
 * curve gives after that time is inside the figures of the page (the test checks it). The speed of the booking only goes down: a flight which is open longer gets more,
 * but every next minute brings fewer passengers than the one before.
 */
public final class JourneyWakeCurve {
    // minutes after the announcement of the flight and the share of the seats in percents; after the last point the load stays the same
    private static final double[] MINUTES = {0, 1, 5, 30, 60, 150, 330, 690, 1410, 2400};
    // The speed of the booking (the slope between the points) only goes down along the whole curve: 8, 1.5, 0.36, 0.17, 0.089, 0.072, 0.061, 0.024, 0.012 percents a minute
    // (the test checks it). The points are inside the figures of the page of the booking (ShadowJetSalesForecast): 23% for a flight announced an hour before the departure
    // (20 to 25), 49% for 6 hours (40 to 50), 71% for 12 hours (70 to 80), 88% for a day (80 to 100).
    private static final double[] PERCENT = {0, 8, 14, 23, 28, 36, 49, 71, 88, 100};

    private JourneyWakeCurve() {
    }

    /**
     * The share of the seats (0 to 1) which is booked by the minute after the announcement of the flight.
     */
    public static double loadAfter(final double minutes) {
        if (minutes <= 0) {
            return 0;
        }
        final int last = MINUTES.length - 1;
        if (minutes >= MINUTES[last]) {
            return PERCENT[last] / 100.0;
        }
        int i = 0;
        while (minutes > MINUTES[i + 1]) {
            i++;
        }
        final double share = (minutes - MINUTES[i]) / (MINUTES[i + 1] - MINUTES[i]);
        return (PERCENT[i] + (PERCENT[i + 1] - PERCENT[i]) * share) / 100.0;
    }

    /**
     * The minute after the announcement of the flight when the curve reaches the share of the seats (0 to 1): the minute when the journey which has this place in the pack
     * wakes up. The same share always gives the same minute, the shares over the end of the curve (more than the whole flight) give the last minute.
     */
    public static double minutesToReach(final double load) {
        final double percent = load * 100.0;
        if (percent <= 0) {
            return 0;
        }
        final int last = PERCENT.length - 1;
        if (percent >= PERCENT[last]) {
            return MINUTES[last];
        }
        int i = 0;
        while (percent > PERCENT[i + 1]) {
            i++;
        }
        final double share = (percent - PERCENT[i]) / (PERCENT[i + 1] - PERCENT[i]);
        return MINUTES[i] + (MINUTES[i + 1] - MINUTES[i]) * share;
    }
}
