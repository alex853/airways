package net.simforge.airways2.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Storage<T> {
    private static final Logger log = LoggerFactory.getLogger(Storage.class);

    private static final int recordHeaderSize = 1;

    // existing or deleted record
    /** @noinspection unused*/
    private static final int RECORD_EXISTS = 0;
    private static final int RECORD_EMPTY_OR_DELETED = 1;

    private final String name;
    private final Instantiator<T> instantiator;
    /** @noinspection FieldCanBeLocal, unused */
    private final DataType idDataType;
    private final DataField[] dataFields;
    private final int recordSize;

    private final DataField[] sortedDataFields;
    private final int[] sortedDataFieldHashs;

    // todo ak3 some header:
    //   version 1b
    //   record count 4b
    //   record size 2b
    //   current time millis 8b
    private byte[] data = new byte[0];

    private Storage(final String name,
                    final Instantiator<T> instantiator,
                    final DataType idDataType,
                    final DataField[] dataFields) {
        checkNotNull(dataFields);

        this.name = name;
        this.instantiator = instantiator;
        this.idDataType = idDataType;
        this.dataFields = dataFields; // todo ak3 check all required fields are initialised correctly
        this.recordSize = recordHeaderSize + dataFields[dataFields.length - 1].offsetPlusSize();

        sortedDataFields = Arrays.copyOf(dataFields, dataFields.length);
        Arrays.sort(sortedDataFields, Comparator.comparingInt(Object::hashCode));
        sortedDataFieldHashs = new int[sortedDataFields.length];
        for (int i = 0; i < sortedDataFields.length; i++) {
            sortedDataFieldHashs[i] = sortedDataFields[i].hashCode();
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        final Path dataPath = rootPath.resolve(name);
        if (!Files.exists(dataPath)) {
            reset();
            return;
        }
        data = Files.readAllBytes(dataPath);
    }

    public void save(final Path rootPath) throws IOException {
        final Path dataPath = rootPath.resolve(name);
        Files.write(dataPath, data,  StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,  StandardOpenOption.WRITE);
    }

    public DataField getDataField(final int fieldIndex) {
        return dataFields[fieldIndex];
    }

    public void reset() {
        data = new byte[0];
    }

    /**
     * count of all records - existing and deleted
     */
    private int getTotalStoredRecordCount() {
        return data.length / recordSize;
    }

    // todo ak3 optimization - that counts can be stored in headers or counted somehow else - after loading and any change
    /**
     * count of existing records
     */
    public int getCount() {
        int count = 0;
        for (int recordId = 1; recordId <= getTotalStoredRecordCount(); recordId++) {
            if (isDeleted(recordId)) {
                continue;
            }

            count++;
        }
        return count;
    }

    public Collection<T> all() {
        final List<T> result = new ArrayList<>();
        for (int recordId = 1; recordId <= getTotalStoredRecordCount(); recordId++) {
            if (isDeleted(recordId)) {
                continue;
            }

            result.add(instantiator.create(recordId));
        }
        return result;
    }

    public Optional<T> byId(final int recordId) {
        if (isOutOfBounds(recordId)) {
            return Optional.empty();
        }
        if (isDeleted(recordId)) {
            return Optional.empty();
        }
        return Optional.of(instantiator.create(recordId));
    }

    @Deprecated
    public Collection<T> filter(final Predicate<T> condition) {
        final List<T> result = new ArrayList<>();
        for (int recordId = 1; recordId <= getTotalStoredRecordCount(); recordId++) {
            if (isDeleted(recordId)) {
                continue;
            }

            final T instance = instantiator.create(recordId);
            if (!condition.test(instance)) {
                continue;
            }

            result.add(instance);
        }
        return result;
    }

    @Deprecated
    public Optional<T> findFirst(final Predicate<T> condition) {
        for (int recordId = 1; recordId <= getTotalStoredRecordCount(); recordId++) {
            if (isDeleted(recordId)) {
                continue;
            }

            final T instance = instantiator.create(recordId);
            if (condition.test(instance)) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    // todo ak0 rename when all .all() usages will be wiped out
    public Stream<T> all1() {
        return IntStream.rangeClosed(1, getTotalStoredRecordCount())
                .filter(recordId -> !isDeleted(recordId))
                .mapToObj(recordId -> instantiator.create(recordId));
    }

    // todo ak0 rename when all .filter() usages will be wiped out
    public Stream<T> filter1(final Condition<T> condition) {
        return IntStream.rangeClosed(1, getTotalStoredRecordCount())
                .filter(recordId -> !isDeleted(recordId))
                .filter(recordId -> condition.test(recordId))
                .mapToObj(recordId -> instantiator.create(recordId));
    }

    // todo ak0 rename when all .findFirst() usages will be wiped out
    public Optional<T> findFirst1(final Condition<T> condition) {
        return IntStream.rangeClosed(1, getTotalStoredRecordCount())
                .filter(recordId -> !isDeleted(recordId))
                .filter(recordId -> condition.test(recordId))
                .mapToObj(recordId -> instantiator.create(recordId))
                .findFirst();
    }
    
    public int addRecord() {
        // todo ak3 optimization - ids of deleted records can be temporarily stored somewhere to improve performance
        int deletedRecordId = 0;
        for (int recordId = 1; recordId <= getTotalStoredRecordCount(); recordId++) {
            if (!isDeleted(recordId)) {
                continue;
            }

            deletedRecordId = recordId;
            break;
        }

        if (deletedRecordId != 0) {
            data[getRecordHeaderOffset(deletedRecordId)] = RECORD_EXISTS;
            return deletedRecordId;
        } else {
            final int addedRecordId = getTotalStoredRecordCount() + 1;
            // todo ak3 to check if new id is possible according to idDataType
            final byte[] newData = new byte[data.length + recordSize];
            System.arraycopy(data, 0, newData, 0, data.length);
            data = newData;
            return addedRecordId;
        }
    }

    public void deleteRecord(final int recordId) {
        checkRecordIdInBounds(recordId);
        checkRecordIdIsNotDeleted(recordId);

        data[getRecordHeaderOffset(recordId)] = RECORD_EMPTY_OR_DELETED;
        Arrays.fill(data, getRecordHeaderOffset(recordId) + 1, getRecordHeaderOffset(recordId+1), (byte) 0);

        vacuumDeletedRecordsAtTail();
    }

    private void vacuumDeletedRecordsAtTail() {
        int lastNonDeletedRecordId = -1;
        int currRecordId = getTotalStoredRecordCount();
        while (currRecordId >= 1) {
            if (!isDeleted(currRecordId)) {
                lastNonDeletedRecordId = currRecordId;
                break;
            }
            currRecordId--;
        }

        if (lastNonDeletedRecordId == -1) {
            return;
        }

        final int recordsToRemove = getTotalStoredRecordCount() - lastNonDeletedRecordId;
        if (recordsToRemove == 0) {
            return;
        }

        log.warn("vacuuming {} storage, dropping {} records at tail, new total stored record count {}",
                name, recordsToRemove, lastNonDeletedRecordId);

        final byte[] newData = new byte[lastNonDeletedRecordId * recordSize];
        System.arraycopy(data, 0, newData, 0, newData.length);
        data = newData;
    }

    public void printDeletedRecordInfo() {
        final int totalRecords = getTotalStoredRecordCount();

        int deletedRecords = 0;
        int highestDeletedId = -1;

        int currentSequence = -1;

        int longestDeletedSequence = -1;
        int highestIdOfLongestDeletedSequence = -1;

        for (int recordId = 1; recordId <= totalRecords; recordId++) {
            if (isDeleted(recordId)) {
                deletedRecords++;
                highestDeletedId = Math.max(highestDeletedId, recordId);

                if (currentSequence == -1) {
                    currentSequence = 1;
                } else {
                    currentSequence++;
                }
            } else {
                if ((currentSequence != -1) && (currentSequence >= longestDeletedSequence)) {
                    longestDeletedSequence = currentSequence;
                    highestIdOfLongestDeletedSequence = recordId - 1;

                    currentSequence = -1;
                }
            }
        }

        if ((currentSequence != -1) && (currentSequence >= longestDeletedSequence)) {
            longestDeletedSequence = currentSequence;
            highestIdOfLongestDeletedSequence = totalRecords;
        }

        log.info("deleted record info for {} storage - total stored {}, deleted {}, highest deleted id {}, longest deleted sequence {}, highest id of longest deleted sequence {}",
                name, totalRecords, deletedRecords, highestDeletedId, longestDeletedSequence, highestIdOfLongestDeletedSequence);
    }

    public int getAsInt(final int recordId, final DataField dataField) {
        checkRecordIdInBounds(recordId);
        checkRecordIdIsNotDeleted(recordId);
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        return getAsIntUnsafe(recordId, dataField);
    }

    public int getAsIntUnsafe(final int recordId, final DataField dataField) {
        checkRecordIdInBounds(recordId);
        checkRecordIdIsNotDeleted(recordId);
        checkNotNull(dataField, "dataField should not be null");

        final int fieldOffset = getFieldOffset(recordId, dataField);

        return switch (dataField.dataType()) {
            case Unsigned8bit -> Byte.toUnsignedInt(data[fieldOffset]);
            case Unsigned16bit -> (Byte.toUnsignedInt(data[fieldOffset]) << 8)
                    + (Byte.toUnsignedInt(data[fieldOffset + 1]));
            case Unsigned24bit -> (Byte.toUnsignedInt(data[fieldOffset]) << 16)
                    + (Byte.toUnsignedInt(data[fieldOffset + 1]) << 8)
                    + (Byte.toUnsignedInt(data[fieldOffset + 2]));
            case Signed32bit -> getIntAtOffset(fieldOffset);
            default -> throw new IllegalStateException("Unexpected dataField dataType: " + dataField.dataType());
        };
    }

    public float getAsFloat(final int recordId, final DataField dataField) {
        checkRecordIdInBounds(recordId);
        checkRecordIdIsNotDeleted(recordId);
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        final int fieldOffset = getFieldOffset(recordId, dataField);

        switch (dataField.dataType()) {
            case Float -> {
                final int intBits = getIntAtOffset(fieldOffset);
                return Float.intBitsToFloat(intBits);
            }
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
        checkRecordIdIsNotDeleted(recordId);
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        final int fieldOffset = getFieldOffset(recordId, dataField);

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
        checkRecordIdIsNotDeleted(recordId);
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        setUnsafe(recordId, dataField, value);
    }

    public void setUnsafe(final int recordId, final DataField dataField, final int value) {
        checkRecordIdInBounds(recordId);
        checkRecordIdIsNotDeleted(recordId);
        checkNotNull(dataField, "dataField should not be null");

        final int fieldOffset = getFieldOffset(recordId, dataField);

        switch (dataField.dataType()) {
            case Unsigned8bit -> {
                checkArgument(0 <= value && value <= 255, "expected range is [0..255]");
                data[fieldOffset] = (byte) value;
            }
            case Unsigned16bit -> {
                checkArgument(0 <= value && value <= 65535, "expected range is [0..65353]");
                data[fieldOffset] = (byte) (value >> 8);
                data[fieldOffset + 1] = (byte) value;
            }
            case Unsigned24bit -> {
                checkArgument(0 <= value && value <= 16777215, "expected range is [0..16777215]");
                data[fieldOffset] = (byte) (value >> 16);
                data[fieldOffset + 1] = (byte) (value >> 8);
                data[fieldOffset + 2] = (byte) value;
            }
            case Signed32bit -> setIntAtOffset(fieldOffset, value); // no need to check if value is within limits
            default -> throw new IllegalStateException("Unsupported data type: " + dataField.dataType());
        }
    }

    public void set(final int recordId, final DataField dataField, final float value) {
        checkRecordIdInBounds(recordId);
        checkRecordIdIsNotDeleted(recordId);
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        final int fieldOffset = getFieldOffset(recordId, dataField);

        switch (dataField.dataType()) {
            case Float -> {
                final int intBits = Float.floatToIntBits(value);
                setIntAtOffset(fieldOffset, intBits);
            }
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
        checkRecordIdIsNotDeleted(recordId);
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);

        final int fieldOffset = getFieldOffset(recordId, dataField);

        if (dataField.dataType() == DataType.PlainString) {
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
        } else {
            throw new IllegalStateException("Unsupported data type: " + dataField.dataType());
        }
    }

    private int getRecordHeaderOffset(final int recordId) {
        return (recordId-1) * recordSize;
    }

    private int getFieldOffset(final int recordId, final DataField dataField) {
        return getRecordHeaderOffset(recordId) + recordHeaderSize + dataField.offset();
    }

    private int getIntAtOffset(final int fieldOffset) {
        return (data[fieldOffset] << 24)
                + (Byte.toUnsignedInt(data[fieldOffset + 1]) << 16)
                + (Byte.toUnsignedInt(data[fieldOffset + 2]) << 8)
                + (Byte.toUnsignedInt(data[fieldOffset + 3]));
    }

    private void setIntAtOffset(final int fieldOffset, final int value) {
        data[fieldOffset] = (byte) (value >> 24);
        data[fieldOffset + 1] = (byte) (value >> 16);
        data[fieldOffset + 2] = (byte) (value >> 8);
        data[fieldOffset + 3] = (byte) value;
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
        if (isOutOfBounds(recordId)) {
            throw new IllegalArgumentException("RecordId is out of range: " + recordId);
        }
    }

    private boolean isOutOfBounds(int recordId) {
        return recordId < 1 || recordId > getTotalStoredRecordCount();
    }

    private void checkRecordIdIsNotDeleted(final int recordId) {
        if (isDeleted(recordId)) {
            throw new IllegalArgumentException("RecordId is deleted: " + recordId);
        }
    }

    private boolean isDeleted(final int recordId) {
        final int recordHeader = data[getRecordHeaderOffset(recordId)];
        return recordHeader == RECORD_EMPTY_OR_DELETED;
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
            return new Storage<>(name, instantiator, idDataType, dataFields.toArray(new DataField[0]));
        }
    }

    public interface Instantiator<T> {
        T create(int recordId);
    }

    public interface Condition<T> {
        boolean test(int recordId);
    }

    public Condition<T> nextForHeartbeatCondition(final DataField heartbeatTimeField, final int worldTime) {
        checkNotNull(heartbeatTimeField);
        checkDataFieldIsInStorage(heartbeatTimeField);
        checkArgument(heartbeatTimeField.dataType() == DataType.Signed32bit);
        checkArgument(worldTime > 0);

        return recordId -> {
            final int heartbeatTime = getAsInt(recordId, heartbeatTimeField);
            return heartbeatTime <= worldTime && heartbeatTime != 0;
        };
    }
}
