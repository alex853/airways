package net.simforge.airways2.world.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * What a BusyBirds pilot gets for a leg of a mission. Money is integer, there are no fractions.
 * <p>
 * Two parts:
 * <ul>
 *     <li>flight pay: the base hourly rate of the aircraft type multiplied by the planned duration of the leg;</li>
 *     <li>income share: {@link #INCOME_SHARE_PERCENT} percent of the income of the mission, a revenue leg only (a reposition leg carries
 *     nobody, so it has no income).</li>
 * </ul>
 * todo ak3 both parts are going to be multiplied by coefficients which depend on the currency of the pilot (type rating, landings in the
 * last 90 days, night landings, experience), see docs/busy-birds.md. The coefficients are 1 now.
 */
public final class BusyBirdsPilotPay {
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsPilotPay.class);

    public static final int INCOME_SHARE_PERCENT = 3;

    // the base hourly rates by ICAO code of the aircraft type
    private static final Map<String, Integer> BASE_HOURLY_RATES = Map.of(
            "C25C", 200,
            "GA7C", 500,
            "GLF6", 500);

    // for a type which is not in the table, the cheapest rate
    private static final int DEFAULT_HOURLY_RATE = 200;

    private BusyBirdsPilotPay() {
    }

    public static int baseHourlyRate(final String aircraftTypeIcao) {
        final Integer rate = BASE_HOURLY_RATES.get(aircraftTypeIcao);
        if (rate == null) {
            log.warn("BusyBirds pilot pay - no base hourly rate for aircraft type {}, default {} is used", aircraftTypeIcao, DEFAULT_HOURLY_RATE);
            return DEFAULT_HOURLY_RATE;
        }
        return rate;
    }

    /**
     * @param plannedDurationSeconds planned duration of the leg, from blocks off to blocks on
     * @param revenueLeg             true if the leg carries the passengers of the mission
     * @param missionIncome          income of the mission, it is shared with the pilot on the revenue leg only
     */
    public static Salary calcLegSalary(final String aircraftTypeIcao,
                                       final int plannedDurationSeconds,
                                       final boolean revenueLeg,
                                       final int missionIncome) {
        final int flightPay = (int) Math.round(baseHourlyRate(aircraftTypeIcao) * plannedDurationSeconds / 3600.0);
        final int incomeShare = revenueLeg ? (int) Math.round(missionIncome * INCOME_SHARE_PERCENT / 100.0) : 0;
        return new Salary(flightPay, incomeShare);
    }

    public record Salary(int flightPay, int incomeShare) {
        public static final Salary ZERO = new Salary(0, 0);

        public int total() {
            return flightPay + incomeShare;
        }

        public Salary plus(final Salary other) {
            return new Salary(flightPay + other.flightPay, incomeShare + other.incomeShare);
        }
    }
}
