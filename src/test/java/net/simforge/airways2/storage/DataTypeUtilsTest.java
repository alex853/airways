package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DataTypeUtilsTest {
    @Test
    public void floatToU16When1to1000() {
        assertEquals(0, DataTypeUtils.floatToU16When1to1000(0f));
        assertEquals(500, DataTypeUtils.floatToU16When1to1000(0.5f));
        assertEquals(1000, DataTypeUtils.floatToU16When1to1000(1f));
        assertEquals(2000, DataTypeUtils.floatToU16When1to1000(2f));

        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatToU16When1to1000(-1f));
        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatToU16When1to1000(66f));
    }

    @Test
    public void floatFromU16When1to1000() {
        assertEquals(0f, DataTypeUtils.floatFromU16When1to1000(0));
        assertEquals(0.5f, DataTypeUtils.floatFromU16When1to1000(500));
        assertEquals(1f, DataTypeUtils.floatFromU16When1to1000(1000));
        assertEquals(2f, DataTypeUtils.floatFromU16When1to1000(2000));

        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatToU16When1to1000(-1000));
        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatToU16When1to1000(66000));
    }

    @Test
    public void floatToU16When1to65535() {
        assertEquals(0, DataTypeUtils.floatToU16When1to65535(0f));
        assertEquals(32768, DataTypeUtils.floatToU16When1to65535(0.5f));
        assertEquals(65535, DataTypeUtils.floatToU16When1to65535(1f));

        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatToU16When1to65535(-0.001f));
        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatToU16When1to65535(1.001f));
    }

    @Test
    public void floatFromU16When1to65535() {
        assertEquals(0f, DataTypeUtils.floatFromU16When1to65535(0));
        assertEquals(0.5f, DataTypeUtils.floatFromU16When1to65535(32768), 0.00001f);
        assertEquals(1f, DataTypeUtils.floatFromU16When1to65535(65535));

        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatFromU16When1to65535(-1));
        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatFromU16When1to65535(66000));
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    @Test
    public void floatToU16When1byteInt1byteFraction() {
        assertEquals(0, DataTypeUtils.floatToU16When1byteInt1byteFraction(0f));
        assertEquals(128, DataTypeUtils.floatToU16When1byteInt1byteFraction(0.5f));
        assertEquals(1*256, DataTypeUtils.floatToU16When1byteInt1byteFraction(1f));
        assertEquals(1*256+128, DataTypeUtils.floatToU16When1byteInt1byteFraction(1.5f));
        assertEquals(255*256, DataTypeUtils.floatToU16When1byteInt1byteFraction(255f));

        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatToU16When1byteInt1byteFraction(-0.001f));
        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatToU16When1byteInt1byteFraction(256f));
    }

    @Test
    public void floatFromU16When1byteInt1byteFraction() {
        assertEquals(0f, DataTypeUtils.floatFromU16When1byteInt1byteFraction(0));
        assertEquals(0.5f, DataTypeUtils.floatFromU16When1byteInt1byteFraction(128));
        assertEquals(1f, DataTypeUtils.floatFromU16When1byteInt1byteFraction(256));
        assertEquals(1.5f, DataTypeUtils.floatFromU16When1byteInt1byteFraction(256+128));
        assertEquals(255f, DataTypeUtils.floatFromU16When1byteInt1byteFraction(255*256));

        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatFromU16When1byteInt1byteFraction(-1));
        assertThrows(IllegalArgumentException.class, () -> DataTypeUtils.floatFromU16When1byteInt1byteFraction(66536));
    }
}
