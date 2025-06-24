package net.simforge.airways2.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageRecordsTest {
    private final Storage<Object> storage = Storage.builder()
            .withInstantiator(recordId -> recordId)
            .withDataField(DataField.of(DataType.Signed32bit))
            .withDataField(DataField.of(DataType.LatLong24bit))
            .withDataField(DataField.of(DataType.PlainString).length(20))
            .build();

    private final DataField signed32bitField = storage.getDataField(0);
    private final DataField latLong24bitField = storage.getDataField(1);
    private final DataField plainString20Field = storage.getDataField(2);

    @BeforeEach
    public void setUp() {
        storage.reset();
    }

    @Test
    public void test_count() {
        storage.addRecord();

        assertEquals(1, storage.getCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {-10, -1, 0, 2, 10})
    public void test_record_id_out_of_bounds(final int recordId) {
        assertThrows(IllegalArgumentException.class, () -> storage.getAsInt(recordId, signed32bitField));
        assertThrows(IllegalArgumentException.class, () -> storage.getAsFloat(recordId, latLong24bitField));
        assertThrows(IllegalArgumentException.class, () -> storage.getAsString(recordId, plainString20Field));
    }

    @Test
    public void test__add_delete_then_read__deleted_should_not_be_read() {
        final int recordId = storage.addRecord();
        storage.deleteRecord(recordId);

        assertEquals(0, storage.getCount());
        assertTrue(storage.all().isEmpty());
    }

    @Test
    public void test__add_several_delete_one_then_read__deleted_should_not_be_read() {
        final int record1Id = storage.addRecord();
        final int record2Id = storage.addRecord();
        final int record3Id = storage.addRecord();
        storage.deleteRecord(record2Id);

        assertEquals(2, storage.getCount());
        assertArrayEquals(
                new Integer[] {record1Id, record3Id},
                storage.all().toArray(new Object[0]));
    }

    @Test
    public void test__add_several_delete_the_last__then_read_the_previous__previous_should_be_read() {
        final int record1Id = storage.addRecord();
        final int record2Id = storage.addRecord();
        final int record3Id = storage.addRecord();
        storage.deleteRecord(record3Id);

        assertEquals(2, storage.getCount());
        assertArrayEquals(
                new Integer[] {record1Id, record2Id},
                storage.all().toArray(new Object[0]));
    }

    @Test
    public void test__add_several_delete_the_middle__then_delete_last__then_read_the_first__first_should_be_read() {
        final int record1Id = storage.addRecord();
        final int record2Id = storage.addRecord();
        final int record3Id = storage.addRecord();
        storage.deleteRecord(record2Id);
        storage.deleteRecord(record3Id);

        assertEquals(1, storage.getCount());
        assertArrayEquals(
                new Integer[] {record1Id},
                storage.all().toArray(new Object[0]));
    }

    @Test
    public void test__read_fields_of_deleted_record__should_fail() {
        final int recordId = storage.addRecord();
        storage.set(recordId, signed32bitField, 10203040);
        storage.set(recordId, latLong24bitField, 123.456f);
        storage.set(recordId, plainString20Field, "1234567890");

        storage.deleteRecord(recordId);

        assertThrows(IllegalArgumentException.class, () -> storage.getAsInt(recordId, signed32bitField));
        assertThrows(IllegalArgumentException.class, () -> storage.getAsFloat(recordId, latLong24bitField));
        assertThrows(IllegalArgumentException.class, () -> storage.getAsString(recordId, plainString20Field));
    }

    @Test
    public void test__add_several_delete_one_then_add_again__deleted_should_not_be_reused() {
        final int record1Id = storage.addRecord();
        final int record2Id = storage.addRecord();
        final int record3Id = storage.addRecord();
        storage.deleteRecord(record2Id);

        final int newlyAddedRecordId = storage.addRecord();

        assertEquals(record2Id, newlyAddedRecordId);
        assertEquals(3, storage.getCount());
        assertArrayEquals(
                new Integer[] {record1Id, newlyAddedRecordId, record3Id},
                storage.all().toArray(new Object[0]));
    }

    @Test
    public void test__read_fields_of_record_created_in_place_of_deleted_record__old_values_should_not_be_read() {
        final int recordId = storage.addRecord();
        storage.set(recordId, signed32bitField, 10203040);
        storage.set(recordId, latLong24bitField, 123.456f);
        storage.set(recordId, plainString20Field, "1234567890");

        storage.deleteRecord(recordId);

        final int newlyAddedRecordId = storage.addRecord();
        assertEquals(0, storage.getAsInt(newlyAddedRecordId, signed32bitField));
        assertEquals(0.0f, storage.getAsFloat(newlyAddedRecordId, latLong24bitField));
        assertEquals("", storage.getAsString(newlyAddedRecordId, plainString20Field));
    }
}