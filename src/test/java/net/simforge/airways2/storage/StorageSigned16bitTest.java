package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Signed16bit: two bytes with the sign (the awake credit of a person, in minutes).
 */
class StorageSigned16bitTest {
    private final Storage<Object> storage = Storage.builder()
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .withDataField(DataField.of(DataType.Signed16bit))
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .build();

    private final DataField beforeField = storage.getDataField(0);
    private final DataField shortField = storage.getDataField(1);
    private final DataField afterField = storage.getDataField(2);

    private final int recordId = storage.addRecord();

    @ParameterizedTest
    @ValueSource(ints = {0, 1, -1, 255, 256, -256, 960, -180, 32_767, -32_768})
    void everyValueOfTheRangeIsStoredAsItIs(final int value) {
        storage.set(recordId, shortField, value);

        assertEquals(value, storage.getAsInt(recordId, shortField));
    }

    @Test
    void theFieldIsTwoBytesAndItsNeighboursAreNotTouched() {
        storage.set(recordId, beforeField, 7);
        storage.set(recordId, afterField, 9);

        storage.set(recordId, shortField, -32_768);

        assertEquals(7, storage.getAsInt(recordId, beforeField));
        assertEquals(9, storage.getAsInt(recordId, afterField));
        assertEquals(2, shortField.size());
    }

    @Test
    void aValueOutOfTheRangeIsAnErrorAndNothingIsCut() {
        storage.set(recordId, shortField, 123);

        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, shortField, 32_768));
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, shortField, -32_769));

        assertEquals(123, storage.getAsInt(recordId, shortField));
    }
}
