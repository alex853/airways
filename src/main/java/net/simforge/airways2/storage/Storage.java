package net.simforge.airways2.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Storage<T> {
    private static final Path rootPath = Paths.get("./storage");

    private final String name;
    private final Path dataPath;
    private final Instantiator<T> instantiator;
    private final DataType idDataType;
    private final DataField[] dataFields;
    private final int recordSize;

    private final DataField[] sortedDataFields;
    private final int[] sortedDataFieldHashs;
    // todo ak add record status byte before record:
    //   empty (can be reused)
    //   stored

    // todo ak some header:
    //   version 1b
    //   record count 4b
    //   record size 2b
    //   current time millis 8b
    private byte[] data = new byte[0];

    private Storage(final String name,
                    final Instantiator<T> instantiator,
                    final DataType idDataType,
                    final DataField[] dataFields) {
        this.name = name;
        this.dataPath = name != null ? rootPath.resolve(name) : null;
        this.instantiator = instantiator;
        this.idDataType = idDataType;
        this.dataFields = dataFields; // todo ak check all required fields are initialised correctly
        this.recordSize = dataFields[dataFields.length - 1].offsetPlusSize();

        sortedDataFields = Arrays.copyOf(dataFields, dataFields.length);
        Arrays.sort(sortedDataFields, Comparator.comparingInt(Object::hashCode));
        sortedDataFieldHashs = new int[sortedDataFields.length];
        for (int i = 0; i < sortedDataFields.length; i++) {
            sortedDataFieldHashs[i] = sortedDataFields[i].hashCode();
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }

    public void loadIfExists() throws IOException {
        if (!Files.exists(dataPath)) {
            return;
        }
        data = Files.readAllBytes(dataPath);
    }

    public void save() throws IOException {
        // todo ak implement safe saving
        //   write to ./data/cities.<current millis>
        //   rename ./data/cities to ./data/cities.<millis from the header!!!>
        //   rename ./data/cities.<current millis> to ./data/cities
        // todo ak update header accordingly
        Files.write(dataPath, data,  StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,  StandardOpenOption.WRITE);
    }

    public DataField getDataField(final int fieldIndex) {
        return dataFields[fieldIndex];
    }

    public int getCount() {
        // todo ak modify when header introduced
        return data.length / recordSize;
    }

    public void reset() {
        data = new byte[0];
    }

    public Optional<T> byId(final int recordId) {
        checkRecordIdInBounds(recordId);
        // todo ak check not deleted
        return Optional.of(instantiator.create(recordId));
    }

    public Optional<T> findFirst(final Predicate<T> condition) {
        for (int recordId = 1; recordId <= getCount(); recordId++) {
            // todo ak check not deleted
            final T instance = instantiator.create(recordId);
            if (condition.test(instance)) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    // todo ak to check if new id is possible according to idDataType
    public int addRecord() {
        final int recordId = getCount() + 1;
        final byte[] newData = new byte[data.length + recordSize];
        System.arraycopy(data, 0, newData, 0, data.length);
        data = newData;
        return recordId;
    }

    public int getAsInt(final int recordId, final DataField dataField) {
        checkRecordIdInBounds(recordId);
        // todo ak check if record deleted
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        final int fieldOffset = (recordId-1) * recordSize + dataField.offset();

        return switch (dataField.dataType()) {
            case Unsigned8bit -> Byte.toUnsignedInt(data[fieldOffset]);
            case Signed32bit -> (data[fieldOffset] << 24)
                    + (Byte.toUnsignedInt(data[fieldOffset + 1]) << 16)
                    + (Byte.toUnsignedInt(data[fieldOffset + 2]) << 8)
                    + (Byte.toUnsignedInt(data[fieldOffset + 3]));
            default -> throw new IllegalStateException("Unexpected dataField dataType: " + dataField.dataType());
        };
    }

    public float getAsFloat(final int recordId, final DataField dataField) {
        checkRecordIdInBounds(recordId);
        // todo ak check if record deleted
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        final int fieldOffset = (recordId-1) * recordSize + dataField.offset();

        switch (dataField.dataType()) {
            case LatLong24bit -> {
                final int maxValue = 128 * 256 * 256 - 1;
                final int intValue = (data[fieldOffset] << 16)
                        + (Byte.toUnsignedInt(data[fieldOffset + 1]) << 8)
                        + (Byte.toUnsignedInt(data[fieldOffset + 2]));
                return (180.0f * intValue) / maxValue;
            }
            case LatLong16bit -> {
                final int maxValue = 128 * 256 - 1;
                final int intValue = (data[fieldOffset] << 8)
                        + (Byte.toUnsignedInt(data[fieldOffset + 1]));
                return (180.0f * intValue) / maxValue;
            }
            default -> throw new IllegalStateException("Unsupported data type: " + dataField.dataType());
        }
    }

    public String getAsString(final int recordId, final DataField dataField) {
        checkRecordIdInBounds(recordId);
        // todo ak check if record deleted
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        final int fieldOffset = (recordId-1) * recordSize + dataField.offset();

        //noinspection SwitchStatementWithTooFewBranches
        switch (dataField.dataType()) {
            case PlainString -> {
                final int len = data[fieldOffset] & 0xFF;
                if (len == 255) {
                    return null;
                }
                return new String(data, fieldOffset + 1, len);
            }
            default -> throw new IllegalStateException("Unsupported data type: " + dataField.dataType());
        }
    }

    public void set(final int recordId, final DataField dataField, final int value) {
        checkRecordIdInBounds(recordId);
        // todo ak check if record deleted
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        final int fieldOffset = (recordId-1) * recordSize + dataField.offset();

        switch (dataField.dataType()) {
            case Unsigned8bit -> {
                if (value < 0 || value > 255) {
                    throw new IllegalArgumentException("Value out of range: " + value);
                }
                data[fieldOffset] = (byte) value;
            }
            case Signed32bit -> {
                // no need to check if value is within limits
                data[fieldOffset] = (byte) (value >> 24);
                data[fieldOffset + 1] = (byte) (value >> 16);
                data[fieldOffset + 2] = (byte) (value >> 8);
                data[fieldOffset + 3] = (byte) value;
            }
            default -> throw new IllegalStateException("Unsupported data type: " + dataField.dataType());
        }
    }

    public void set(final int recordId, final DataField dataField, final float value) {
        checkRecordIdInBounds(recordId);
        // todo ak check if record deleted
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        final int fieldOffset = (recordId-1) * recordSize + dataField.offset();

        switch (dataField.dataType()) {
            case LatLong24bit -> {
                checkArgument(-180 <= value && value <= 180, "Lat/Long Value out of range: " + value);
                final int maxValue = 128 * 256 * 256 - 1;
                final int intValue = (int) (value / 180.0 * maxValue);
                data[fieldOffset] = (byte) (intValue >> 16);
                data[fieldOffset + 1] = (byte) (intValue >> 8);
                data[fieldOffset + 2] = (byte) intValue;
            }
            case LatLong16bit -> {
                checkArgument(-180 <= value && value <= 180, "Lat/Long Value out of range: " + value);
                final int maxValue = 128 * 256 - 1;
                final int intValue = (int) (value / 180.0 * maxValue);
                data[fieldOffset] = (byte) (intValue >> 8);
                data[fieldOffset + 1] = (byte) intValue;
            }
            default -> throw new IllegalStateException("Unsupported data type: " + dataField.dataType());
        }
    }

    public void set(final int recordId, final DataField dataField, final String value) {
        checkRecordIdInBounds(recordId);
        // todo ak check if record deleted
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        final int fieldOffset = (recordId-1) * recordSize + dataField.offset();

        switch (dataField.dataType()) {
            case PlainString:
                if (value != null && value.length() > dataField.length()) {
                    throw new IllegalArgumentException("Value is too long, actual length " + value.length() + " while max length is " + dataField.length());
                }
                Arrays.fill(data, fieldOffset, fieldOffset + dataField.size(), (byte) 0);
                if (value == null) {
                    data[fieldOffset] = (byte) 255;
                } else {
                    data[fieldOffset] = (byte) value.length();
                    System.arraycopy(value.getBytes(), 0, data, fieldOffset + 1, value.length());
                }
                break;
            default:
                throw new IllegalStateException("Unsupported data type: " + dataField.dataType());
        }
    }

    private void checkDataFieldIsInStorage(final DataField dataField) {
        final int hash = dataField.hashCode();
        final int index = Arrays.binarySearch(sortedDataFieldHashs, hash);
        if (index < 0) {
            throw new IllegalArgumentException("can't find dataField by hash");
        }
        final DataField dataFieldByHash = sortedDataFields[index];
        if (dataFieldByHash != dataField) {
            throw new IllegalArgumentException("dataField is not the same as dataField found by hash");
        }
    }

    private void checkRecordIdInBounds(final int recordId) {
        if (recordId < 1 || recordId > getCount()) {
            throw new IllegalArgumentException("RecordId is out of range: " + recordId);
        }
    }

    public static class Builder<T> {
        private String name;
        private Instantiator<T> instantiator;
        private DataType idDataType;
        private final LinkedList<DataField> dataFields = new LinkedList<>();

        private Builder() {
        }

        public Builder<T> name(final String name) {
            this.name = name;
            return this;
        }

        public Builder<T> withInstantiator(final Instantiator<T> instantiator) {
            this.instantiator = instantiator;
            return this;
        }

        public Builder<T> withIdOf(final DataType idDataType) {
            this.idDataType = idDataType;
            return this;
        }

        public Builder<T> withDataField(final DataField dataField) {
            dataFields.add(dataFields.isEmpty()
                    ? dataField
                    : dataField.after(dataFields.getLast()));
            return this;
        }

        public Storage<T> build() {
            if (dataFields.isEmpty()) {
                throw new IllegalStateException("dataFields not set");
            }
            return new Storage<T>(name, instantiator, idDataType, dataFields.toArray(new DataField[0]));
        }
    }

    public interface Instantiator<T> {
        T create(int recordId);
    }
}
