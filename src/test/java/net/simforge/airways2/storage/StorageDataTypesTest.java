package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class StorageDataTypesTest {
    private final Storage<Object> storage = Storage.builder()
            .withDataField(DataField.of(DataType.Signed32bit))
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .withDataField(DataField.of(DataType.Unsigned16bit))
            .withDataField(DataField.of(DataType.LatLong24bit))
            .withDataField(DataField.of(DataType.LatLong16bit))
            .withDataField(DataField.of(DataType.PlainString).length(20))
            .build();

    private final DataField signed32bitField = storage.getDataField(0);
    private final DataField unsigned8bitField = storage.getDataField(1);
    private final DataField unsigned16bitField = storage.getDataField(2);
    private final DataField latLong24bitField = storage.getDataField(3);
    private final DataField latLong16bitField = storage.getDataField(4);
    private final DataField plainString20Field = storage.getDataField(5);

    private final int recordId = storage.addRecord();

    @ParameterizedTest
    @ValueSource(ints = {0, 1000000, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    public void test_signed32bit(final int value) {
        storage.set(recordId, signed32bitField, value);
        assertEquals(value, storage.getAsInt(recordId, signed32bitField));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 255})
    public void test_unsigned8bit(final int value) {
        storage.set(recordId, unsigned8bitField, value);
        assertEquals(value, storage.getAsInt(recordId, unsigned8bitField));
    }

    @Test
    public void test_unsigned8bit_out_of_bounds() {
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, unsigned8bitField, -1));
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, unsigned8bitField, 256));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1000, 10000, 20000, 30000, 40000, 50000, 60000, 65535})
    public void test_unsigned16bit(final int value) {
        storage.set(recordId, unsigned16bitField, value);
        assertEquals(value, storage.getAsInt(recordId, unsigned16bitField));
    }

    @Test
    public void test_unsigned16bit_out_of_bounds() {
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, unsigned16bitField, -1));
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, unsigned16bitField, 65536));
    }

    @ParameterizedTest
    @ValueSource(floats = {-180, -179.9f, -90, -1, 0, 1, 90, 179.9f, 180})
    public void test_latLong24bit(final float value) {
        storage.set(recordId, latLong24bitField, value);
        assertEquals(value, storage.getAsFloat(recordId, latLong24bitField), 0.0001);
    }

    @Test
    public void test_latLong24bit_out_of_bounds() {
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, latLong24bitField, -181.0f));
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, latLong24bitField, 181.0f));
    }

    @ParameterizedTest
    @ValueSource(floats = {-180, -179.9f, -90, -1, 0, 1, 90, 179.9f, 180})
    public void test_latLong16bit(final float value) {
        storage.set(recordId, latLong16bitField, value);
        assertEquals(value, storage.getAsFloat(recordId, latLong16bitField), 0.01);
    }

    @Test
    public void test_latLong16bit_out_of_bounds() {
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, latLong16bitField, -181.0f));
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, latLong16bitField, 181.0f));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "Hello World"})
    public void test_plainString_20(final String value) {
        storage.set(recordId, plainString20Field, value);
        assertEquals(value, storage.getAsString(recordId, plainString20Field));
    }

    @Test
    public void test_plainString_20_null() {
        final String nullString = null;
        storage.set(recordId, plainString20Field, nullString);
        assertNull(storage.getAsString(recordId, plainString20Field));
    }

    @Test
    public void test_plainString_20_too_long_text() {
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, plainString20Field, "waaaaayyyyyy toooooooo loooooooong teeeeeext"));
    }


    // todo ak wrong formats on wrong methods
}