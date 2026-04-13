package net.simforge.airways2.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CabinLayoutTest {
    @Test
    public void test_creation_Y160() {
        final CabinLayout layout = CabinLayout.Y(160);
        final int stored = layout.toSigned32bit();
        final CabinLayout restored = CabinLayout.fromSigned32bit(stored);
        assertEquals(layout, restored);
    }

    @Test
    public void test_creation_F0J0W0Y0() {
        final CabinLayout layout = CabinLayout.FJWY(0, 0, 0, 0);
        final int stored = layout.toSigned32bit();
        final CabinLayout restored = CabinLayout.fromSigned32bit(stored);
        assertEquals(layout, restored);
    }

    @Test
    public void test_creation_F31J127W255Y1023() {
        final CabinLayout layout = CabinLayout.FJWY(31, 127, 255, 1023);
        final int stored = layout.toSigned32bit();
        final CabinLayout restored = CabinLayout.fromSigned32bit(stored);
        assertEquals(layout, restored);
    }

    @Test
    public void test_creation_F0J0W0Y0_toString() {
        final CabinLayout layout = CabinLayout.FJWY(0, 0, 0, 0);
        assertEquals("", layout.toString());
    }

    @Test
    public void test_creation_F0J10W0Y0_toString() {
        final CabinLayout layout = CabinLayout.FJWY(0, 10, 0, 0);
        assertEquals("J10", layout.toString());
    }

    @Test
    public void test_creation_Y100() {
        final CabinLayout layout = CabinLayout.FJWY(0, 0, 0, 100);
        assertEquals("Y100", layout.toString());
    }

    @Test
    public void test_parse_empty() {
        assertEquals(CabinLayout.NOBODY, CabinLayout.parseString(""));
        assertEquals(CabinLayout.NOBODY, CabinLayout.parseString(" "));
    }

    @Test
    public void test_parse_onlyEconomy() {
        assertEquals(
                CabinLayout.FJWY(0, 0, 0, 100),
                CabinLayout.parseString("Y100")
        );
    }

    @Test
    public void test_parse_businessAndEconomy() {
        assertEquals(
                CabinLayout.FJWY(0, 10, 0, 150),
                CabinLayout.parseString("J10/Y150")
        );
    }

    @Test
    public void test_parse_fullLayout() {
        assertEquals(
                CabinLayout.FJWY(8, 40, 24, 200),
                CabinLayout.parseString("F8/J40/W24/Y200")
        );
    }

    @Test
    public void test_parse_anyOrder() {
        assertEquals(
                CabinLayout.FJWY(8, 40, 24, 200),
                CabinLayout.parseString("Y200/W24/J40/F8")
        );
    }

    @Test
    public void test_parse_partial() {
        assertEquals(
                CabinLayout.FJWY(0, 40, 0, 200),
                CabinLayout.parseString("J40/Y200")
        );
    }

    @Test
    public void test_parse_zeroValues() {
        assertEquals(
                CabinLayout.FJWY(0, 0, 0, 0),
                CabinLayout.parseString("F0/J0/W0/Y0")
        );
    }

    @Test
    public void test_parse_invalidType() {
        assertThrows(IllegalArgumentException.class,
                () -> CabinLayout.parseString("X100"));
    }

    @Test
    public void test_parse_missingNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> CabinLayout.parseString("Y"));
    }

    @Test
    public void test_parse_nonNumeric() {
        assertThrows(IllegalArgumentException.class,
                () -> CabinLayout.parseString("Y10A"));
    }

    @Test
    public void test_parse_badFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> CabinLayout.parseString("/Y100"));
    }
}
