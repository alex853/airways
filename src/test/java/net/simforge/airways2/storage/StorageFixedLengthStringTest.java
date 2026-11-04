package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FixedLengthString: exactly the length bytes, no length byte, the string ends at the first zero byte or at the end of the field.
 */
class StorageFixedLengthStringTest {
    private final Storage<Object> storage = Storage.builder()
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .withDataField(DataField.of(DataType.FixedLengthString).length(4))
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .build();

    private final DataField beforeField = storage.getDataField(0);
    private final DataField stringField = storage.getDataField(1);
    private final DataField afterField = storage.getDataField(2);

    private final int recordId = storage.addRecord();

    @Test
    void theFieldTakesExactlyTheLengthBytes() {
        assertEquals(4, stringField.size());
        assertEquals(1, afterField.offset() - stringField.offset() - 3);
    }

    @Test
    void aNewRecordHasNull() {
        assertNull(storage.getAsString(recordId, stringField));
    }

    @Test
    void stringsOfEveryLengthAreStoredAsTheyAre() {
        for (final String value : new String[]{"A", "AB", "ABC", "ABCD"}) {
            storage.set(recordId, stringField, value);

            assertEquals(value, storage.getAsString(recordId, stringField));
        }
    }

    @Test
    void aShorterStringAfterALongerOneDoesNotKeepTheTail() {
        storage.set(recordId, stringField, "ABCD");
        storage.set(recordId, stringField, "XY");

        assertEquals("XY", storage.getAsString(recordId, stringField));
    }

    @Test
    void nullAndEmptyStringAreTheSameAndAreReadAsNull() {
        storage.set(recordId, stringField, "ABCD");
        storage.set(recordId, stringField, (String) null);
        assertNull(storage.getAsString(recordId, stringField));

        storage.set(recordId, stringField, "ABCD");
        storage.set(recordId, stringField, "");
        assertNull(storage.getAsString(recordId, stringField));
    }

    @Test
    void aTooLongStringAndAZeroCharacterAreErrors() {
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, stringField, "ABCDE"));
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, stringField, "A\0B"));
    }

    @Test
    void theNeighboursAreNotTouched() {
        storage.set(recordId, beforeField, 7);
        storage.set(recordId, afterField, 9);

        storage.set(recordId, stringField, "ABCD");
        storage.set(recordId, stringField, "AB");

        assertEquals(7, storage.getAsInt(recordId, beforeField));
        assertEquals(9, storage.getAsInt(recordId, afterField));
    }
}
