package net.simforge.airways2.storage;

/**
 * Half precision float (IEEE 754 binary16): 1 sign bit, 5 bits of exponent, 10 bits of mantissa. The values go from -65504 to 65504, the step
 * grows with the value: it is 1/1024 of the power of two below the value, e.g. 0.5 between 512 and 1024, 1 between 1024 and 2048, 32 between
 * 32768 and 65536. The conversion is done by arithmetic, the value is rounded to the nearest representable one (half up).
 */
final class Half {
    static final float MAX_VALUE = 65504.0f;

    private Half() {
    }

    /**
     * @param value a finite value, not more than {@link #MAX_VALUE} by the absolute value
     */
    static int toBits(final float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || Math.abs(value) > MAX_VALUE) {
            throw new IllegalArgumentException("Half float value out of range [-65504 .. 65504]: " + value);
        }

        final int sign = (Float.floatToRawIntBits(value) >>> 31) != 0 ? 0x8000 : 0;
        final double abs = Math.abs((double) value);
        if (abs == 0.0) {
            return sign;
        }

        final int exponent = Math.getExponent(abs);
        if (exponent < -14) {
            // subnormal numbers (and the smallest normal one after the rounding): the value is a number of 2^-24
            final long m = Math.round(abs * 16777216.0);
            return sign | (int) m; // m is up to 1024, 1024 is the smallest normal number 2^-14
        }

        long mantissa = Math.round(abs / Math.pow(2.0, exponent - 10)); // from 1024 to 2048
        int e = exponent;
        if (mantissa == 2048) {
            mantissa = 1024;
            e++;
        }
        return sign | ((e + 15) << 10) | (int) (mantissa - 1024);
    }

    static float fromBits(final int bits) {
        final int exponent = (bits >> 10) & 0x1F;
        final int mantissa = bits & 0x3FF;
        final float magnitude;
        if (exponent == 0) {
            magnitude = (float) (mantissa * Math.pow(2.0, -24));
        } else if (exponent == 31) {
            magnitude = mantissa == 0 ? Float.POSITIVE_INFINITY : Float.NaN;
        } else {
            magnitude = (float) ((1024 + mantissa) * Math.pow(2.0, exponent - 25));
        }
        return (bits & 0x8000) != 0 ? -magnitude : magnitude;
    }
}
