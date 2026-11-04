package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.storage.Strings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AirportsLayoutTest {
    @TempDir
    Path root;

    @Test
    public void test__record_is_20_bytes_and_new_fields_default_to_zero() throws IOException {
        final Airports airports = new Airports(new Strings());
        final Airports.Airport airport = airports.create(10.5, -20.25, "ABC", "KABC", "Alpha");

        assertEquals(0, airport.getCountryId());
        assertEquals(0, airport.getFlags());
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
        airports.byId(1).orElseThrow().setFlags(0x81);

        airports.save(root);
        final Airports loaded = new Airports(strings);
        loaded.loadIfExists(root);

        assertEquals("ABC", loaded.byId(1).orElseThrow().getIata());
        assertEquals("KABC", loaded.byId(1).orElseThrow().getIcao());
        assertEquals(200, loaded.byId(1).orElseThrow().getCountryId());
        assertEquals(0x81, loaded.byId(1).orElseThrow().getFlags());
        assertEquals("AB", loaded.byId(2).orElseThrow().getIata());
        assertEquals("ABC", loaded.byId(2).orElseThrow().getIcao());
        assertNull(loaded.byId(3).orElseThrow().getIata());
        assertEquals("AB12", loaded.byId(3).orElseThrow().getIcao());
        assertEquals(3, loaded.byIcao("AB12").orElseThrow().getId());
    }

    @Test
    public void test__file_of_the_old_layout_is_migrated_once() throws IOException {
        // the old layout: iata and icao are length-prefixed strings
        final Storage<Object> old = Storage.builder()
                .name("airports")
                .withIdOf(DataType.Unsigned16bit)
                .withDataField(DataField.of(DataType.LatLong24bit))
                .withDataField(DataField.of(DataType.LatLong24bit))
                .withDataField(DataField.of(DataType.PlainString).length(3))
                .withDataField(DataField.of(DataType.PlainString).length(4))
                .withDataField(DataField.of(DataType.Signed32bit))
                .build();
        final String[][] codes = {{"ABC", "KABC"}, {"AB", "ABC"}, {null, "AB12"}, {"", "LFPY"}};
        for (int i = 0; i < codes.length; i++) {
            final int id = old.addRecord();
            old.set(id, old.getDataField(0), 10.0f + i);
            old.set(id, old.getDataField(1), 20.0f + i);
            old.set(id, old.getDataField(2), codes[i][0]);
            old.set(id, old.getDataField(3), codes[i][1]);
            old.set(id, old.getDataField(4), 100 + i);
        }
        old.save(root);

        final Airports airports = new Airports(new Strings());
        airports.loadIfExists(root);

        for (int i = 0; i < codes.length; i++) {
            final Airports.Airport airport = airports.byId(i + 1).orElseThrow();
            assertEquals(codes[i][0] == null || codes[i][0].isEmpty() ? null : codes[i][0], airport.getIata());
            assertEquals(codes[i][1], airport.getIcao());
            assertEquals(0, airport.getCountryId());
            assertEquals(0, airport.getFlags());
        }

        // the values which are set after the migration stay after the next loads
        airports.byId(1).orElseThrow().setCountryId(4);
        airports.byId(1).orElseThrow().setFlags(3);
        airports.byId(2).orElseThrow().setCountryId(3);
        airports.byId(2).orElseThrow().setFlags(4);
        airports.save(root);
        final Airports reloaded = new Airports(new Strings());
        reloaded.loadIfExists(root);
        assertEquals(4, reloaded.byId(1).orElseThrow().getCountryId());
        assertEquals(3, reloaded.byId(1).orElseThrow().getFlags());
        assertEquals(3, reloaded.byId(2).orElseThrow().getCountryId());
        assertEquals(4, reloaded.byId(2).orElseThrow().getFlags());
    }
}
