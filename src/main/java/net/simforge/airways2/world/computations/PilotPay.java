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
 * max(the least coefficient, 100% - the sum). How much is taken off and the least coefficient are the rates of the operator (PilotPayRates); the methods without
 * the rates use those of BusyBirds. The calculation is in floating point and only the result is rounded (Math.round, half up).
 * <p>
 * The income share is the base percent of the experience level of the pilot (the level is in the parameters) of the base income of the contract.
 */
public final class PilotPay {
    private PilotPay() {
    }

    /**
     * The base share of the income for an experience level of the pilot on the type of the aircraft (the rates of BusyBirds).
     */
    public static float baseIncomeSharePercent(final int experienceLevel) {
        return baseIncomeSharePercent(experienceLevel, PilotPayRates.BUSY_BIRDS);
    }

    public static float baseIncomeSharePercent(final int experienceLevel, final PilotPayRates rates) {
        return rates.baseIncomeSharePercent(experienceLevel);
    }

    /**
     * What is taken off the pay: the reason. How many percents it is, is in the rates of the operator.
     */
    public enum Deduction {
        NoTypeCurrency,
        NoNightCurrency,
        CannotCarryPassengers,
        Manual
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
     * The coefficient of the flight pay in percents (the least to 100): the currencies and the manual flight count.
     */
    public static int flightPayCoefficientPercent(final PilotPayParams params) {
        return flightPayCoefficientPercent(params, PilotPayRates.BUSY_BIRDS);
    }

    public static int flightPayCoefficientPercent(final PilotPayParams params, final PilotPayRates rates) {
        return coefficient(flightPayDeductions(params), rates);
    }

    /**
     * The coefficient of the income share in percents (the least to 100): only the type currency and the manual flight count.
     */
    public static int incomeShareCoefficientPercent(final PilotPayParams params) {
        return incomeShareCoefficientPercent(params, PilotPayRates.BUSY_BIRDS);
    }

    public static int incomeShareCoefficientPercent(final PilotPayParams params, final PilotPayRates rates) {
        return coefficient(incomeShareDeductions(params), rates);
    }

    private static int coefficient(final List<Deduction> deductions, final PilotPayRates rates) {
        final int reductionsPercent = deductions.stream().mapToInt(rates::deductionPercent).sum();
        return Math.max(rates.minCoefficientPercent(), 100 - reductionsPercent);
    }

    /**
     * The flight pay: the base flight pay of the contract with the coefficient of its parameters.
     */
    public static int flightPay(final PilotContracts.Contract contract) {
        return flightPay(contract.getBaseFlightPay(), contract.getPayParams());
    }

    /**
     * The flight pay by the base flight pay and the parameters (the quotation of a plan, before there is a contract).
     */
    public static int flightPay(final int baseFlightPay, final PilotPayParams params) {
        return flightPay(baseFlightPay, params, PilotPayRates.BUSY_BIRDS);
    }

    public static int flightPay(final int baseFlightPay, final PilotPayParams params, final PilotPayRates rates) {
        return (int) Math.round(baseFlightPay * (double) flightPayCoefficientPercent(params, rates) / 100.0);
    }

    /**
     * The income share: the base percent of the experience level of the base income of the contract, with the coefficient of its parameters. It is zero
     * while the base income is zero (a flight without passengers, or the journeys are not known yet).
     */
    public static int incomeShare(final PilotContracts.Contract contract) {
        return incomeShare(contract.getBaseIncome(), contract.getPayParams());
    }

    /**
     * The income share by the base income and the parameters (the quotation of a plan, before there is a contract). It is zero for a zero income.
     */
    public static int incomeShare(final int baseIncome, final PilotPayParams params) {
        return incomeShare(baseIncome, params, PilotPayRates.BUSY_BIRDS);
    }

    public static int incomeShare(final int baseIncome, final PilotPayParams params, final PilotPayRates rates) {
        if (baseIncome <= 0) {
            return 0;
        }
        return (int) Math.round((double) baseIncome * baseIncomeSharePercent(params.getExperienceLevel(), rates) * incomeShareCoefficientPercent(params, rates) / 10_000.0);
    }

    /**
     * The percent of the income of the mission which the pilot gets: the base percent of the experience level with the coefficient of the parameters.
     */
    public static double effectiveIncomeSharePercent(final PilotPayParams params) {
        return effectiveIncomeSharePercent(params, PilotPayRates.BUSY_BIRDS);
    }

    public static double effectiveIncomeSharePercent(final PilotPayParams params, final PilotPayRates rates) {
        return baseIncomeSharePercent(params.getExperienceLevel(), rates) * incomeShareCoefficientPercent(params, rates) / 100.0;
    }

    /**
     * Everything the pilot gets for the flight.
     */
    public static int total(final PilotContracts.Contract contract) {
        return flightPay(contract) + incomeShare(contract);
    }
}
