package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Float16bit: half precision float in two bytes, from -65504 to 65504. The accuracy depends on the size of the value.
 */
class StorageFloat16bitTest {
    private final Storage<Object> storage = Storage.builder()
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .withDataField(DataField.of(DataType.Float16bit))
            .withDataField(DataField.of(DataType.Unsigned8bit))
            .build();

    private final DataField beforeField = storage.getDataField(0);
    private final DataField halfField = storage.getDataField(1);
    private final DataField afterField = storage.getDataField(2);

    private final int recordId = storage.addRecord();

    private float roundTrip(final float value) {
        storage.set(recordId, halfField, value);
        return storage.getAsFloat(recordId, halfField);
    }

    @Test
    public void test_the_field_is_two_bytes() {
        assertEquals(2, halfField.size());
        assertEquals(1, halfField.offset());
        assertEquals(3, afterField.offset());
    }

    @ParameterizedTest
    @ValueSource(floats = {0.0f, 1.0f, -1.0f, 0.5f, 0.25f, 2.0f, 100.0f, 1000.0f, 1024.0f, 1530.0f, 2047.0f, 2048.0f, 4096.0f, 65504.0f, -65504.0f, 0.000061035156f})
    public void test_values_which_a_half_float_holds_exactly_are_read_back_as_is(final float value) {
        assertEquals(value, roundTrip(value), 0.0f);
    }

    @Test
    public void test_accuracy_for_minutes_of_night_time__one_minute_up_to_34_hours() {
        // from 1024 to 2048 the step is 1, so the minutes of up to 34 hours are stored to a whole minute
        assertEquals(1500.0f, roundTrip(1500.2f), 0.0f);
        assertEquals(1501.0f, roundTrip(1500.6f), 0.0f);
        assertEquals(2047.0f, roundTrip(2046.9f), 0.0f);
        // from 512 to 1024 the step is 0.5
        assertEquals(700.5f, roundTrip(700.4f), 0.0f);
        // under 16 the step is under 0.016
        assertEquals(12.5f, roundTrip(12.5f), 0.0f);
        assertEquals(3.14f, roundTrip(3.14f), 0.002f);
    }

    @Test
    public void test_the_step_grows_with_the_value() {
        // 32768 .. 65504: the step is 32
        assertEquals(40000.0f, roundTrip(40010.0f), 0.0f);
        assertEquals(40032.0f, roundTrip(40020.0f), 0.0f);
    }

    @Test
    public void test_small_increments_are_lost_when_the_value_is_big__that_is_why_it_is_not_an_accumulator() {
        // adding 0.2 minutes (12 seconds) to 1500 minutes in a half float changes nothing
        float stored = roundTrip(1500.0f);
        for (int i = 0; i < 100; i++) {
            stored = roundTrip(stored + 0.2f);
        }
        assertEquals(1500.0f, stored, 0.0f);
    }

    @Test
    public void test_rounding_is_to_the_nearest_value() {
        assertEquals(1024.0f, roundTrip(1024.4f), 0.0f);
        assertEquals(1025.0f, roundTrip(1024.6f), 0.0f);
        assertEquals(2048.0f, roundTrip(2047.6f), 0.0f);
    }

    @Test
    public void test_a_very_small_value_goes_to_the_subnormal_numbers_or_to_zero() {
        assertEquals(0.0f, roundTrip(1.0e-9f), 0.0f);
        assertEquals(5.9604645E-8f, roundTrip(6.0e-8f), 1.0e-12f); // the smallest subnormal number
    }

    @ParameterizedTest
    @ValueSource(floats = {65504.1f, 65520.0f, 100000.0f, -65520.0f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    public void test_a_value_out_of_the_range_is_an_error_and_the_old_value_stays(final float value) {
        storage.set(recordId, halfField, 1234.0f);

        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, halfField, value));

        assertEquals(1234.0f, storage.getAsFloat(recordId, halfField), 0.0f);
    }

    @Test
    public void test_neighbour_fields_are_not_touched() {
        storage.set(recordId, beforeField, 77);
        storage.set(recordId, afterField, 200);

        storage.set(recordId, halfField, -65504.0f);

        assertEquals(77, storage.getAsInt(recordId, beforeField));
        assertEquals(200, storage.getAsInt(recordId, afterField));
        assertEquals(-65504.0f, storage.getAsFloat(recordId, halfField), 0.0f);
    }

    @Test
    public void test_the_new_record_has_zero() {
        assertEquals(0.0f, storage.getAsFloat(recordId, halfField), 0.0f);
    }

    @Test
    public void test_the_other_kinds_of_access_are_not_applicable() {
        assertThrows(IllegalStateException.class, () -> storage.getAsInt(recordId, halfField));
        assertThrows(IllegalStateException.class, () -> storage.set(recordId, halfField, 5));
        assertThrows(IllegalStateException.class, () -> storage.getAsString(recordId, halfField));
        assertThrows(IllegalStateException.class, () -> storage.set(recordId, halfField, "5"));
    }

    @Test
    public void test_bits_of_known_values() {
        // 1.0 is 0x3C00, -2.0 is 0xC000, 65504 is 0x7BFF, the smallest normal number 2^-14 is 0x0400, the smallest subnormal one is 0x0001
        assertEquals(0x3C00, Half.toBits(1.0f));
        assertEquals(0xC000, Half.toBits(-2.0f));
        assertEquals(0x7BFF, Half.toBits(65504.0f));
        assertEquals(0x0400, Half.toBits((float) Math.pow(2, -14)));
        assertEquals(0x0001, Half.toBits((float) Math.pow(2, -24)));
        assertEquals(0x0000, Half.toBits(0.0f));
        assertEquals(0x8000, Half.toBits(-0.0f));
        assertEquals(1.0f, Half.fromBits(0x3C00), 0.0f);
        assertEquals(-2.0f, Half.fromBits(0xC000), 0.0f);
        assertEquals(65504.0f, Half.fromBits(0x7BFF), 0.0f);
    }

    @Test
    public void test_every_half_value_goes_through_the_conversion_unchanged() {
        for (int bits = 0; bits < 0x10000; bits++) {
            final int exponent = (bits >> 10) & 0x1F;
            if (exponent == 31) {
                continue; // infinity and NaN, they are not stored
            }
            final float value = Half.fromBits(bits);
            final int back = Half.toBits(value);
            // minus zero and zero are two codes of the same number, everything else must be the same code
            if (value == 0.0f) {
                assertEquals(bits & 0x8000, back, "bits " + bits);
            } else {
                assertEquals(bits, back, "bits " + bits + ", value " + value);
            }
        }
    }
}
