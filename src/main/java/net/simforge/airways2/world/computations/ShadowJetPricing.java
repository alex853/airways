package net.simforge.airways2.world.computations;

import net.simforge.airways2.tools.CabinLayout;

/**
 * The price of a journey on a ShadowJet flight of a pilot: what the passengers pay for the trip. It is set in the price of the journey (Journeys.Journey.getPrice) when the
 * journey is booked to the flight (JourneyControl.bookDirectFlightJourneyNoChecks), the same field in which BusyBirds keeps the price of its business charter, so the income
 * of the transport flight (TransportFlightExtras) is the sum of the prices of the journeys of the flight for both operators, with no special case.
 * <p>
 * The price depends on the distance of the flight, the number of the passengers of the journey and the class of the service: the price of a passenger of a class is
 * a fixed part and a part by the distance, multiplied by the factor of the class, then multiplied by the number of the passengers. The class is the ACTUAL class of the
 * journey, the one it is seated in (Journeys.Journey.getBookedCabinService, with the upgrade or the downgrade), not the class it prefers (getPreferredCabinService).
 * The factors of the classes (2026-10-30): Y 1, W 1.5, J 4, F 7.
 * <p>
 * todo ak1 THE NUMBERS ARE PARTLY A DRAFT (2026-10-30): the structure (the distance, the passengers, the actual class) and the factors of the classes are decided, the fixed part
 * (30) and the price of a nautical mile (0.12) are not, they are taken to have a plausible income share of the pilot (the shares of ShadowJet are 0.1 to 3%,
 * PilotPayRates.SHADOW_JET) and must be agreed. Nothing is rounded to a "nice" number yet.
 */
public final class ShadowJetPricing {
    // the fixed part of the price of a passenger of the economy class, in the money of the game
    static final double FIXED_PART_PER_PASSENGER = 30.0;
    // the part by the distance, for a nautical mile, a passenger of the economy class
    static final double PER_NAUTICAL_MILE = 0.12;

    private ShadowJetPricing() {
    }

    /**
     * The factor of the class of the service: the economy is 1.
     */
    public static double classFactor(final CabinLayout.Service service) {
        return switch (service) {
            case Y -> 1.0;
            case W -> 1.5;
            case J -> 4.0;
            case F -> 7.0;
        };
    }

    /**
     * The price of the journey of groupSize passengers on a flight of distanceNm nautical miles in the class of the service, rounded to the whole.
     */
    public static int price(final double distanceNm, final int groupSize, final CabinLayout.Service service) {
        if (distanceNm < 0 || groupSize < 0) {
            throw new IllegalArgumentException("distance and group size can not be negative: " + distanceNm + ", " + groupSize);
        }
        final double perPassenger = (FIXED_PART_PER_PASSENGER + PER_NAUTICAL_MILE * distanceNm) * classFactor(service);
        return (int) Math.round(perPassenger * groupSize);
    }
}
