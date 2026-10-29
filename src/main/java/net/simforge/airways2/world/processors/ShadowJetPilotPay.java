package net.simforge.airways2.world.processors;

/**
 * The base pay of a ShadowJet pilot: the base hourly rate of the aircraft type. What the pilot gets (the flight pay and the income share, with the deductions)
 * is calculated by PilotPay with the rates PilotPayRates.SHADOW_JET, see docs/flight-contracts.md. Money is integer, there are no fractions.
 */
public final class ShadowJetPilotPay {
    // 2026-10-29: the rate is v100 for every type
    // TODO the families of types (an Airbus 320 has several ICAO codes) and the rates by the family
    private static final int BASE_HOURLY_RATE = 100;

    private ShadowJetPilotPay() {
    }

    public static int baseHourlyRate(final String aircraftTypeIcao) {
        return BASE_HOURLY_RATE;
    }

    /**
     * The flight pay before the coefficients of the pilot: the base hourly rate of the type multiplied by the planned duration of the flight, rounded.
     */
    public static int baseFlightPay(final String aircraftTypeIcao, final int plannedDurationSeconds) {
        return (int) Math.round(baseHourlyRate(aircraftTypeIcao) * plannedDurationSeconds / 3600.0);
    }
}
