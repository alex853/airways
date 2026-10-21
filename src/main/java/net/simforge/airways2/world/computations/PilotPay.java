package net.simforge.airways2.world.computations;

import net.simforge.airways2.world.datamodel.PilotContracts;

import java.util.ArrayList;
import java.util.List;

/**
 * What a pilot gets for a flight by the contract: the final flight pay and income share are calculated from the base numbers and the parameters of the
 * contract (PilotPayParams), see docs/flight-contracts.md. Nothing is stored, so when a parameter changes (the manual flight) or the base income becomes
 * known (the journeys of a ShadowJet flight) the result changes with it.
 * <p>
 * What the pilot lacks (a currency) and the manual flight reduce the pay. The reductions are added up, they are not multiplied: coefficient =
 * max(10%, 100% - the sum). The calculation is in floating point and only the result is rounded (Math.round, half up).
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
     * What is taken off the pay: the reason and the percents.
     */
    public enum Deduction {
        NoTypeCurrency(NO_TYPE_CURRENCY_PERCENT),
        NoNightCurrency(NO_NIGHT_CURRENCY_PERCENT),
        CannotCarryPassengers(CANNOT_CARRY_PASSENGERS_PERCENT),
        Manual(MANUAL_PERCENT);

        private final int percent;

        Deduction(final int percent) {
            this.percent = percent;
        }

        public int getPercent() {
            return percent;
        }
    }

    /**
     * The deductions from the flight pay: the currencies and the manual flight.
     */
    public static List<Deduction> flightPayDeductions(final PilotPayParams params) {
        final List<Deduction> deductions = new ArrayList<>();
        if (!params.hasTypeCurrency()) {
            deductions.add(Deduction.NoTypeCurrency);
        }
        if (!params.hasNightCurrency()) {
            deductions.add(Deduction.NoNightCurrency);
        }
        if (!params.canCarryPassengers()) {
            deductions.add(Deduction.CannotCarryPassengers);
        }
        if (params.isManual()) {
            deductions.add(Deduction.Manual);
        }
        return deductions;
    }

    /**
     * The deductions from the income share: only the type currency and the manual flight.
     */
    public static List<Deduction> incomeShareDeductions(final PilotPayParams params) {
        final List<Deduction> deductions = new ArrayList<>();
        if (!params.hasTypeCurrency()) {
            deductions.add(Deduction.NoTypeCurrency);
        }
        if (params.isManual()) {
            deductions.add(Deduction.Manual);
        }
        return deductions;
    }

    /**
     * The coefficient of the flight pay in percents (10 to 100): the currencies and the manual flight count.
     */
    public static int flightPayCoefficientPercent(final PilotPayParams params) {
        return coefficient(flightPayDeductions(params));
    }

    /**
     * The coefficient of the income share in percents (10 to 100): only the type currency and the manual flight count.
     */
    public static int incomeShareCoefficientPercent(final PilotPayParams params) {
        return coefficient(incomeShareDeductions(params));
    }

    private static int coefficient(final List<Deduction> deductions) {
        return coefficient(deductions.stream().mapToInt(Deduction::getPercent).sum());
    }

    private static int coefficient(final int reductionsPercent) {
        return Math.max(MIN_COEFFICIENT_PERCENT, 100 - reductionsPercent);
    }

    /**
     * The flight pay: the base flight pay of the contract with the coefficient of its parameters.
     */
    public static int flightPay(final PilotContracts.Contract contract) {
        return (int) Math.round(contract.getBaseFlightPay() * (double) flightPayCoefficientPercent(contract.getPayParams()) / 100.0);
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
        return (int) Math.round((double) income * baseIncomeSharePercent(params.getExperienceLevel()) * incomeShareCoefficientPercent(params) / 10_000.0);
    }

    /**
     * Everything the pilot gets for the flight.
     */
    public static int total(final PilotContracts.Contract contract) {
        return flightPay(contract) + incomeShare(contract);
    }
}
