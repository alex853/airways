package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class StorageDataTypesTest {
    private final Storage<Object> storage = Storage.builder()
            .withDataField(DataField.of(DataType.Signed32bit))
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .withDataField(DataField.of(DataType.Unsigned16bit))
            .withDataField(DataField.of(DataType.Unsigned24bit))
            .withDataField(DataField.of(DataType.Float))
            .withDataField(DataField.of(DataType.LatLong24bit))
            .withDataField(DataField.of(DataType.LatLong16bit))
            .withDataField(DataField.of(DataType.PlainString).length(20))
            .build();

    private final DataField signed32bitField = storage.getDataField(0);
    private final DataField unsigned8bitField = storage.getDataField(1);
    private final DataField unsigned16bitField = storage.getDataField(2);
    private final DataField unsigned24bitField = storage.getDataField(3);
    private final DataField floatField = storage.getDataField(4);
    private final DataField latLong24bitField = storage.getDataField(5);
    private final DataField latLong16bitField = storage.getDataField(6);
    private final DataField plainString20Field = storage.getDataField(7);

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
    @ValueSource(ints = {0, 1000, 1000000, 10000000, 16777215})
    public void test_unsigned24bit(final int value) {
        storage.set(recordId, unsigned24bitField, value);
        assertEquals(value, storage.getAsInt(recordId, unsigned24bitField));
    }

    @Test
    public void test_unsigned24bit_out_of_bounds() {
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, unsigned24bitField, -1));
        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, unsigned24bitField, 16777216));
    }

    @ParameterizedTest
    @ValueSource(floats = {-999, -180, -179.9f, -90, -1, 0, 1, 90, 179.9f, 180, 999, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY})
    public void test_float(final float value) {
        storage.set(recordId, floatField, value);
        assertEquals(value, storage.getAsFloat(recordId, floatField));
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

    @Test
    public void test_plainString_longer_than_254_bytes_should_fail() {
        assertThrows(IllegalArgumentException.class,
                () -> Storage.builder()
                        .withDataField(DataField.of(DataType.PlainString).length(255))
                        .build());
    }

    @Test
    public void test_plainString_with_zero_length_should_fail() {
        assertThrows(IllegalArgumentException.class,
                () -> Storage.builder()
                        .withDataField(DataField.of(DataType.PlainString).length(0))
                        .build());
    }

    @Test
    public void test_plainString_with_negative_length_should_fail() {
        assertThrows(IllegalArgumentException.class,
                () -> Storage.builder()
                        .withDataField(DataField.of(DataType.PlainString).length(-1))
                        .build());
    }

    @ParameterizedTest
    @EnumSource(value = DataType.class, mode = EnumSource.Mode.EXCLUDE, names = { "PlainString" } )
    public void test_length_method_is_not_applicable_to_most_of_types(final DataType dataType) {
        assertThrows(IllegalArgumentException.class,
                () -> Storage.builder()
                        .withDataField(DataField.of(dataType).length(20))
                        .build());
    }

    // todo ak3 tests for cases when wrong formats on wrong methods
}