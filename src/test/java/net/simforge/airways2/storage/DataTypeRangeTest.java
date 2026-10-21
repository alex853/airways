package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The range of the integer data types, the one place where the limits are written.
 */
class DataTypeRangeTest {
    @Test
    void theRangesOfTheIntegerTypes() {
        assertEquals(255, DataType.Unsigned8bit.maxValue());
        assertEquals(65_535, DataType.Unsigned16bit.maxValue());
        assertEquals(16_777_215, DataType.Unsigned24bit.maxValue());
        assertEquals(Integer.MAX_VALUE, DataType.Signed32bit.maxValue());
        assertEquals(549_755_813_887L, DataType.Signed40bit.maxValue());
        assertEquals(0, DataType.Unsigned8bit.minValue());
        assertEquals(0, DataType.Unsigned16bit.minValue());
        assertEquals(0, DataType.Unsigned24bit.minValue());
        assertEquals(Integer.MIN_VALUE, DataType.Signed32bit.minValue());
        assertEquals(-549_755_813_888L, DataType.Signed40bit.minValue());
    }

    @Test
    void aValueIsInTheRangeOrNot() {
        assertTrue(DataType.Unsigned16bit.contains(0));
        assertTrue(DataType.Unsigned16bit.contains(65_535));
        assertFalse(DataType.Unsigned16bit.contains(65_536));
        assertFalse(DataType.Unsigned16bit.contains(-1));
        assertTrue(DataType.Unsigned24bit.contains(16_777_215));
        assertFalse(DataType.Unsigned24bit.contains(16_777_216));
        assertTrue(DataType.Signed40bit.contains(-549_755_813_888L));
        assertFalse(DataType.Signed40bit.contains(-549_755_813_889L));
    }

    @Test
    void theTypesWithoutAnIntegerRangeSayNoRange() {
        assertTrue(DataType.Unsigned8bit.isInteger());
        assertFalse(DataType.Float.isInteger());
        assertFalse(DataType.PlainString.isInteger());
        assertThrows(IllegalStateException.class, DataType.Float::maxValue);
        assertThrows(IllegalStateException.class, () -> DataType.PlainString.contains(1));
    }
}
