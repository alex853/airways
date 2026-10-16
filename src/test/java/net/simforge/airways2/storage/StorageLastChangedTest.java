package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageLastChangedTest {
    // the clock does not move, a new stamp must still differ from the previous one
    private final AtomicLong clock = new AtomicLong(1_000_000L);

    private final Storage<Object> storage = Storage.builder()
            .name("last-changed-test")
            .withInstantiator(recordId -> new Object())
            .clock(clock::get)
            .withDataField(DataField.of(DataType.Signed32bit))
            .withDataField(DataField.of(DataType.Float))
            .withDataField(DataField.of(DataType.PlainString).length(10))
            .build();

    private final DataField intField = storage.getDataField(0);
    private final DataField floatField = storage.getDataField(1);
    private final DataField stringField = storage.getDataField(2);

    @Test
    public void test__a_new_storage_has_the_current_time() {
        assertEquals(1_000_000L, storage.getLastChangedAtMillis());
    }

    @Test
    public void test__adding_a_record_changes_the_stamp() {
        final long before = storage.getLastChangedAtMillis();

        storage.addRecord();

        assertTrue(storage.getLastChangedAtMillis() > before);
    }

    @Test
    public void test__deleting_a_record_changes_the_stamp() {
        final int recordId = storage.addRecord();
        final long before = storage.getLastChangedAtMillis();

        storage.deleteRecord(recordId);

        assertTrue(storage.getLastChangedAtMillis() > before);
    }

    @Test
    public void test__every_setter_changes_the_stamp() {
        final int recordId = storage.addRecord();

        long before = storage.getLastChangedAtMillis();
        storage.set(recordId, intField, 5);
        assertTrue(storage.getLastChangedAtMillis() > before, "int");

        before = storage.getLastChangedAtMillis();
        storage.set(recordId, floatField, 1.5f);
        assertTrue(storage.getLastChangedAtMillis() > before, "float");

        before = storage.getLastChangedAtMillis();
        storage.set(recordId, stringField, "text");
        assertTrue(storage.getLastChangedAtMillis() > before, "string");

        before = storage.getLastChangedAtMillis();
        storage.setUnsafe(recordId, intField, 6);
        assertTrue(storage.getLastChangedAtMillis() > before, "unsafe int");
    }

    @Test
    public void test__setting_the_same_value_is_a_change_too() {
        final int recordId = storage.addRecord();
        storage.set(recordId, intField, 5);
        final long before = storage.getLastChangedAtMillis();

        storage.set(recordId, intField, 5);

        assertTrue(storage.getLastChangedAtMillis() > before);
    }

    @Test
    public void test__reading_does_not_change_the_stamp() {
        final int recordId = storage.addRecord();
        storage.set(recordId, intField, 5);
        storage.set(recordId, floatField, 1.5f);
        storage.set(recordId, stringField, "text");
        final long before = storage.getLastChangedAtMillis();

        storage.getAsInt(recordId, intField);
        storage.getAsFloat(recordId, floatField);
        storage.getAsString(recordId, stringField);
        storage.byId(recordId);
        storage.all().count();
        storage.filter(id -> true).count();

        assertEquals(before, storage.getLastChangedAtMillis());
    }

    @Test
    public void test__failed_setter_does_not_change_the_stamp() {
        final int recordId = storage.addRecord();
        final long before = storage.getLastChangedAtMillis();

        assertThrows(IllegalStateException.class, () -> storage.set(recordId, intField, "text"));
        assertThrows(IllegalStateException.class, () -> storage.set(recordId, stringField, 5));
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId + 100, intField, 5));

        assertEquals(before, storage.getLastChangedAtMillis());
    }

    @Test
    public void test__stamp_follows_the_clock_when_it_moves() {
        storage.addRecord();

        clock.set(5_000_000L);
        storage.addRecord();

        assertEquals(5_000_000L, storage.getLastChangedAtMillis());
    }

    @Test
    public void test__stamps_of_a_burst_of_changes_are_all_different() {
        final int recordId = storage.addRecord();

        long previous = storage.getLastChangedAtMillis();
        for (int i = 0; i < 1000; i++) {
            storage.set(recordId, intField, i);
            final long current = storage.getLastChangedAtMillis();
            assertTrue(current > previous);
            previous = current;
        }
    }

    @Test
    public void test__loading_changes_the_stamp(@TempDir final Path dir) throws IOException {
        final int recordId = storage.addRecord();
        storage.set(recordId, intField, 42);
        storage.save(dir);
        final long before = storage.getLastChangedAtMillis();

        storage.loadIfExists(dir);

        assertTrue(storage.getLastChangedAtMillis() > before);
        assertEquals(42, storage.getAsInt(recordId, intField));
    }

    @Test
    public void test__loading_without_a_file_resets_the_storage_and_changes_the_stamp(@TempDir final Path dir) throws IOException {
        storage.addRecord();
        final long before = storage.getLastChangedAtMillis();

        storage.loadIfExists(dir);

        assertEquals(0, storage.getCount());
        assertTrue(storage.getLastChangedAtMillis() > before);
    }
}
