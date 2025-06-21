package net.simforge.airways2.storage;

import static com.google.common.base.Preconditions.checkArgument;

public class DataTypeUtils {
    public static int floatToU16When1to1000(final float value) {
        checkArgument(0f <= value && value <= 65.5f);
        return Math.round(value * 1000f);
    }

    public static float floatFromU16When1to1000(final int value) {
        checkArgument(0 <= value && value <= 65500);
        return value / 1000f;
    }

    public static int floatToU16When1to65535(final float value) {
        checkArgument(0f <= value && value <= 1f);
        return Math.round(value * 65535f);
    }

    public static float floatFromU16When1to65535(final int value) {
        checkArgument(0 <= value && value <= 65535);
        return value / 65535f;
    }

    public static int floatToU16When1byteInt1byteFraction(final float value) {
        checkArgument(0f <= value & value < 256);
        final int integerPart = (int) value;
        final float fractionalPart = value - integerPart;
        return integerPart * 256 + (int) Math.floor(fractionalPart * 256);
    }

    public static float floatFromU16When1byteInt1byteFraction(final int value) {
        checkArgument(0 <= value & value < 65536);
        final int integerPart = value / 256;
        final int fractionalPart = value % 256;
        return integerPart + (fractionalPart / 256f);
    }
}
