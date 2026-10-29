package net.simforge.airways2.world.computations;

import java.util.EnumMap;
import java.util.Map;

/**
 * The rates of an operator for the pay of its pilots: what is taken off the pay for a missing currency and for the manual flight, the least coefficient of the
 * pay and the base share of the income by the experience level. The operator decides them, the qualifications of the pilot only say whether the pilot meets a
 * criterion. The formula (PilotPay) is the same for all the operators. The base hourly rates of the types are also an affair of the operator (BusyBirdsPilotPay,
 * ShadowJetPilotPay).
 */
public final class PilotPayRates {
    /**
     * BusyBirds: -25% for a missing currency, -67% for the manual flight, the pay is not less than 10% of the base, the share is 0.5 to 7%.
     */
    public static final PilotPayRates BUSY_BIRDS = new PilotPayRates(25, 25, 25, 67, 10, new float[]{0.5f, 1.0f, 2.0f, 3.0f, 5.0f, 7.0f});

    /**
     * ShadowJet (2026-10-29): -10% for everything, -50% for the manual flight, the pay is not less than 10% of the base, the share is 0.1 to 3%.
     */
    public static final PilotPayRates SHADOW_JET = new PilotPayRates(10, 10, 10, 50, 10, new float[]{0.1f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f});

    private final Map<PilotPay.Deduction, Integer> deductionPercents = new EnumMap<>(PilotPay.Deduction.class);
    private final int minCoefficientPercent;
    private final float[] baseShareByLevel;

    private PilotPayRates(final int noTypeCurrencyPercent,
                          final int noNightCurrencyPercent,
                          final int cannotCarryPassengersPercent,
                          final int manualPercent,
                          final int minCoefficientPercent,
                          final float[] baseShareByLevel) {
        deductionPercents.put(PilotPay.Deduction.NoTypeCurrency, noTypeCurrencyPercent);
        deductionPercents.put(PilotPay.Deduction.NoNightCurrency, noNightCurrencyPercent);
        deductionPercents.put(PilotPay.Deduction.CannotCarryPassengers, cannotCarryPassengersPercent);
        deductionPercents.put(PilotPay.Deduction.Manual, manualPercent);
        this.minCoefficientPercent = minCoefficientPercent;
        this.baseShareByLevel = baseShareByLevel;
    }

    /**
     * What is taken off the pay for the reason, in percents.
     */
    public int deductionPercent(final PilotPay.Deduction deduction) {
        return deductionPercents.get(deduction);
    }

    /**
     * The pay is not less than this part of the base, in percents.
     */
    public int minCoefficientPercent() {
        return minCoefficientPercent;
    }

    /**
     * The base share of the income for an experience level of the pilot on the type of the aircraft, in percents.
     */
    public float baseIncomeSharePercent(final int experienceLevel) {
        if (experienceLevel < 0 || experienceLevel >= baseShareByLevel.length) {
            throw new IllegalArgumentException("experience level is from 0 to " + (baseShareByLevel.length - 1) + ": " + experienceLevel);
        }
        return baseShareByLevel[experienceLevel];
    }
}
