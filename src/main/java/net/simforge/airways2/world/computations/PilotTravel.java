package net.simforge.airways2.world.computations;

/**
 * How a free pilot moves from an airport to another one by itself (not on a flight of the game): the ways, what they cost and how long they take. The
 * distance is the straight line between the airports in nautical miles, there is no limit of realism (from Moscow to Buenos Aires by ground is possible, it
 * just takes a week).
 */
public final class PilotTravel {
    private PilotTravel() {
    }

    public enum Method {
        // the feature flag of the person (Person.isWarpDrive): the pilot is there at once and it costs nothing
        WarpDrive("Warp drive"),
        // the personal aircraft of Mach 3, it is very expensive
        Mach3("Mach 3 flight"),
        // trains and cars: cheap and slow
        Ground("By ground");

        private final String title;

        Method(final String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    // Mach 3 is about 3 x 573 knots (the speed of sound at the cruise altitude)
    public static final int MACH3_KNOTS = 1720;
    // 10 000 for every 500 miles
    public static final int MACH3_PRICE_PER_MILE = 20;
    public static final int GROUND_KNOTS = 30;
    public static final int GROUND_PRICE_PER_MILE = 2;

    /**
     * What the way costs and how long it takes: minutes (zero is at once) and money (a whole number, zero is free).
     */
    public record Quote(int durationMinutes, long price) {
    }

    public static Quote quote(final Method method, final double distanceNm) {
        return switch (method) {
            case WarpDrive -> new Quote(0, 0);
            case Mach3 -> new Quote(minutes(distanceNm, MACH3_KNOTS), Math.round(distanceNm * MACH3_PRICE_PER_MILE));
            case Ground -> new Quote(minutes(distanceNm, GROUND_KNOTS), Math.round(distanceNm * GROUND_PRICE_PER_MILE));
        };
    }

    // the time of the distance at the speed, a whole minute up, not less than a minute
    private static int minutes(final double distanceNm, final int knots) {
        return Math.max(1, (int) Math.ceil(distanceNm / knots * 60));
    }
}
