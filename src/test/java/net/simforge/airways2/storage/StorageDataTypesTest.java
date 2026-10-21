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
            .withDataField(DataField.of(DataType.Tenths8bit))
            .withDataField(DataField.of(DataType.Float16bit))
            .withDataField(DataField.of(DataType.PlainString).length(20))
            .withDataField(DataField.of(DataType.Signed64bit))
            .build();

    private final DataField signed32bitField = storage.getDataField(0);
    private final DataField unsigned8bitField = storage.getDataField(1);
    private final DataField unsigned16bitField = storage.getDataField(2);
    private final DataField unsigned24bitField = storage.getDataField(3);
    private final DataField floatField = storage.getDataField(4);
    private final DataField latLong24bitField = storage.getDataField(5);
    private final DataField latLong16bitField = storage.getDataField(6);
    private final DataField tenths8bitField = storage.getDataField(7);
    private final DataField float16bitField = storage.getDataField(8);
    private final DataField plainString20Field = storage.getDataField(9);
    private final DataField signed64bitField = storage.getDataField(10);

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

    // ---- wrong formats on wrong methods ----

    private static final java.util.List<DataType> INT_TYPES = java.util.List.of(
            DataType.Signed32bit, DataType.Unsigned8bit, DataType.Unsigned16bit, DataType.Unsigned24bit);
    private static final java.util.List<DataType> FLOAT_TYPES = java.util.List.of(
            DataType.Float, DataType.LatLong24bit, DataType.LatLong16bit, DataType.Tenths8bit, DataType.Float16bit);

    private DataField fieldOf(final DataType dataType) {
        return switch (dataType) {
            case Signed32bit -> signed32bitField;
            case Unsigned8bit -> unsigned8bitField;
            case Unsigned16bit -> unsigned16bitField;
            case Unsigned24bit -> unsigned24bitField;
            case Float -> floatField;
            case LatLong24bit -> latLong24bitField;
            case LatLong16bit -> latLong16bitField;
            case Tenths8bit -> tenths8bitField;
            case Float16bit -> float16bitField;
            case PlainString -> plainString20Field;
            case Signed64bit -> signed64bitField;
        };
    }

    @ParameterizedTest
    @EnumSource(value = DataType.class, names = {"Float", "LatLong24bit", "LatLong16bit", "Tenths8bit", "Float16bit", "PlainString", "Signed64bit"})
    public void test_getAsInt_is_not_applicable_to_not_integer_types(final DataType dataType) {
        assertThrows(IllegalStateException.class, () -> storage.getAsInt(recordId, fieldOf(dataType)));
    }

    @ParameterizedTest
    @EnumSource(value = DataType.class, names = {"Signed32bit", "Unsigned8bit", "Unsigned16bit", "Unsigned24bit", "PlainString", "Signed64bit"})
    public void test_getAsFloat_is_not_applicable_to_not_float_types(final DataType dataType) {
        assertThrows(IllegalStateException.class, () -> storage.getAsFloat(recordId, fieldOf(dataType)));
    }

    @ParameterizedTest
    @EnumSource(value = DataType.class, mode = EnumSource.Mode.EXCLUDE, names = {"PlainString"})
    public void test_getAsString_is_applicable_to_plain_string_only(final DataType dataType) {
        assertThrows(IllegalStateException.class, () -> storage.getAsString(recordId, fieldOf(dataType)));
    }

    @ParameterizedTest
    @EnumSource(value = DataType.class, names = {"Float", "LatLong24bit", "LatLong16bit", "Tenths8bit", "Float16bit", "PlainString"})
    public void test_set_int_is_not_applicable_to_not_integer_types(final DataType dataType) {
        assertThrows(IllegalStateException.class, () -> storage.set(recordId, fieldOf(dataType), 1));
    }

    @ParameterizedTest
    @EnumSource(value = DataType.class, names = {"Signed32bit", "Unsigned8bit", "Unsigned16bit", "Unsigned24bit", "PlainString"})
    public void test_set_float_is_not_applicable_to_not_float_types(final DataType dataType) {
        assertThrows(IllegalStateException.class, () -> storage.set(recordId, fieldOf(dataType), 1.5f));
    }

    @ParameterizedTest
    @EnumSource(value = DataType.class, mode = EnumSource.Mode.EXCLUDE, names = {"PlainString"})
    public void test_set_string_is_applicable_to_plain_string_only(final DataType dataType) {
        assertThrows(IllegalStateException.class, () -> storage.set(recordId, fieldOf(dataType), "text"));
    }

    @Test
    public void test_wrong_method_does_not_change_the_value() {
        storage.set(recordId, unsigned16bitField, 1234);
        storage.set(recordId, floatField, 2.5f);
        storage.set(recordId, plainString20Field, "text");

        assertThrows(IllegalStateException.class, () -> storage.set(recordId, unsigned16bitField, 9.5f));
        assertThrows(IllegalStateException.class, () -> storage.set(recordId, floatField, 7));
        assertThrows(IllegalStateException.class, () -> storage.set(recordId, plainString20Field, 7));

        assertEquals(1234, storage.getAsInt(recordId, unsigned16bitField));
        assertEquals(2.5f, storage.getAsFloat(recordId, floatField));
        assertEquals("text", storage.getAsString(recordId, plainString20Field));
    }

    @Test
    public void test_every_data_type_is_covered_by_the_helper_lists() {
        final java.util.Set<DataType> covered = new java.util.HashSet<>(INT_TYPES);
        covered.addAll(FLOAT_TYPES);
        covered.add(DataType.PlainString);
        covered.add(DataType.Signed64bit);

        assertEquals(java.util.Set.of(DataType.values()), covered);
    }
}