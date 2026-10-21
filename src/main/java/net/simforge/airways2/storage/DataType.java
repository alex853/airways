package net.simforge.airways2.storage;

import static com.google.common.base.Preconditions.checkState;

public enum DataType {
    Signed32bit(Integer.MIN_VALUE, Integer.MAX_VALUE), // just as usual 'int'
    Signed40bit(-(1L << 39), (1L << 39) - 1), // 5 bytes signed integer, from -549 755 813 888 to 549 755 813 887 (money: balances and amounts), read and written by getAsLong and setLong
    Unsigned24bit(0, 16_777_215), // 3 bytes unsigned integer
    Unsigned16bit(0, 65_535), // 2 bytes unsigned integer
    Unsigned8bit(0, 255), // 1 byte unsiged integer, from 0 to 255
    Float,
    LatLong24bit, // accuracy 0.0001, this means 11 meters step on equator
    LatLong16bit, // accuracy 0.01, this means 1.1 kilometer step on equator
    Tenths8bit, // a decimal with one digit after the point in one byte: from 0.0 to 25.5, accuracy 0.1; it is stored as an integer from 0 to 255 (3.4 is 34)
    Float16bit, // half precision float in two bytes (IEEE 754 binary16): from -65504 to 65504, the accuracy gets worse as the value grows, 1 for values from 1024 to 2048
    PlainString, // default length is 20 bytes, max length is 254 bytes
//    PackedTo6BitsString
    ;

    private final boolean integer;
    private final long minValue;
    private final long maxValue;

    DataType() {
        this.integer = false;
        this.minValue = 0;
        this.maxValue = 0;
    }

    DataType(final long minValue, final long maxValue) {
        this.integer = true;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    /**
     * Whether the type is an integer one: it has a range, see minValue, maxValue and contains.
     */
    public boolean isInteger() {
        return integer;
    }

    /**
     * The smallest value of an integer type (zero for the unsigned ones).
     */
    public long minValue() {
        checkState(integer, "%s is not an integer type", this);
        return minValue;
    }

    /**
     * The biggest value of an integer type, for instance 65 535 for Unsigned16bit and 16 777 215 for Unsigned24bit.
     */
    public long maxValue() {
        checkState(integer, "%s is not an integer type", this);
        return maxValue;
    }

    /**
     * Whether the value fits the integer type.
     */
    public boolean contains(final long value) {
        return minValue() <= value && value <= maxValue;
    }
}
