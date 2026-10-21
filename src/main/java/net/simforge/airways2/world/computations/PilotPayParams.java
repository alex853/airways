package net.simforge.airways2.world.computations;

import net.simforge.airways2.storage.DataType;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The parameters of the pay of a pilot for a flight, in one byte. They are what the pay depends on, see PilotPay: the currency of the pilot on the type
 * of the aircraft, the experience level, and whether the flight is manual. The object takes the byte (the one which is stored in the contract), it can
 * be changed and gives the byte back, so the same parameters can be shown anywhere.
 * <p>
 * The bits of the byte:
 * <pre>
 *   bit 0  type currency        (set: the pilot is current on the type)
 *   bit 1  night currency       (set: the pilot is current for the night flying)
 *   bit 2  can carry passengers (set: the pilot is current for carrying passengers)
 *   bit 3  manual               (set: the flight is manual, nothing tracks it)
 *   bit 4  free
 *   bits 5-7  the experience level on the type, 0 to 5
 * </pre>
 * A setter changes its own bits only, so the free bit stays as it is.
 */
public final class PilotPayParams {
    public static final int MAX_EXPERIENCE_LEVEL = 5;

    private static final int TYPE_CURRENCY = 1;
    private static final int NIGHT_CURRENCY = 1 << 1;
    private static final int CAN_CARRY_PASSENGERS = 1 << 2;
    private static final int MANUAL = 1 << 3;
    private static final int LEVEL_SHIFT = 5;
    private static final int LEVEL_MASK = 0b111 << LEVEL_SHIFT;

    private int bits;

    private PilotPayParams(final int bits) {
        this.bits = bits;
    }

    /**
     * Nothing is set: no currency, not manual, the experience level 0.
     */
    public static PilotPayParams none() {
        return new PilotPayParams(0);
    }

    /**
     * The parameters from the byte (0 to 255, as it is stored in the contract).
     */
    public static PilotPayParams of(final int value) {
        checkArgument(DataType.Unsigned8bit.contains(value), "the parameters are one byte: %s", value);
        return new PilotPayParams(value);
    }

    /**
     * The byte for the contract, 0 to 255.
     */
    public int toByte() {
        return bits;
    }

    public boolean hasTypeCurrency() {
        return (bits & TYPE_CURRENCY) != 0;
    }

    public void setTypeCurrency(final boolean value) {
        set(TYPE_CURRENCY, value);
    }

    public boolean hasNightCurrency() {
        return (bits & NIGHT_CURRENCY) != 0;
    }

    public void setNightCurrency(final boolean value) {
        set(NIGHT_CURRENCY, value);
    }

    public boolean canCarryPassengers() {
        return (bits & CAN_CARRY_PASSENGERS) != 0;
    }

    public void setCanCarryPassengers(final boolean value) {
        set(CAN_CARRY_PASSENGERS, value);
    }

    public boolean isManual() {
        return (bits & MANUAL) != 0;
    }

    public void setManual(final boolean value) {
        set(MANUAL, value);
    }

    /**
     * The experience level on the type, 0 to 5.
     */
    public int getExperienceLevel() {
        return (bits & LEVEL_MASK) >> LEVEL_SHIFT;
    }

    public void setExperienceLevel(final int level) {
        checkArgument(level >= 0 && level <= MAX_EXPERIENCE_LEVEL, "the experience level is from 0 to %s: %s", MAX_EXPERIENCE_LEVEL, level);
        bits = (bits & ~LEVEL_MASK) | (level << LEVEL_SHIFT);
    }

    private void set(final int mask, final boolean value) {
        bits = value ? bits | mask : bits & ~mask;
    }

    @Override
    public String toString() {
        return String.format("{ type: %s, night: %s, pax: %s, manual: %s, level: %s }",
                hasTypeCurrency(), hasNightCurrency(), canCarryPassengers(), isManual(), getExperienceLevel());
    }
}
