package net.simforge.airways2.worldbuilder.tools;

import net.simforge.refdata.airports.Airports;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ImportSelectedAirports takes the data of an airport from the refdata library, so every code of World25_009 has to be found there.
 */
class ImportSelectedAirportsRefdataTest {
    @Test
    void test__airports_of_the_last_batch_are_in_refdata() {
        for (final String icao : new String[]{"MDPC", "VAAH", "ZSAM", "LFBO", "SKBO", "CYEG"}) {
            assertTrue(Airports.get().findByIcao(icao).isPresent(), icao);
        }
    }

    @Test
    void test__bali_is_in_refdata_under_the_old_code_only() {
        // the world uses the new code WADD, the library still has WRRR, see ImportSelectedAirports.OLD_CODES_IN_REFDATA
        assertTrue(Airports.get().findByIcao("WRRR").isPresent());
    }
}
