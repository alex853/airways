package net.simforge.airways2.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class StorageDataFieldsTest {
    private final Storage storage = Storage.builder()
            .withDataField(DataField.of(DataType.Signed32bit))
            .withDataField(DataField.of(DataType.LatLong24bit))
            .withDataField(DataField.of(DataType.PlainString).length(20))
            .build();

    private final DataField signed32bitFieldNotInStorage = DataField.of(DataType.Signed32bit);
    private final DataField latLong24bitFieldNotInStorage = DataField.of(DataType.LatLong24bit);
    private final DataField plainString20FieldNotInStorage = DataField.of(DataType.PlainString).length(20);

    @BeforeEach
    public void setUp() {
        storage.reset();
    }

    @Test
    public void test_data_field_not_in_storage() {
        final int recordId = storage.addRecord();

        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, signed32bitFieldNotInStorage, 123));
        assertThrows(IllegalArgumentException.class, () -> storage.getAsInt(recordId, signed32bitFieldNotInStorage));

        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, latLong24bitFieldNotInStorage, 123.0f));
        assertThrows(IllegalArgumentException.class, () -> storage.getAsFloat(recordId, latLong24bitFieldNotInStorage));

        assertThrows(IllegalArgumentException.class, () -> storage.set(recordId, plainString20FieldNotInStorage, "123"));
        assertThrows(IllegalArgumentException.class, () -> storage.getAsString(recordId, plainString20FieldNotInStorage));
    }

}