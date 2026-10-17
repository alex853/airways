package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A record id must fit into the id data type of the storage: the storage is full when it does not.
 */
class StorageIdLimitTest {
    private static Storage.Builder<Integer> builder(final DataType idDataType) {
        return Storage.<Integer>builder()
                .name("id-limit-test")
                .withInstantiator(recordId -> recordId)
                .withIdOf(idDataType)
                .withDataField(DataField.of(DataType.Signed32bit))
                .deletedRecordQuarantine(Duration.ZERO);
    }

    private static void fill(final Storage<Integer> storage, final int count) {
        for (int i = 0; i < count; i++) {
            storage.addRecord();
        }
    }

    @Test
    public void test__unsigned_8_bit_id_storage_holds_255_records_and_refuses_the_256th() {
        final Storage<Integer> storage = builder(DataType.Unsigned8bit).build();

        fill(storage, 255);

        final IllegalStateException e = assertThrows(IllegalStateException.class, storage::addRecord);
        assertTrue(e.getMessage().contains("id-limit-test"), e.getMessage());
        assertTrue(e.getMessage().contains("256"), e.getMessage());
        assertTrue(e.getMessage().contains("Unsigned8bit"), e.getMessage());
    }

    @Test
    public void test__refused_record_does_not_change_the_storage() {
        final Storage<Integer> storage = builder(DataType.Unsigned8bit).build();
        fill(storage, 255);
        final long stamp = storage.getLastChangedAtMillis();

        assertThrows(IllegalStateException.class, storage::addRecord);
        assertThrows(IllegalStateException.class, storage::addRecord);

        assertEquals(255, storage.all().count());
        assertEquals(stamp, storage.getLastChangedAtMillis());
    }

    @Test
    public void test__deleted_record_of_a_full_storage_is_reused_instead_of_the_failure() {
        final Storage<Integer> storage = builder(DataType.Unsigned8bit).build();
        fill(storage, 255);
        storage.deleteRecord(100);

        final int reused = assertDoesNotThrow(storage::addRecord);

        assertEquals(100, reused);
        assertThrows(IllegalStateException.class, storage::addRecord);
    }

    @Test
    public void test__quarantined_deleted_record_does_not_help_a_full_storage() {
        final Storage<Integer> storage = builder(DataType.Unsigned8bit).deletedRecordQuarantine(Duration.ofDays(1)).build();
        fill(storage, 255);
        storage.deleteRecord(100);

        assertThrows(IllegalStateException.class, storage::addRecord);
    }

    @Test
    public void test__unsigned_16_bit_id_limit() {
        final Storage<Integer> storage = builder(DataType.Unsigned16bit).build();

        fill(storage, 65_535);

        assertEquals(65_535, storage.all().count());
        assertThrows(IllegalStateException.class, storage::addRecord);
    }

    @Test
    public void test__storage_without_the_id_data_type_has_no_small_limit() {
        final Storage<Integer> storage = Storage.<Integer>builder()
                .name("id-limit-test")
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Signed32bit))
                .build();

        fill(storage, 1000);

        assertEquals(1000, storage.all().count());
    }

    @Test
    public void test__not_integer_id_data_type_is_refused_when_the_storage_is_built() {
        assertThrows(IllegalArgumentException.class, () -> builder(DataType.Float).build());
        assertThrows(IllegalArgumentException.class, () -> builder(DataType.PlainString).build());
    }
}
