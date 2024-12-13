package net.simforge.airways2.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StorageRecordsTest {
    private final Storage<Object> storage = Storage.builder()
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
        final int recordId = storage.addRecord();

        assertEquals(1, storage.getCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {-10, -1, 0, 2, 10})
    public void test_record_id_out_of_bounds(final int recordId) {
        assertThrows(IllegalArgumentException.class, () -> storage.getAsInt(recordId, signed32bitField));
        assertThrows(IllegalArgumentException.class, () -> storage.getAsFloat(recordId, latLong24bitField));
        assertThrows(IllegalArgumentException.class, () -> storage.getAsString(recordId, plainString20Field));
    }

    // todo ak deleted records should not be read

}