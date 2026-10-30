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
