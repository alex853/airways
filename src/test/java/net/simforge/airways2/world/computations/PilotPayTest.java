package net.simforge.airways2.world.computations;

import net.simforge.airways2.world.datamodel.PilotContracts;
import net.simforge.airways2.world.datamodel.PilotContracts.Penalty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What the pilot gets for a flight: the flight pay (base flight pay with the penalties) and the income share (the base percent of the income with the
 * penalties). The penalties are added up and the result is not lower than 10% of the base.
 */
class PilotPayTest {
    private PilotContracts contracts;
    private int nextMissionId;

    @BeforeEach
    void beforeEach() {
        contracts = new PilotContracts();
        nextMissionId = 1;
    }

    private PilotContracts.Contract contract(final int baseFlightPay, final float baseIncomeSharePercent, final Penalty... penalties) {
        final PilotContracts.Contract contract = contracts.create(nextMissionId++, 3, 7, baseFlightPay, baseIncomeSharePercent);
        for (final Penalty penalty : penalties) {
            contract.setPenalty(penalty, true);
        }
        return contract;
    }

    // ---- the flight pay

    @Test
    void withoutPenaltiesThePilotGetsTheWholeFlightPay() {
        final PilotContracts.Contract contract = contract(400, 3.0f);

        assertEquals(100, PilotPay.flightPayCoefficientPercent(contract));
        assertEquals(400, PilotPay.flightPay(contract));
    }

    @Test
    void everyCurrencyPenaltyTakesAQuarterOfTheFlightPay() {
        assertEquals(300, PilotPay.flightPay(contract(400, 3.0f, Penalty.NoTypeCurrency)));
        assertEquals(300, PilotPay.flightPay(contract(400, 3.0f, Penalty.NoNightCurrency)));
        assertEquals(300, PilotPay.flightPay(contract(400, 3.0f, Penalty.NoCarryPassengers)));
    }

    @Test
    void thePenaltiesAreAddedUpNotMultiplied() {
        assertEquals(200, PilotPay.flightPay(contract(400, 3.0f, Penalty.NoTypeCurrency, Penalty.NoNightCurrency)));
        // three penalties: 100 - 75 = 25%, not 0.75 * 0.75 * 0.75 = 42%
        final PilotContracts.Contract three = contract(400, 3.0f, Penalty.NoTypeCurrency, Penalty.NoNightCurrency, Penalty.NoCarryPassengers);
        assertEquals(25, PilotPay.flightPayCoefficientPercent(three));
        assertEquals(100, PilotPay.flightPay(three));
    }

    @Test
    void theManualFlightTakesTwoThirdsOfTheFlightPay() {
        final PilotContracts.Contract contract = contract(400, 3.0f, Penalty.Manual);

        assertEquals(33, PilotPay.flightPayCoefficientPercent(contract));
        assertEquals(132, PilotPay.flightPay(contract));
    }

    @Test
    void theManualFlightWithAnyCurrencyPenaltyDropsToTheMinimum() {
        // 100 - 67 - 25 = 8%, the minimum is 10%
        final PilotContracts.Contract one = contract(400, 3.0f, Penalty.Manual, Penalty.NoTypeCurrency);
        assertEquals(10, PilotPay.flightPayCoefficientPercent(one));
        assertEquals(40, PilotPay.flightPay(one));

        // the minimum is the floor, more penalties do not go lower
        final PilotContracts.Contract all = contract(400, 3.0f, Penalty.Manual, Penalty.NoTypeCurrency, Penalty.NoNightCurrency, Penalty.NoCarryPassengers);
        assertEquals(10, PilotPay.flightPayCoefficientPercent(all));
        assertEquals(40, PilotPay.flightPay(all));
    }

    @Test
    void theFlightPayIsRoundedHalfUp() {
        assertEquals(250, PilotPay.flightPay(contract(333, 1.0f, Penalty.NoTypeCurrency)));   // 249.75
        assertEquals(1, PilotPay.flightPay(contract(5, 1.0f, Penalty.Manual, Penalty.NoTypeCurrency))); // 0.5
        assertEquals(0, PilotPay.flightPay(contract(1, 1.0f, Penalty.Manual)));                  // 0.33
        assertEquals(0, PilotPay.flightPay(contract(0, 1.0f)));
    }

    @Test
    void aBigFlightPayDoesNotOverflow() {
        assertEquals(PilotContracts.MAX_FLIGHT_PAY, PilotPay.flightPay(contract(PilotContracts.MAX_FLIGHT_PAY, 1.0f)));
        assertEquals(5_536_481, PilotPay.flightPay(contract(PilotContracts.MAX_FLIGHT_PAY, 1.0f, Penalty.Manual)));
    }

    // ---- the income share

    @Test
    void theIncomeShareIsThePercentOfTheIncome() {
        assertEquals(600, PilotPay.incomeShare(contract(400, 3.0f), 20_000));
        assertEquals(100, PilotPay.incomeShare(contract(400, 0.5f), 20_000));
        assertEquals(1_400, PilotPay.incomeShare(contract(400, 7.0f), 20_000));
        // the biggest base share
        assertEquals(255, PilotPay.incomeShare(contract(400, 25.5f), 1_000));
        assertEquals(7, PilotPay.incomeShare(contract(400, 0.7f), 1_000));
    }

    @Test
    void onlyTheTypeCurrencyAndTheManualFlightChangeTheIncomeShare() {
        assertEquals(600, PilotPay.incomeShare(contract(400, 3.0f), 20_000));
        assertEquals(450, PilotPay.incomeShare(contract(400, 3.0f, Penalty.NoTypeCurrency), 20_000));
        assertEquals(198, PilotPay.incomeShare(contract(400, 3.0f, Penalty.Manual), 20_000));
        // the night currency and the passengers do not touch it
        assertEquals(600, PilotPay.incomeShare(contract(400, 3.0f, Penalty.NoNightCurrency), 20_000));
        assertEquals(600, PilotPay.incomeShare(contract(400, 3.0f, Penalty.NoCarryPassengers), 20_000));
        assertEquals(600, PilotPay.incomeShare(contract(400, 3.0f, Penalty.NoNightCurrency, Penalty.NoCarryPassengers), 20_000));

        assertEquals(100, PilotPay.incomeShareCoefficientPercent(contract(400, 3.0f, Penalty.NoNightCurrency, Penalty.NoCarryPassengers)));
        assertEquals(75, PilotPay.incomeShareCoefficientPercent(contract(400, 3.0f, Penalty.NoTypeCurrency)));
        assertEquals(33, PilotPay.incomeShareCoefficientPercent(contract(400, 3.0f, Penalty.Manual)));
    }

    @Test
    void theManualFlightWithoutTheTypeCurrencyDropsTheIncomeShareToTheMinimum() {
        final PilotContracts.Contract contract = contract(400, 3.0f, Penalty.Manual, Penalty.NoTypeCurrency);

        assertEquals(10, PilotPay.incomeShareCoefficientPercent(contract));
        assertEquals(60, PilotPay.incomeShare(contract, 20_000));
    }

    @Test
    void aFlightWithoutIncomeHasNoIncomeShare() {
        // a ferry leg carries nobody
        assertEquals(0, PilotPay.incomeShare(contract(400, 3.0f), 0));
        assertEquals(0, PilotPay.incomeShare(contract(400, 3.0f), -5));
        assertEquals(0, PilotPay.incomeShare(contract(400, 0.0f), 20_000));
    }

    @Test
    void theIncomeShareIsRoundedHalfUp() {
        assertEquals(6, PilotPay.incomeShare(contract(0, 0.5f), 1_234));                              // 6.17
        assertEquals(7, PilotPay.incomeShare(contract(0, 0.5f), 1_300));                              // 6.5
        assertEquals(5, PilotPay.incomeShare(contract(0, 0.5f, Penalty.NoTypeCurrency), 1_234));      // 4.63
        assertEquals(0, PilotPay.incomeShare(contract(0, 0.5f), 99));                                 // 0.495
    }

    @Test
    void theBaseSharesOfTheLevelsAre_half_1_2_3_5_7_percents() {
        assertEquals(0.5f, PilotPay.baseIncomeSharePercent(0), 0.0f);
        assertEquals(1.0f, PilotPay.baseIncomeSharePercent(1), 0.0f);
        assertEquals(2.0f, PilotPay.baseIncomeSharePercent(2), 0.0f);
        assertEquals(3.0f, PilotPay.baseIncomeSharePercent(3), 0.0f);
        assertEquals(5.0f, PilotPay.baseIncomeSharePercent(4), 0.0f);
        assertEquals(7.0f, PilotPay.baseIncomeSharePercent(5), 0.0f);
        assertThrows(IllegalArgumentException.class, () -> PilotPay.baseIncomeSharePercent(6));
        assertThrows(IllegalArgumentException.class, () -> PilotPay.baseIncomeSharePercent(-1));
    }

    @Test
    void theIncomeShareByTheLevelOfAnIncomeOf10000() {
        final int[] expected = {50, 100, 200, 300, 500, 700};
        for (int level = 0; level <= 5; level++) {
            final PilotContracts.Contract contract = contract(0, PilotPay.baseIncomeSharePercent(level));
            assertEquals(expected[level], PilotPay.incomeShare(contract, 10_000), "level " + level);
        }
    }

    // ---- everything the pilot gets, and the events which change it

    @Test
    void theTotalIsTheFlightPayAndTheIncomeShare() {
        assertEquals(1_000, PilotPay.total(contract(400, 3.0f), 20_000));                                                      // 400 + 600
        assertEquals(100, PilotPay.total(contract(400, 3.0f, Penalty.Manual, Penalty.NoTypeCurrency), 20_000));              // 40 + 60
        assertEquals(400, PilotPay.total(contract(400, 3.0f), 0));                                                             // a ferry leg
    }

    @Test
    void theManualStartChangesWhatThePilotGetsWithoutChangingTheBaseNumbers() {
        final PilotContracts.Contract contract = contract(400, 3.0f, Penalty.NoNightCurrency);
        assertEquals(300 + 600, PilotPay.total(contract, 20_000));

        // the event: the pilot starts the flight manually
        contract.setPenalty(Penalty.Manual, true);

        assertEquals(400, contract.getBaseFlightPay());
        assertEquals(3.0f, contract.getBaseIncomeSharePercent(), 0.0f);
        assertEquals(40 + 198, PilotPay.total(contract, 20_000)); // 100 - 25 - 67 = 8% -> 10% of 400, and 33% of 600
    }

    @Test
    void theIncomeOfAShadowJetFlightIsKnownLaterAndTheShareFollowsIt() {
        final PilotContracts.Contract contract = contract(400, 1.0f);

        // at the booking there are no journeys yet, at blocks off the journeys on board pay 15 000
        assertEquals(400, PilotPay.total(contract, 0));
        assertEquals(400 + 150, PilotPay.total(contract, 15_000));
    }
}
