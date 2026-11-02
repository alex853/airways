package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.computations.PilotPayRates;
import net.simforge.airways2.worldbuilder.World25;

import java.util.Optional;

/**
 * Which operator pays its pilots by which rates (PilotPayRates). The formula of the pay is the same (PilotPay), the rates are of the operator.
 */
public final class OperatorPay {
    private OperatorPay() {
    }

    /**
     * The flight pay before the coefficients of the pilot for a time in the air and on the ground (blocks off to blocks on): the base hourly rate of the type of the operator
     * multiplied by the time. The contract has it for the planned time (the estimate of the booking), the pay of a finished flight takes it for the actual time.
     */
    public static int baseFlightPay(final int aircraftOperatorId, final String aircraftTypeIcao, final int durationSeconds) {
        return aircraftOperatorId == World25.ShadowJetOperatorId
                ? ShadowJetPilotPay.baseFlightPay(aircraftTypeIcao, durationSeconds)
                : BusyBirdsPilotPay.baseFlightPay(aircraftTypeIcao, durationSeconds);
    }

    /**
     * The rates of the operator, empty for an operator which does not pay its pilots (no contract, no pay).
     */
    public static Optional<PilotPayRates> ratesOf(final int aircraftOperatorId) {
        if (aircraftOperatorId == World25.BusyBirdsOperatorId) {
            return Optional.of(PilotPayRates.BUSY_BIRDS);
        }
        if (aircraftOperatorId == World25.ShadowJetOperatorId) {
            return Optional.of(PilotPayRates.SHADOW_JET);
        }
        return Optional.empty();
    }
}
