package net.simforge.airways2.world.computations;

/**
 * What a pilot pays for the food and for the night while it is free (not in a flight and not on the way). The prices depend on the place: a city is cheaper
 * than an airport, and the prices of a place are a bit different from the prices of another one (the last digit of its id is added), so they are not round.
 * <p>
 * The meals are paid when the pilot stops being free (falls asleep, goes on the way, goes on a flight) for the time it was free in this place, a part of the
 * price of the day. The night is paid at the fall asleep and is not returned if the pilot wakes up earlier. See docs/persons-pilots.md.
 */
public final class PilotLiving {
    private PilotLiving() {
    }

    public static final int MEALS_CITY_BASE = 10;
    public static final int MEALS_AIRPORT_BASE = 50;
    public static final int SLEEP_CITY_BASE = 40;
    public static final int SLEEP_AIRPORT_BASE = 120;
    // an airport which is a hub, and an airport with more flights in a week than this, cost more
    public static final int HUB_EXTRA = 20;
    public static final int BUSY_EXTRA = 20;
    public static final int BUSY_FLIGHTS_PER_WEEK = 1000;
    // the day of a pilot for the meals: a free time of this length is a whole price of the day
    public static final int MEALS_DAY_MINUTES = 16 * 60;

    /**
     * The price in a city: the base grows with the attraction of the city (1 is the base x 1.1, 10 is x 2, 20 is x 3), plus the last digit of the id of the city.
     */
    public static int cityPrice(final int base, final float attraction, final int cityId) {
        return Math.round((float) (base * (1 + attraction / 10.0))) + cityId % 10;
    }

    /**
     * The price at an airport: the base, plus the extra for a hub, plus the extra for a busy airport (more than 1000 flights in a week), plus the last digit
     * of the id of the airport.
     */
    public static int airportPrice(final int base, final boolean hub, final int flightsPerWeek, final int airportId) {
        return base + (hub ? HUB_EXTRA : 0) + (flightsPerWeek > BUSY_FLIGHTS_PER_WEEK ? BUSY_EXTRA : 0) + airportId % 10;
    }

    /**
     * The meals for the time the pilot was free in a place: the part of the price of the day, not more than the whole price (the day is 16 hours).
     */
    public static int mealsAmount(final int dayPrice, final int minutes) {
        if (minutes <= 0) {
            return 0;
        }
        return Math.round((float) (dayPrice * Math.min(1.0, minutes / (double) MEALS_DAY_MINUTES)));
    }
}
