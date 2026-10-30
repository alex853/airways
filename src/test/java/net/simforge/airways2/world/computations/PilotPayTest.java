package net.simforge.airways2.world.computations;

import net.simforge.airways2.world.datamodel.PilotContracts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The actual pays in different situations: the base flight pay of a contract, the income of its flight (the income of the transport flight, it is not in the contract) and the
 * parameters of the contract give the flight pay and the income share.
 */
class PilotPayTest {
    private static int missionId = 0;

    // a contract and the income of its flight
    private record Case(PilotContracts.Contract contract, int income) {
    }

    private static int flightPay(final Case c) {
        return PilotPay.flightPay(c.contract());
    }

    private static int incomeShare(final Case c) {
        return PilotPay.incomeShare(c.income(), c.contract());
    }

    private static int total(final Case c) {
        return PilotPay.total(c.income(), c.contract());
    }

    private static Case contract(final int baseFlightPay, final int income, final int level,
                                 final boolean type, final boolean night, final boolean pax, final boolean manual) {
        final PilotPayParams params = PilotPayParams.none();
        params.setExperienceLevel(level);
        params.setTypeCurrency(type);
        params.setNightCurrency(night);
        params.setCanCarryPassengers(pax);
        params.setManual(manual);
        return new Case(new PilotContracts().create(++missionId, 1, 1, baseFlightPay, params), income);
    }

    private static Case allOk(final int baseFlightPay, final int income, final int level) {
        return contract(baseFlightPay, income, level, true, true, true, false);
    }

    // ---- the flight pay

    @Test
    void aPilotWithEverythingGetsTheBaseFlightPay() {
        assertEquals(400, flightPay(allOk(400, 0, 3)));
    }

    @Test
    void everyMissingCurrencyTakesAQuarter() {
        assertEquals(300, flightPay(contract(400, 0, 3, false, true, true, false)));
        assertEquals(300, flightPay(contract(400, 0, 3, true, false, true, false)));
        assertEquals(300, flightPay(contract(400, 0, 3, true, true, false, false)));
    }

    @Test
    void theReductionsAreAddedUpNotMultiplied() {
        assertEquals(200, flightPay(contract(400, 0, 3, false, false, true, false)));
        assertEquals(100, flightPay(contract(400, 0, 3, false, false, false, false)));
    }

    @Test
    void aManualFlightPaysAThird() {
        assertEquals(132, flightPay(contract(400, 0, 3, true, true, true, true)));
    }

    @Test
    void theFlightPayIsNotLessThanTenPercent() {
        // manual 67 + one miss 25 = 92 off, 8% is left, but 10% is the floor
        assertEquals(40, flightPay(contract(400, 0, 3, false, true, true, true)));
        assertEquals(40, flightPay(contract(400, 0, 0, false, false, false, true)));
    }

    @Test
    void theFlightPayIsRoundedHalfUp() {
        // 75% of 10 is 7.5
        assertEquals(8, flightPay(contract(10, 0, 0, false, true, true, false)));
        // 75% of 1 is 0.75
        assertEquals(1, flightPay(contract(1, 0, 0, false, true, true, false)));
        // 75% of 2 is 1.5
        assertEquals(2, flightPay(contract(2, 0, 0, false, true, true, false)));
        // 75% of 5 is 3.75
        assertEquals(4, flightPay(contract(5, 0, 0, false, true, true, false)));
        assertEquals(0, flightPay(allOk(0, 0, 0)));
    }

    @Test
    void theExperienceLevelDoesNotChangeTheFlightPay() {
        for (int level = 0; level <= 5; level++) {
            assertEquals(400, flightPay(allOk(400, 0, level)), "level " + level);
        }
    }

    // ---- the income share

    @Test
    void theIncomeShareIsTheLevelPercentOfTheBaseIncome() {
        // level III is 3%
        assertEquals(600, incomeShare(allOk(0, 20_000, 3)));
    }

    @Test
    void theTableOfTheBasePercentsByLevel() {
        final int[] expected = {50, 100, 200, 300, 500, 700};
        for (int level = 0; level <= 5; level++) {
            assertEquals(expected[level], incomeShare(allOk(0, 10_000, level)), "level " + level);
        }
        assertEquals(0.5f, PilotPay.baseIncomeSharePercent(0));
        assertEquals(7.0f, PilotPay.baseIncomeSharePercent(5));
        assertThrows(IllegalArgumentException.class, () -> PilotPay.baseIncomeSharePercent(6));
        assertThrows(IllegalArgumentException.class, () -> PilotPay.baseIncomeSharePercent(-1));
    }

    @Test
    void noTypeCurrencyTakesAQuarterOfTheShare() {
        assertEquals(450, incomeShare(contract(0, 20_000, 3, false, true, true, false)));
    }

    @Test
    void aManualFlightGetsAThirdOfTheShare() {
        assertEquals(198, incomeShare(contract(0, 20_000, 3, true, true, true, true)));
    }

    @Test
    void manualAndNoTypeCurrencyGoToTheFloor() {
        // 67 + 25 off: 8%, the floor is 10% of 600
        assertEquals(60, incomeShare(contract(0, 20_000, 3, false, true, true, true)));
    }

    @Test
    void nightAndPassengerCurrenciesDoNotTouchTheShare() {
        assertEquals(600, incomeShare(contract(0, 20_000, 3, true, false, false, false)));
    }

    @Test
    void noIncomeNoShare() {
        assertEquals(0, incomeShare(allOk(400, 0, 5)));
    }

    @Test
    void theShareFollowsTheIncomeOfTheFlight() {
        // the contract does not have the income, the income is the income of the transport flight: the same contract gives other shares for other incomes
        final PilotContracts.Contract contract = allOk(400, 0, 2).contract();
        assertEquals(0, PilotPay.incomeShare(0, contract));

        assertEquals(1000, PilotPay.incomeShare(50_000, contract));
        assertEquals(400, PilotPay.incomeShare(20_000, contract));
    }

    @Test
    void theShareIsRoundedHalfUp() {
        // 0.5% of 150 is 0.75, 1% of 150 is 1.5, 1% of 149 is 1.49
        assertEquals(1, incomeShare(allOk(0, 150, 0)));
        assertEquals(2, incomeShare(allOk(0, 150, 1)));
        assertEquals(1, incomeShare(allOk(0, 149, 1)));
    }

    // ---- the total and the changes of the parameters

    @Test
    void theTotalIsTheFlightPayAndTheShare() {
        // everything is fine, level III: 400 + 3% of 20000
        assertEquals(1000, total(allOk(400, 20_000, 3)));
        // no type currency: 300 + 450
        assertEquals(750, total(contract(400, 20_000, 3, false, true, true, false)));
        // manual: 132 + 198
        assertEquals(330, total(contract(400, 20_000, 3, true, true, true, true)));
        // a ferry leg without passengers, the base income is zero: only the flight pay
        assertEquals(400, total(allOk(400, 0, 3)));
        // a pilot without anything, level 0, a manual flight: 10% of 400 and 10% of 0.5% of 20000
        assertEquals(40 + 10, total(contract(400, 20_000, 0, false, false, false, true)));
    }

    @Test
    void theManualStartChangesThePayOfTheSameContract() {
        final PilotContracts.Contract contract = allOk(400, 20_000, 3).contract();
        assertEquals(1000, PilotPay.total(20_000, contract));

        final PilotPayParams params = contract.getPayParams();
        params.setManual(true);
        contract.setPayParams(params);

        assertEquals(330, PilotPay.total(20_000, contract));
        // the base flight pay and the level stay
        assertEquals(400, contract.getBaseFlightPay());
        assertEquals(3, contract.getPayParams().getExperienceLevel());
    }

    @Test
    void theCoefficientsAreExposed() {
        final PilotPayParams params = PilotPayParams.none();
        assertEquals(25, PilotPay.flightPayCoefficientPercent(params));
        assertEquals(75, PilotPay.incomeShareCoefficientPercent(params));

        params.setTypeCurrency(true);
        params.setNightCurrency(true);
        params.setCanCarryPassengers(true);
        assertEquals(100, PilotPay.flightPayCoefficientPercent(params));
        assertEquals(100, PilotPay.incomeShareCoefficientPercent(params));

        params.setManual(true);
        assertEquals(33, PilotPay.flightPayCoefficientPercent(params));
        assertEquals(33, PilotPay.incomeShareCoefficientPercent(params));
    }

    @Test
    void thePayByTheNumbersIsTheSameAsByTheContract() {
        final PilotContracts.Contract contract = contract(592, 10_484, 0, false, true, false, false).contract();

        assertEquals(PilotPay.flightPay(contract), PilotPay.flightPay(592, contract.getPayParams()));
        assertEquals(PilotPay.incomeShare(10_484, contract), PilotPay.incomeShare(10_484, contract.getPayParams()));
        // 592 * 50%, 10484 * 0.5% * 75%
        assertEquals(296, PilotPay.flightPay(592, contract.getPayParams()));
        assertEquals(39, PilotPay.incomeShare(10_484, contract.getPayParams()));
        assertEquals(0, PilotPay.incomeShare(0, contract.getPayParams()));
    }

    @Test
    void theEffectiveIncomeSharePercentIsTheBasePercentWithTheCoefficient() {
        final PilotPayParams params = PilotPayParams.none();
        params.setExperienceLevel(3);
        params.setTypeCurrency(true);
        assertEquals(3.0, PilotPay.effectiveIncomeSharePercent(params), 0.0001);

        params.setTypeCurrency(false);
        assertEquals(2.25, PilotPay.effectiveIncomeSharePercent(params), 0.0001);

        params.setExperienceLevel(0);
        assertEquals(0.375, PilotPay.effectiveIncomeSharePercent(params), 0.0001);
    }
}
