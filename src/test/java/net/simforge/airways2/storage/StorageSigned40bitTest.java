package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Signed40bit: five bytes with the sign, for the money.
 */
class StorageSigned40bitTest {
    private final Storage<Object> storage = Storage.builder()
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .withDataField(DataField.of(DataType.Signed40bit))
            .withDataField(DataField.of(DataType.Signed32bit))
            .build();

    private final DataField byteField = storage.getDataField(0);
    private final DataField longField = storage.getDataField(1);
    private final DataField intField = storage.getDataField(2);

    private final int recordId = storage.addRecord();

    @ParameterizedTest
    @ValueSource(longs = {0, 1, -1, 255, 256, -256, 1_000_000, -1_000_000, 2_147_483_647L, 2_147_483_648L, 4_294_967_295L, 4_294_967_296L, -4_294_967_296L,
            5_000_000_000L, -5_000_000_000L, 549_755_813_887L, -549_755_813_888L})
    void everyValueOfTheRangeIsStoredAsItIs(final long value) {
        storage.setLong(recordId, longField, value);

        assertEquals(value, storage.getAsLong(recordId, longField));
    }

    @Test
    void theRangeIsFromMinus549BillionTo549Billion() {
        assertEquals(-549_755_813_888L, DataType.Signed40bit.minValue());
        assertEquals(549_755_813_887L, DataType.Signed40bit.maxValue());
    }

    @Test
    void aValueOutOfTheRangeIsAnErrorAndNothingIsCut() {
        storage.setLong(recordId, longField, 12345);

        assertThrows(IllegalArgumentException.class, () -> storage.setLong(recordId, longField, 549_755_813_888L));
        assertThrows(IllegalArgumentException.class, () -> storage.setLong(recordId, longField, -549_755_813_889L));
        assertThrows(IllegalArgumentException.class, () -> storage.setLong(recordId, longField, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> storage.setLong(recordId, longField, Long.MIN_VALUE));

        assertEquals(12345, storage.getAsLong(recordId, longField));
    }

    @Test
    void theNeighbourFieldsDoNotSuffer() {
        storage.set(recordId, byteField, 200);
        storage.set(recordId, intField, -123456);

        storage.setLong(recordId, longField, DataType.Signed40bit.minValue());
        assertEquals(200, storage.getAsInt(recordId, byteField));
        assertEquals(-123456, storage.getAsInt(recordId, intField));

        storage.setLong(recordId, longField, DataType.Signed40bit.maxValue());
        assertEquals(200, storage.getAsInt(recordId, byteField));
        assertEquals(-123456, storage.getAsInt(recordId, intField));
    }

    @Test
    void aFieldIsFiveBytes() {
        // the fields are 1 + 5 + 4 bytes
        assertEquals(10, intField.offsetPlusSize());
    }

    @Test
    void otherFieldsAreNotReadAsLong() {
        assertThrows(IllegalArgumentException.class, () -> storage.getAsLong(recordId, intField));
        assertThrows(IllegalArgumentException.class, () -> storage.setLong(recordId, intField, 1));
    }
}
