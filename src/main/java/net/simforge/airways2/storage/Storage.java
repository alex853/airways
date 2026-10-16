package net.simforge.airways2.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class Storage<T> {
    private static final Logger log = LoggerFactory.getLogger(Storage.class);

    private static final int recordHeaderSize = 1;

    // existing or deleted record
    /** @noinspection unused*/
    private static final int RECORD_EXISTS = 0;
    private static final int RECORD_EMPTY_OR_DELETED = 1;

    // a deleted record keeps the time of deletion in minutes from this moment in the first 4 bytes of its body
    private static final long QUARANTINE_EPOCH_MILLIS = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli();
    private static final int DELETED_AT_SIZE = 4;

    /**
     * How long a deleted record is not reused if the builder does not say another value.
     */
    public static final Duration DEFAULT_DELETED_RECORD_QUARANTINE = Duration.ofDays(1);

    private final String name;
    private final Instantiator<T> instantiator;
    /** @noinspection FieldCanBeLocal, unused */
    private final DataType idDataType;
    private final DataField[] dataFields;
    private final int recordSize;

    private final DataField[] sortedDataFields;
    private final int[] sortedDataFieldHashs;

    // optional index of the records by their heartbeat time, see Builder#withHeartbeatIndex
    private final DataField heartbeatIndexField;
    private final HeartbeatIndex heartbeatIndex;

    // todo ak3 some header:
    //   version 1b
    //   record count 4b
    //   record size 2b
    //   current time millis 8b
    private byte[] data = new byte[0];

    private long deletedRecordQuarantineMillis;
    private final LongSupplier clock;
    // The moment of the last change of the storage (loading, adding or deleting a record, any setter). It is read by other
    // threads without any lock, a cache of the data of the storage compares it with the value it was built with.
    // Every change makes the value strictly greater than the previous one, even if the clock has not moved.
    private volatile long lastChangedAtMillis;
    private final boolean canStoreDeletedAt;
    // deleted records which are known to the storage: {recordId, deletedAt}, the oldest deletion first
    private final ArrayDeque<int[]> deletedRecords = new ArrayDeque<>();

    private Storage(final String name,
                    final Instantiator<T> instantiator,
                    final DataType idDataType,
                    final DataField[] dataFields,
                    final long deletedRecordQuarantineMillis,
                    final boolean deletedRecordQuarantineIsExplicit,
                    final LongSupplier clock,
                    final int heartbeatIndexFieldIndex) {
        checkNotNull(dataFields);

        this.name = name;
        this.instantiator = instantiator;
        this.idDataType = idDataType;
        this.dataFields = dataFields; // todo ak3 check all required fields are initialised correctly
        this.recordSize = recordHeaderSize + dataFields[dataFields.length - 1].offsetPlusSize();

        this.deletedRecordQuarantineMillis = deletedRecordQuarantineMillis;
        this.clock = clock;
        this.lastChangedAtMillis = clock.getAsLong();
        this.canStoreDeletedAt = recordSize - recordHeaderSize >= DELETED_AT_SIZE;
        if (deletedRecordQuarantineMillis > 0 && !canStoreDeletedAt) {
            if (deletedRecordQuarantineIsExplicit) {
                throw new IllegalStateException("Storage " + name + " has records too small to keep the deletion time, "
                        + "at least " + DELETED_AT_SIZE + " bytes of data are required for the quarantine");
            }
            // the default quarantine is just not applicable to such records
            this.deletedRecordQuarantineMillis = 0;
        }

        if (heartbeatIndexFieldIndex >= 0) {
            checkArgument(heartbeatIndexFieldIndex < dataFields.length, "heartbeat index field is out of the data fields");
            checkArgument(dataFields[heartbeatIndexFieldIndex].dataType() == DataType.Signed32bit,
                    "heartbeat index field must be Signed32bit, it is " + dataFields[heartbeatIndexFieldIndex].dataType());
            this.heartbeatIndexField = dataFields[heartbeatIndexFieldIndex];
            this.heartbeatIndex = new HeartbeatIndex();
        } else {
            this.heartbeatIndexField = null;
            this.heartbeatIndex = null;
        }

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
        rebuildDeletedRecords();
        rebuildHeartbeatIndex();
        markChanged();
    }

    /**
     * The moment of the last change of the storage: the loading, adding or deleting of a record, any setter. A new value is
     * always greater than the previous one. It can be read from any thread, without any lock.
     */
    public long getLastChangedAtMillis() {
        return lastChangedAtMillis;
    }

    private void markChanged() {
        lastChangedAtMillis = Math.max(clock.getAsLong(), lastChangedAtMillis + 1);
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
        deletedRecords.clear();
        if (heartbeatIndex != null) {
            heartbeatIndex.clear();
        }
        markChanged();
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

    public Optional<T> byId(final int recordId) {
        if (isOutOfBounds(recordId)) {
            return Optional.empty();
        }
        if (isDeleted(recordId)) {
            return Optional.empty();
        }
        return Optional.of(instantiator.create(recordId));
    }

    public Stream<T> all() {
        return IntStream.rangeClosed(1, getTotalStoredRecordCount())
                .filter(recordId -> !isDeleted(recordId))
                .mapToObj(instantiator::create);
    }

    public Stream<T> filter(final Condition<T> condition) {
        return IntStream.rangeClosed(1, getTotalStoredRecordCount())
                .filter(recordId -> !isDeleted(recordId))
                .filter(condition::test)
                .mapToObj(instantiator::create);
    }

    public Optional<T> findFirst(final Condition<T> condition) {
        final OptionalInt first = IntStream.rangeClosed(1, getTotalStoredRecordCount())
                .filter(recordId -> !isDeleted(recordId))
                .filter(condition::test)
                .findFirst();
        if (first.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(instantiator.create(first.getAsInt()));
    }
    
    /**
     * Adds a new record. A deleted record is reused only when its quarantine is over, otherwise the storage grows.
     */
    public int addRecord() {
        final int[] oldest = deletedRecords.peekFirst();
        if (oldest != null && isQuarantineOver(oldest[1])) {
            deletedRecords.pollFirst();

            final int recordId = oldest[0];
            final int headerOffset = getRecordHeaderOffset(recordId);
            // the body keeps the 'deleted at' time, it must be clean for the new record
            Arrays.fill(data, headerOffset + recordHeaderSize, headerOffset + recordSize, (byte) 0);
            data[headerOffset] = RECORD_EXISTS;
            markChanged();
            return recordId;
        }

        final int addedRecordId = getTotalStoredRecordCount() + 1;
        // todo ak1 to check if new id is possible according to idDataType
        final byte[] newData = new byte[data.length + recordSize];
        System.arraycopy(data, 0, newData, 0, data.length);
        data = newData;
        markChanged();
        return addedRecordId;
    }

    /**
     * Deletes a record. The storage is not shrunk, the record id is not reused until the quarantine is over.
     */
    public void deleteRecord(final int recordId) {
        checkRecordIdInBounds(recordId);
        checkRecordIdIsNotDeleted(recordId);

        if (heartbeatIndex != null) {
            heartbeatIndex.remove(readHeartbeatIndexField(recordId), recordId); // before the body is cleared
        }

        final int headerOffset = getRecordHeaderOffset(recordId);
        data[headerOffset] = RECORD_EMPTY_OR_DELETED;
        Arrays.fill(data, headerOffset + recordHeaderSize, headerOffset + recordSize, (byte) 0);

        final int deletedAt = currentDeletedAtMinutes();
        if (canStoreDeletedAt) {
            setIntAtOffset(headerOffset + recordHeaderSize, deletedAt);
        }
        deletedRecords.addLast(new int[]{recordId, deletedAt});
        markChanged();
    }

    /**
     * Count of deleted records which can not be reused yet because of the quarantine
     */
    public int getQuarantinedDeletedRecordCount() {
        return (int) deletedRecords.stream().filter(r -> !isQuarantineOver(r[1])).count();
    }

    /**
     * Count of deleted records which are ready to be reused
     */
    public int getReusableDeletedRecordCount() {
        return (int) deletedRecords.stream().filter(r -> isQuarantineOver(r[1])).count();
    }

    public long getDeletedRecordQuarantineMillis() {
        return deletedRecordQuarantineMillis;
    }

    private boolean isQuarantineOver(final int deletedAtMinutes) {
        if (deletedRecordQuarantineMillis <= 0) {
            return true;
        }
        final long deletedAtMillis = QUARANTINE_EPOCH_MILLIS + deletedAtMinutes * 60_000L;
        final long ageMillis = clock.getAsLong() - deletedAtMillis;
        return ageMillis >= deletedRecordQuarantineMillis;
    }

    private int currentDeletedAtMinutes() {
        final long minutes = (clock.getAsLong() - QUARANTINE_EPOCH_MILLIS) / 60_000L;
        // zero means 'unknown' (records deleted before the quarantine was introduced)
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, minutes));
    }

    /**
     * Rebuilds the list of deleted records from the data, the oldest deletion first.
     * Deleted records without the 'deleted at' time are treated as deleted just now, so they are quarantined as well.
     */
    private void rebuildDeletedRecords() {
        deletedRecords.clear();

        final List<int[]> found = new ArrayList<>();
        final int now = currentDeletedAtMinutes();
        for (int recordId = 1; recordId <= getTotalStoredRecordCount(); recordId++) {
            if (!isDeleted(recordId)) {
                continue;
            }

            int deletedAt = canStoreDeletedAt ? getIntAtOffset(getRecordHeaderOffset(recordId) + recordHeaderSize) : 0;
            if (deletedAt <= 0) {
                deletedAt = now;
                if (canStoreDeletedAt) {
                    setIntAtOffset(getRecordHeaderOffset(recordId) + recordHeaderSize, deletedAt);
                }
            }
            found.add(new int[]{recordId, deletedAt});
        }

        found.sort(Comparator.<int[]>comparingInt(r -> r[1]).thenComparingInt(r -> r[0]));
        deletedRecords.addAll(found);
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
        log.info("deleted record quarantine for {} storage - quarantine {}, in quarantine {}, ready to be reused {}",
                name, Duration.ofMillis(deletedRecordQuarantineMillis), getQuarantinedDeletedRecordCount(), getReusableDeletedRecordCount());
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
        final boolean isHeartbeatField = heartbeatIndex != null && dataField == heartbeatIndexField;
        final int oldHeartbeatTime = isHeartbeatField ? getIntAtOffset(fieldOffset) : 0;

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

        if (isHeartbeatField) {
            heartbeatIndex.update(recordId, oldHeartbeatTime, value);
        }

        markChanged(); // after the data is written: whoever sees the new stamp sees the new data
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

        markChanged();
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
            markChanged();
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
        private long deletedRecordQuarantineMillis = DEFAULT_DELETED_RECORD_QUARANTINE.toMillis();
        private boolean deletedRecordQuarantineIsExplicit = false;
        private LongSupplier clock = System::currentTimeMillis;
        private int heartbeatIndexFieldIndex = -1;

        private Builder() {
        }

        /**
         * A deleted record is not reused during this time (one day by default, zero means 'reuse at once').
         * It requires at least 4 bytes of data in a record, the default one is skipped for smaller records.
         */
        public Builder<T> deletedRecordQuarantine(final Duration quarantine) {
            checkNotNull(quarantine);
            checkArgument(!quarantine.isNegative(), "quarantine should not be negative");
            this.deletedRecordQuarantineMillis = quarantine.toMillis();
            this.deletedRecordQuarantineIsExplicit = true;
            return this;
        }

        /**
         * Source of current time in milliseconds, it is intended for tests.
         */
        public Builder<T> clock(final LongSupplier clock) {
            this.clock = checkNotNull(clock);
            return this;
        }

        /**
         * The storage keeps an index of its records by the heartbeat time kept in the data field with this number (the order of
         * withDataField calls, from zero), the field must be Signed32bit, zero means 'no heartbeat'.
         * Then {@link Storage#nextForHeartbeat(int)} does not scan the storage.
         */
        public Builder<T> withHeartbeatIndex(final int dataFieldIndex) {
            checkArgument(dataFieldIndex >= 0, "dataFieldIndex should not be negative");
            this.heartbeatIndexFieldIndex = dataFieldIndex;
            return this;
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
            return new Storage<>(name, instantiator, idDataType, dataFields.toArray(new DataField[0]),
                    deletedRecordQuarantineMillis, deletedRecordQuarantineIsExplicit, clock, heartbeatIndexFieldIndex);
        }
    }

    public interface Instantiator<T> {
        T create(int recordId);
    }

    @SuppressWarnings("unused")
    public interface Condition<T> {
        boolean test(int recordId);

        static <T> Condition<T> and(Condition<T> condition1, Condition<T> condition2) {
            return recordId -> condition1.test(recordId) && condition2.test(recordId);
        }
    }

    /**
     * The record with the earliest heartbeat time, if that time is not later than the world time (zero heartbeat means 'none').
     * It uses the heartbeat index, so it does not scan the storage; the storage must be built with a heartbeat index.
     */
    public Optional<T> nextForHeartbeat(final int worldTime) {
        checkState(heartbeatIndex != null, "the storage %s has no heartbeat index", name);

        while (true) {
            final OptionalInt recordId = heartbeatIndex.firstDue(worldTime);
            if (recordId.isEmpty()) {
                return Optional.empty();
            }

            final int id = recordId.getAsInt();
            final int indexedHeartbeatTime = heartbeatIndex.earliestHeartbeatTime().orElseThrow();
            if (!isOutOfBounds(id) && !isDeleted(id) && readHeartbeatIndexField(id) == indexedHeartbeatTime) {
                return Optional.of(instantiator.create(id));
            }

            // must never happen, the index is maintained by the storage itself; the entry is dropped not to be stuck on it
            log.error("heartbeat index of {} storage is broken: record {} has no heartbeat time {}", name, id, indexedHeartbeatTime);
            heartbeatIndex.remove(indexedHeartbeatTime, id);
        }
    }

    /**
     * Entries of the heartbeat index in the order of processing, each is {heartbeat time, record id}. For diagnostics.
     */
    public List<int[]> heartbeatIndexEntries() {
        checkState(heartbeatIndex != null, "the storage %s has no heartbeat index", name);
        return heartbeatIndex.entries();
    }

    /**
     * Compares the heartbeat index with the data of the storage, a scan of all the records. It returns descriptions of the differences,
     * an empty list means the index is consistent. It is intended for tests and diagnostics.
     */
    public List<String> checkHeartbeatIndex() {
        checkState(heartbeatIndex != null, "the storage %s has no heartbeat index", name);

        final List<String> problems = new ArrayList<>();
        final Set<Long> expected = new HashSet<>();
        for (int recordId = 1; recordId <= getTotalStoredRecordCount(); recordId++) {
            if (isDeleted(recordId)) {
                continue;
            }
            final int heartbeatTime = readHeartbeatIndexField(recordId);
            if (heartbeatTime != 0) {
                expected.add(((long) heartbeatTime << 32) | recordId);
                if (!heartbeatIndex.contains(heartbeatTime, recordId)) {
                    problems.add("record " + recordId + " has heartbeat time " + heartbeatTime + " but it is not in the index");
                }
            }
        }
        for (final int[] entry : heartbeatIndex.entries()) {
            if (!expected.contains(((long) entry[0] << 32) | entry[1])) {
                problems.add("index entry {" + entry[0] + ", " + entry[1] + "} does not match any record");
            }
        }
        return problems;
    }

    private int readHeartbeatIndexField(final int recordId) {
        return getIntAtOffset(getFieldOffset(recordId, heartbeatIndexField));
    }

    private void rebuildHeartbeatIndex() {
        if (heartbeatIndex == null) {
            return;
        }

        heartbeatIndex.clear();
        for (int recordId = 1; recordId <= getTotalStoredRecordCount(); recordId++) {
            if (!isDeleted(recordId)) {
                heartbeatIndex.add(readHeartbeatIndexField(recordId), recordId);
            }
        }
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

    public Storage.Condition<T> byZeroHeartbeat(final DataField heartbeatTimeField) {
        return recordId -> getAsInt(recordId, heartbeatTimeField) == 0;
    }
}
