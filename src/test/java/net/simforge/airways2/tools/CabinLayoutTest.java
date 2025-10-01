package net.simforge.airways2.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CabinLayoutTest {
    @Test
    public void test_Y160() {
        final CabinLayout layout = CabinLayout.Y(160);
        final int stored = layout.toSigned32bit();
        final CabinLayout restored = CabinLayout.fromSigned32bit(stored);
        assertEquals(layout, restored);
    }

    @Test
    public void test_F0J0W0Y0() {
        final CabinLayout layout = CabinLayout.FJWY(0, 0, 0, 0);
        final int stored = layout.toSigned32bit();
        final CabinLayout restored = CabinLayout.fromSigned32bit(stored);
        assertEquals(layout, restored);
    }

    @Test
    public void test_F31J127W255Y1023() {
        final CabinLayout layout = CabinLayout.FJWY(31, 127, 255, 1023);
        final int stored = layout.toSigned32bit();
        final CabinLayout restored = CabinLayout.fromSigned32bit(stored);
        assertEquals(layout, restored);
    }

    @Test
    public void test_F0J0W0Y0_toString() {
        final CabinLayout layout = CabinLayout.FJWY(0, 0, 0, 0);
        assertEquals("Y0", layout.toString());
    }

    @Test
    public void test_F0J10W0Y0_toString() {
        final CabinLayout layout = CabinLayout.FJWY(0, 10, 0, 0);
        assertEquals("J10", layout.toString());
    }

    @Test
    public void test_Y100() {
        final CabinLayout layout = CabinLayout.FJWY(0, 0, 0, 100);
        assertEquals("Y100", layout.toString());
    }
}
