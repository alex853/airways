package net.simforge.airways2.world.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The base pay of a BusyBirds pilot: the base hourly rates of the aircraft types and the flight pay before the coefficients. What the pilot gets for a leg
 * (the flight pay and the income share by the experience level, with the deductions for the currency) is calculated by PilotPay, see
 * docs/flight-contracts.md. Money is integer, there are no fractions.
 */
public final class BusyBirdsPilotPay {
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsPilotPay.class);

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
     * The flight pay before the coefficients of the pilot: the base hourly rate of the type multiplied by the planned duration of the leg, rounded.
     * What the pilot gets is calculated by PilotPay from it and the parameters of the pilot.
     */
    public static int baseFlightPay(final String aircraftTypeIcao, final int plannedDurationSeconds) {
        return (int) Math.round(baseHourlyRate(aircraftTypeIcao) * plannedDurationSeconds / 3600.0);
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
