package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A storage with a growth step reserves the capacity for several records, but the reserve is not visible: the records, their ids and the saved file are the same.
 */
class StorageGrowthStepTest {
    private static final DataField VALUE = DataField.of(DataType.Signed32bit);

    private static Storage<Integer> storage(final int growthStep) {
        return Storage.<Integer>builder()
                .name("growth-step-test")
                .withInstantiator(recordId -> recordId)
                .withDataField(VALUE)
                .growthStep(growthStep)
                .deletedRecordQuarantine(Duration.ZERO)
                .build();
    }

    @Test
    public void test__reserve_is_not_visible_as_records() {
        final Storage<Integer> storage = storage(100);

        assertEquals(1, storage.addRecord());
        assertEquals(2, storage.addRecord());
        assertEquals(3, storage.addRecord());

        assertEquals(3, storage.all().count());
        assertTrue(storage.byId(3).isPresent());
        assertTrue(storage.byId(4).isEmpty());
    }

    @Test
    public void test__data_survives_the_growths() {
        final Storage<Integer> storage = storage(10);

        for (int i = 1; i <= 250; i++) {
            final int id = storage.addRecord();
            assertEquals(i, id);
            storage.set(id, storage.getDataField(0), i * 7);
        }

        assertEquals(250, storage.all().count());
        for (int i = 1; i <= 250; i++) {
            assertEquals(i * 7, storage.getAsInt(i, storage.getDataField(0)));
        }
    }

    @Test
    public void test__saved_file_has_the_records_only_and_loads_back(@TempDir final Path dir) throws IOException {
        final Storage<Integer> storage = storage(100);
        final int id1 = storage.addRecord();
        storage.set(id1, storage.getDataField(0), 42);
        final int id2 = storage.addRecord();
        storage.set(id2, storage.getDataField(0), 43);

        storage.save(dir);

        // the same two records without a reserve make a file of the same size
        final Path exactDir = Files.createDirectory(dir.resolve("exact"));
        final Storage<Integer> exact = storage(1);
        exact.addRecord();
        exact.addRecord();
        exact.save(exactDir);
        assertEquals(Files.size(exactDir.resolve("growth-step-test")), Files.size(dir.resolve("growth-step-test")));

        final Storage<Integer> loaded = storage(100);
        loaded.loadIfExists(dir);
        assertEquals(2, loaded.all().count());
        assertEquals(42, loaded.getAsInt(1, loaded.getDataField(0)));
        assertEquals(43, loaded.getAsInt(2, loaded.getDataField(0)));
        assertEquals(3, loaded.addRecord());
    }
}
