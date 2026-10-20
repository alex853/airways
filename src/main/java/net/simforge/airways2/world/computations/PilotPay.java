package net.simforge.airways2.world.computations;

import net.simforge.airways2.world.datamodel.PilotContracts;

/**
 * What a pilot gets for a flight by the contract: the final flight pay and income share are calculated from the base numbers and the parameters of the
 * contract (PilotPayParams), see docs/flight-contracts.md. Nothing is stored, so when a parameter changes (the manual flight) or the base income becomes
 * known (the journeys of a ShadowJet flight) the result changes with it.
 * <p>
 * What the pilot lacks (a currency) and the manual flight reduce the pay. The reductions are added up, they are not multiplied: coefficient =
 * max(10%, 100% - the sum). The numbers are whole percents and the result is rounded half up, so the calculation is the same everywhere.
 * <p>
 * The income share is the base percent of the experience level of the pilot (the level is in the parameters) of the base income of the contract.
 */
public final class PilotPay {
    // what is taken off for a missing currency, in percents
    public static final int NO_TYPE_CURRENCY_PERCENT = 25;
    public static final int NO_NIGHT_CURRENCY_PERCENT = 25;
    public static final int CANNOT_CARRY_PASSENGERS_PERCENT = 25;
    public static final int MANUAL_PERCENT = 67;
    // the pay is not less than this part of the base
    public static final int MIN_COEFFICIENT_PERCENT = 10;

    // the base share of the income by the experience level 0 to 5, in percents
    private static final float[] BASE_SHARE_BY_LEVEL = {0.5f, 1.0f, 2.0f, 3.0f, 5.0f, 7.0f};

    private PilotPay() {
    }

    /**
     * The base share of the income for an experience level of the pilot on the type of the aircraft.
     */
    public static float baseIncomeSharePercent(final int experienceLevel) {
        if (experienceLevel < 0 || experienceLevel >= BASE_SHARE_BY_LEVEL.length) {
            throw new IllegalArgumentException("experience level is from 0 to " + (BASE_SHARE_BY_LEVEL.length - 1) + ": " + experienceLevel);
        }
        return BASE_SHARE_BY_LEVEL[experienceLevel];
    }

    /**
     * The coefficient of the flight pay in percents (10 to 100): the currencies and the manual flight count.
     */
    public static int flightPayCoefficientPercent(final PilotPayParams params) {
        int reductions = 0;
        if (!params.hasTypeCurrency()) {
            reductions += NO_TYPE_CURRENCY_PERCENT;
        }
        if (!params.hasNightCurrency()) {
            reductions += NO_NIGHT_CURRENCY_PERCENT;
        }
        if (!params.canCarryPassengers()) {
            reductions += CANNOT_CARRY_PASSENGERS_PERCENT;
        }
        if (params.isManual()) {
            reductions += MANUAL_PERCENT;
        }
        return coefficient(reductions);
    }

    /**
     * The coefficient of the income share in percents (10 to 100): only the type currency and the manual flight count.
     */
    public static int incomeShareCoefficientPercent(final PilotPayParams params) {
        int reductions = 0;
        if (!params.hasTypeCurrency()) {
            reductions += NO_TYPE_CURRENCY_PERCENT;
        }
        if (params.isManual()) {
            reductions += MANUAL_PERCENT;
        }
        return coefficient(reductions);
    }

    private static int coefficient(final int reductionsPercent) {
        return Math.max(MIN_COEFFICIENT_PERCENT, 100 - reductionsPercent);
    }

    /**
     * The flight pay: the base flight pay of the contract with the coefficient of its parameters.
     */
    public static int flightPay(final PilotContracts.Contract contract) {
        return (int) ((contract.getBaseFlightPay() * (long) flightPayCoefficientPercent(contract.getPayParams()) + 50) / 100);
    }

    /**
     * The income share: the base percent of the experience level of the base income of the contract, with the coefficient of its parameters. It is zero
     * while the base income is zero (a flight without passengers, or the journeys are not known yet).
     */
    public static int incomeShare(final PilotContracts.Contract contract) {
        final int income = contract.getBaseIncome();
        if (income <= 0) {
            return 0;
        }
        final PilotPayParams params = contract.getPayParams();
        // whole numbers: the base share in tenths of a percent, the coefficient in percents; 1000 tenths is 100% and the coefficient is divided by 100
        final long tenths = Math.round(baseIncomeSharePercent(params.getExperienceLevel()) * 10.0f);
        return (int) ((income * tenths * incomeShareCoefficientPercent(params) + 50_000) / 100_000);
    }

    /**
     * Everything the pilot gets for the flight.
     */
    public static int total(final PilotContracts.Contract contract) {
        return flightPay(contract) + incomeShare(contract);
    }
}
