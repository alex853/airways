package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.Strings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AirportsLayoutTest {
    @TempDir
    Path root;

    @Test
    public void test__record_is_20_bytes_and_new_fields_default_to_zero() throws IOException {
        final Airports airports = new Airports(new Strings());
        final Airports.Airport airport = airports.create(10.5, -20.25, "ABC", "KABC", "Alpha");

        assertEquals(0, airport.getCountryId());
        assertFalse(airport.isExcluded());
        assertFalse(airport.isHub());
        airports.save(root);
        assertEquals(20, Files.size(root.resolve("airports")));
    }

    @Test
    public void test__codes_of_every_length_and_new_fields_are_kept() throws IOException {
        final Strings strings = new Strings();
        final Airports airports = new Airports(strings);
        airports.create(1, 2, "ABC", "KABC", "Alpha");
        airports.create(3, 4, "AB", "ABC", "Beta");
        airports.create(5, 6, null, "AB12", "Gamma");
        airports.byId(1).orElseThrow().setCountryId(200);
        airports.byId(1).orElseThrow().setHub(true);

        airports.save(root);
        final Airports loaded = new Airports(strings);
        loaded.loadIfExists(root);

        assertEquals("ABC", loaded.byId(1).orElseThrow().getIata());
        assertEquals("KABC", loaded.byId(1).orElseThrow().getIcao());
        assertEquals(200, loaded.byId(1).orElseThrow().getCountryId());
        assertTrue(loaded.byId(1).orElseThrow().isHub());
        assertFalse(loaded.byId(1).orElseThrow().isExcluded());
        assertEquals("AB", loaded.byId(2).orElseThrow().getIata());
        assertEquals("ABC", loaded.byId(2).orElseThrow().getIcao());
        assertNull(loaded.byId(3).orElseThrow().getIata());
        assertEquals("AB12", loaded.byId(3).orElseThrow().getIcao());
        assertEquals(3, loaded.byIcao("AB12").orElseThrow().getId());
    }

    @Test
    public void test__flags_excluded_and_hub_are_independent_bits() throws IOException {
        final Strings strings = new Strings();
        final Airports airports = new Airports(strings);
        final Airports.Airport airport = airports.create(1, 2, "ABC", "KABC", "Alpha");
        assertFalse(airport.isExcluded());
        assertFalse(airport.isHub());

        airport.setExcluded(true);
        assertTrue(airport.isExcluded());
        assertFalse(airport.isHub());

        airport.setHub(true);
        assertTrue(airport.isExcluded());
        assertTrue(airport.isHub());

        airport.setExcluded(false);
        assertFalse(airport.isExcluded());
        assertTrue(airport.isHub());

        airports.save(root);
        final Airports loaded = new Airports(strings);
        loaded.loadIfExists(root);
        assertTrue(loaded.byId(1).orElseThrow().isHub());
        assertFalse(loaded.byId(1).orElseThrow().isExcluded());
    }
}
