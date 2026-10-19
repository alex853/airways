package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tenths8bit: a decimal with one digit after the point in one byte, from 0.0 to 25.5. It is rounded to the tenth and is stored as an
 * integer from 0 to 255 (3.4 is 34).
 */
class StorageTenths8bitTest {
    private final Storage<Object> storage = Storage.builder()
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .withDataField(DataField.of(DataType.Tenths8bit))
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .build();

    private final DataField beforeField = storage.getDataField(0);
    private final DataField tenthsField = storage.getDataField(1);
    private final DataField afterField = storage.getDataField(2);

    private final int recordId = storage.addRecord();

    @Test
    public void test_the_field_is_one_byte() {
        assertEquals(1, tenthsField.size());
        assertEquals(1, tenthsField.offset());
        assertEquals(2, afterField.offset());
    }

    @ParameterizedTest
    @ValueSource(floats = {0.0f, 0.1f, 0.5f, 1.0f, 2.3f, 3.4f, 7.6f, 10.0f, 12.7f, 25.4f, 25.5f})
    public void test_a_value_with_one_digit_is_stored_and_read_back_as_is(final float value) {
        storage.set(recordId, tenthsField, value);

        assertEquals(value, storage.getAsFloat(recordId, tenthsField), 0.0001f);
    }

    @Test
    public void test_it_is_rounded_to_the_tenth() {
        storage.set(recordId, tenthsField, 3.42f);
        assertEquals(3.4f, storage.getAsFloat(recordId, tenthsField), 0.0001f);

        storage.set(recordId, tenthsField, 3.46f);
        assertEquals(3.5f, storage.getAsFloat(recordId, tenthsField), 0.0001f);

        storage.set(recordId, tenthsField, 7.55f);
        assertEquals(7.6f, storage.getAsFloat(recordId, tenthsField), 0.0001f);

        storage.set(recordId, tenthsField, 0.04f);
        assertEquals(0.0f, storage.getAsFloat(recordId, tenthsField), 0.0001f);

        storage.set(recordId, tenthsField, 25.46f);
        assertEquals(25.5f, storage.getAsFloat(recordId, tenthsField), 0.0001f);
    }

    @Test
    public void test_it_is_stored_as_an_integer_number_of_tenths() {
        final Storage<Object> raw = Storage.builder()
                .name("tenths-test")
                .withDataField(DataField.of(DataType.Tenths8bit))
                .build();
        final int id = raw.addRecord();
        raw.set(id, raw.getDataField(0), 3.42f);

        // the stored byte is read from the saved file: the byte 0 is the header of the record, the byte 1 is the value
        final byte[] bytes = dump(raw);
        assertEquals(34, Byte.toUnsignedInt(bytes[1]));

        raw.set(id, raw.getDataField(0), 25.5f);
        assertEquals(255, Byte.toUnsignedInt(dump(raw)[1]));
    }

    private static byte[] dump(final Storage<Object> storage) {
        try {
            final java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("tenths");
            storage.save(dir);
            return java.nio.file.Files.readAllBytes(dir.resolve("tenths-test"));
        } catch (final java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @ParameterizedTest
    @ValueSource(floats = {-0.1f, -0.04f, -1.0f, 25.51f, 25.6f, 26.0f, 100.0f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    public void test_a_value_out_of_the_range_is_an_error_and_the_old_value_stays(final float value) {
        storage.set(recordId, tenthsField, 3.4f);

        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, tenthsField, value));

        assertEquals(3.4f, storage.getAsFloat(recordId, tenthsField), 0.0001f);
    }

    @Test
    public void test_neighbour_fields_are_not_touched() {
        storage.set(recordId, beforeField, 77);
        storage.set(recordId, afterField, 200);

        storage.set(recordId, tenthsField, 25.5f);

        assertEquals(77, storage.getAsInt(recordId, beforeField));
        assertEquals(200, storage.getAsInt(recordId, afterField));
        assertEquals(25.5f, storage.getAsFloat(recordId, tenthsField), 0.0001f);
    }

    @Test
    public void test_the_other_kinds_of_access_are_not_applicable() {
        assertThrows(IllegalStateException.class, () -> storage.getAsInt(recordId, tenthsField));
        assertThrows(IllegalStateException.class, () -> storage.set(recordId, tenthsField, 5));
        assertThrows(IllegalStateException.class, () -> storage.getAsString(recordId, tenthsField));
        assertThrows(IllegalStateException.class, () -> storage.set(recordId, tenthsField, "5"));
    }

    @Test
    public void test_the_new_record_has_zero() {
        assertEquals(0.0f, storage.getAsFloat(recordId, tenthsField), 0.0f);
    }
}
