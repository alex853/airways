package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Signed64bit: a long in eight bytes, for the money.
 */
class StorageSigned64bitTest {
    private final Storage<Object> storage = Storage.builder()
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .withDataField(DataField.of(DataType.Signed64bit))
            .withDataField(DataField.of(DataType.Signed32bit))
            .build();

    private final DataField byteField = storage.getDataField(0);
    private final DataField longField = storage.getDataField(1);
    private final DataField intField = storage.getDataField(2);

    private final int recordId = storage.addRecord();

    @ParameterizedTest
    @ValueSource(longs = {0, 1, -1, 1_000_000, -1_000_000, 4_294_967_295L, 4_294_967_296L, -4_294_967_296L, 1L << 40, Long.MAX_VALUE, Long.MIN_VALUE})
    void everyLongIsStoredAsItIs(final long value) {
        storage.setLong(recordId, longField, value);

        assertEquals(value, storage.getAsLong(recordId, longField));
    }

    @Test
    void theNeighbourFieldsDoNotSuffer() {
        storage.set(recordId, byteField, 200);
        storage.set(recordId, intField, -123456);

        storage.setLong(recordId, longField, Long.MIN_VALUE);
        assertEquals(200, storage.getAsInt(recordId, byteField));
        assertEquals(-123456, storage.getAsInt(recordId, intField));

        storage.setLong(recordId, longField, Long.MAX_VALUE);
        assertEquals(200, storage.getAsInt(recordId, byteField));
        assertEquals(-123456, storage.getAsInt(recordId, intField));
    }

    @Test
    void aRecordWithAnEightByteFieldHasTheRightSize() {
        // the deletion status (1 byte, with the header of the record) is not counted here, the fields are 1 + 8 + 4 bytes
        assertEquals(13, intField.offsetPlusSize());
    }

    @Test
    void otherFieldsAreNotReadAsLong() {
        assertThrows(IllegalArgumentException.class, () -> storage.getAsLong(recordId, intField));
        assertThrows(IllegalArgumentException.class, () -> storage.setLong(recordId, intField, 1));
    }
}
