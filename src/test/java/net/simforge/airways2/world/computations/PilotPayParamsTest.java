package net.simforge.airways2.world.computations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parameters of the pay in one byte: bit 0 type currency, bit 1 night currency, bit 2 can carry passengers, bit 3 manual, bit 4 free,
 * bits 5-7 the experience level 0 to 5.
 */
class PilotPayParamsTest {
    @Test
    void nothingIsSetInTheEmptyParameters() {
        final PilotPayParams params = PilotPayParams.none();

        assertFalse(params.hasTypeCurrency());
        assertFalse(params.hasNightCurrency());
        assertFalse(params.canCarryPassengers());
        assertFalse(params.isManual());
        assertEquals(0, params.getExperienceLevel());
        assertEquals(0, params.toByte());
    }

    @Test
    void everyFlagIsItsOwnBit() {
        final PilotPayParams params = PilotPayParams.none();

        params.setTypeCurrency(true);
        assertEquals(0b0000_0001, params.toByte());
        params.setNightCurrency(true);
        assertEquals(0b0000_0011, params.toByte());
        params.setCanCarryPassengers(true);
        assertEquals(0b0000_0111, params.toByte());
        params.setManual(true);
        assertEquals(0b0000_1111, params.toByte());

        params.setNightCurrency(false);
        assertEquals(0b0000_1101, params.toByte());
        assertTrue(params.hasTypeCurrency());
        assertFalse(params.hasNightCurrency());
        assertTrue(params.canCarryPassengers());
        assertTrue(params.isManual());
    }

    @Test
    void theExperienceLevelIsInTheThreeTopBits() {
        final PilotPayParams params = PilotPayParams.none();

        for (int level = 0; level <= 5; level++) {
            params.setExperienceLevel(level);
            assertEquals(level, params.getExperienceLevel());
            assertEquals(level << 5, params.toByte(), "level " + level);
        }
        params.setExperienceLevel(5);
        assertEquals(0b1010_0000, params.toByte());
    }

    @Test
    void theLevelAndTheFlagsDoNotTouchEachOther() {
        final PilotPayParams params = PilotPayParams.none();
        params.setTypeCurrency(true);
        params.setManual(true);

        params.setExperienceLevel(3);
        assertTrue(params.hasTypeCurrency());
        assertTrue(params.isManual());
        assertFalse(params.hasNightCurrency());
        assertEquals(3, params.getExperienceLevel());

        params.setExperienceLevel(1);
        params.setNightCurrency(true);
        assertEquals(1, params.getExperienceLevel());
        assertEquals(0b0010_1011, params.toByte());
    }

    @Test
    void theLevelIsFrom0To5() {
        final PilotPayParams params = PilotPayParams.none();

        assertThrows(IllegalArgumentException.class, () -> params.setExperienceLevel(6));
        assertThrows(IllegalArgumentException.class, () -> params.setExperienceLevel(-1));
        assertEquals(0, params.getExperienceLevel());
    }

    @Test
    void theParametersCanBeTakenFromTheByteAndGivenBack() {
        final PilotPayParams params = PilotPayParams.of(0b0111_0101);

        assertTrue(params.hasTypeCurrency());
        assertFalse(params.hasNightCurrency());
        assertTrue(params.canCarryPassengers());
        assertFalse(params.isManual());
        assertEquals(3, params.getExperienceLevel());
        assertEquals(0b0111_0101, params.toByte());

        assertThrows(IllegalArgumentException.class, () -> PilotPayParams.of(-1));
        assertThrows(IllegalArgumentException.class, () -> PilotPayParams.of(256));
    }

    @Test
    void theFreeBitStaysAsItIs() {
        // bit 4 is free: the parameters do not use it and do not clear it
        final PilotPayParams params = PilotPayParams.of(0b0001_0000);

        params.setTypeCurrency(true);
        params.setManual(true);
        params.setExperienceLevel(4);
        params.setTypeCurrency(false);
        params.setManual(false);
        params.setExperienceLevel(0);

        assertEquals(0b0001_0000, params.toByte());
    }
}
