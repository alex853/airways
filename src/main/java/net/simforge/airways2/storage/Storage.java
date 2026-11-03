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

    /** The size of a page of a paged storage if the builder does not say another one. */
    public static final int DEFAULT_PAGE_SIZE_BYTES = 256 * 1024;

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
    // the highest record id which fits into the id data type
    private final long maxRecordId;
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
    // the bytes of the records: one array or the pages, see Builder#growthStep and Builder#pageSize; the records take the first storedRecordCount * recordSize bytes
    private final ByteStore data;
    private int storedRecordCount;

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
                    final int heartbeatIndexFieldIndex,
                    final int growthStep,
                    final int pageSizeBytes) {
        checkNotNull(dataFields);

        this.name = name;
        this.instantiator = instantiator;
        this.idDataType = idDataType;
        this.maxRecordId = maxRecordIdOf(idDataType);
        this.dataFields = dataFields; // todo ak3 check all required fields are initialised correctly
        this.recordSize = recordHeaderSize + dataFields[dataFields.length - 1].offsetPlusSize();
        this.data = pageSizeBytes > 0 ? new PagedByteStore(recordSize, pageSizeBytes) : new FlatByteStore((growthStep - 1) * recordSize);

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
        storedRecordCount = data.load(dataPath, recordSize);
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

    // The storage is written by one thread at a time (all the writes go under the write lock of the world), so the
    // read-modify-write is safe. The field is volatile only to make the new value visible to the threads which read it without a lock.
    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    private void markChanged() {
        lastChangedAtMillis =Math.max(clock.getAsLong(), lastChangedAtMillis + 1);
    }

    public void save(final Path rootPath) throws IOException {
        final Path dataPath = rootPath.resolve(name);
        // the reserve of the capacity is not saved, the file holds the records only
        data.save(dataPath, storedRecordCount * recordSize);
    }

    public DataField getDataField(final int fieldIndex) {
        return dataFields[fieldIndex];
    }

    public void reset() {
        data.clear();
        storedRecordCount = 0;
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
        return storedRecordCount;
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
    
    // A storage without the id data type (a bare test storage) is limited by int only.
    private static long maxRecordIdOf(final DataType idDataType) {
        if (idDataType == null) {
            return Integer.MAX_VALUE;
        }
        return switch (idDataType) {
            case Unsigned8bit, Unsigned16bit, Unsigned24bit, Signed32bit -> idDataType.maxValue();
            default -> throw new IllegalArgumentException("Record id data type must be an integer one, it is " + idDataType);
        };
    }

    /**
     * Adds a new record. A deleted record is reused only when its quarantine is over, otherwise the storage grows.
     * If the new id does not fit into the id data type of the storage, the storage is full and IllegalStateException is thrown.
     */
    public int addRecord() {
        final int[] oldest = deletedRecords.peekFirst();
        if (oldest != null && isQuarantineOver(oldest[1])) {
            deletedRecords.pollFirst();

            final int recordId = oldest[0];
            final int headerOffset = getRecordHeaderOffset(recordId);
            // the body keeps the 'deleted at' time, it must be clean for the new record
            data.fill(headerOffset + recordHeaderSize, headerOffset + recordSize, (byte) 0);
            data.set(headerOffset, (byte) RECORD_EXISTS);
            markChanged();
            return recordId;
        }

        final int addedRecordId = getTotalStoredRecordCount() + 1;
        // a reused id was checked when it was given, only a new id can be out of the id data type
        if (addedRecordId > maxRecordId) {
            throw new IllegalStateException("Storage " + name + " is full: record id " + addedRecordId
                    + " does not fit into " + idDataType + " (max id is " + maxRecordId + ")");
        }
        final int requiredLength = addedRecordId * recordSize;
        // the array grows by growthStep records at once, the pages are added one by one and nothing is copied
        data.ensureLength(requiredLength);
        storedRecordCount = addedRecordId;
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
        data.set(headerOffset, (byte) RECORD_EMPTY_OR_DELETED);
        data.fill(headerOffset + recordHeaderSize, headerOffset + recordSize, (byte) 0);

        final int deletedAt = currentDeletedAtMinutes();
        if (canStoreDeletedAt) {
            setIntAtOffset(headerOffset + recordHeaderSize, deletedAt);
        }
        deletedRecords.addLast(new int[]{recordId, deletedAt});
        markChanged();
    }

    /**
     * Tries to cut the tail of the storage: while the last page has no live record (its records are all deleted, in the quarantine or not, or have not been created yet), the
     * page is released and the records of it are forgotten, with their places in the queue of the deleted ones. The quarantine does not hold the page: the ids of the dropped
     * records are given to the new records again (they come after the last record, as the new ids do). The page with at least one live record stays, and so everything before it.
     * It is for a storage which shrinks, the reuse of the deleted records in the middle does not make the memory smaller. A storage which is one array has the page of one
     * record, so it drops the dead records of the end one by one (the array stays as it is).
     *
     * @return what has been done and what stopped it, see {@link TrimResult}; there is always a result, also when nothing is dropped
     */
    public TrimResult tryTrimTail() {
        final int recordsPerPage = data.recordsPerPage();
        final int recordsBefore = storedRecordCount;
        final int pagesBefore = data.pageCount();

        int newTotal = storedRecordCount;
        while (newTotal > 0) {
            // the first record of the page of the last record
            final int pageFirstId = (newTotal - 1) / recordsPerPage * recordsPerPage + 1;
            boolean hasLiveRecord = false;
            for (int recordId = pageFirstId; recordId <= newTotal && !hasLiveRecord; recordId++) {
                hasLiveRecord = !isDeleted(recordId);
            }
            if (hasLiveRecord) {
                break;
            }
            newTotal = pageFirstId - 1;
        }

        final int dropped = storedRecordCount - newTotal;
        if (dropped > 0) {
            final int lastId = newTotal;
            deletedRecords.removeIf(record -> record[0] > lastId);
            data.truncate(newTotal * recordSize, storedRecordCount * recordSize);
            storedRecordCount = newTotal;

            markChanged();
        }

        // the page which has stopped the trimming (or the last one if everything is dropped, then there is none): how many of its records live
        int liveOnLastPage = 0;
        int recordsOnLastPage = 0;
        if (newTotal > 0) {
            final int pageFirstId = (newTotal - 1) / recordsPerPage * recordsPerPage + 1;
            recordsOnLastPage = newTotal - pageFirstId + 1;
            for (int recordId = pageFirstId; recordId <= newTotal; recordId++) {
                if (!isDeleted(recordId)) {
                    liveOnLastPage++;
                }
            }
        }

        return new TrimResult(recordsBefore, pagesBefore, dropped, pagesBefore - data.pageCount(), data.pageCount(), recordsOnLastPage, liveOnLastPage, deletedRecords.size());
    }

    /**
     * The result of {@link #tryTrimTail()}: the records and the pages before, how many have been dropped, and the last page which remains (how many records it holds and how
     * many of them live, it is the reason why the trimming has stopped); also how many deleted records are known to the storage after it (the quarantine and the reuse).
     */
    public record TrimResult(int recordsBefore, int pagesBefore, int droppedRecords, int droppedPages, int pagesAfter,
                             int recordsOnLastPage, int liveOnLastPage, int deletedRecordsKnown) {
    }

    /**
     * How many pages the storage holds (one for a storage which is one array), for the tests and the diagnostics.
     */
    int getPageCount() {
        return data.pageCount();
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
            case Unsigned8bit -> Byte.toUnsignedInt(data.get(fieldOffset));
            case Unsigned16bit -> (Byte.toUnsignedInt(data.get(fieldOffset)) << 8)
                    + (Byte.toUnsignedInt(data.get(fieldOffset + 1)));
            case Unsigned24bit -> (Byte.toUnsignedInt(data.get(fieldOffset)) << 16)
                    + (Byte.toUnsignedInt(data.get(fieldOffset + 1)) << 8)
                    + (Byte.toUnsignedInt(data.get(fieldOffset + 2)));
            case Signed16bit -> (short) ((Byte.toUnsignedInt(data.get(fieldOffset)) << 8) | Byte.toUnsignedInt(data.get(fieldOffset + 1)));
            case Signed32bit -> getIntAtOffset(fieldOffset);
            default -> throw new IllegalStateException("Unexpected dataField dataType: " + dataField.dataType());
        };
    }

    /**
     * The value of a Signed40bit field.
     */
    public long getAsLong(final int recordId, final DataField dataField) {
        checkRecordIdInBounds(recordId);
        checkRecordIdIsNotDeleted(recordId);
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);
        checkArgument(dataField.dataType() == DataType.Signed40bit, "the field is not Signed40bit, it is %s", dataField.dataType());

        return get40BitLongAtOffset(getFieldOffset(recordId, dataField));
    }

    /**
     * Sets a Signed40bit field. A value out of the range of five bytes is an error, it is not cut.
     */
    public void setLong(final int recordId, final DataField dataField, final long value) {
        checkRecordIdInBounds(recordId);
        checkRecordIdIsNotDeleted(recordId);
        checkNotNull(dataField, "dataField should not be null");
        checkDataFieldIsInStorage(dataField);
        checkArgument(dataField.dataType() == DataType.Signed40bit, "the field is not Signed40bit, it is %s", dataField.dataType());
        checkArgument(DataType.Signed40bit.contains(value), "expected range is [%s..%s]: %s", DataType.Signed40bit.minValue(), DataType.Signed40bit.maxValue(), value);

        set40BitLongAtOffset(getFieldOffset(recordId, dataField), value);

        markChanged(); // after the data is written: whoever sees the new stamp sees the new data
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
                final int intValue = (data.get(fieldOffset) << 16)
                        + (Byte.toUnsignedInt(data.get(fieldOffset + 1)) << 8)
                        + (Byte.toUnsignedInt(data.get(fieldOffset + 2)));
                return (180.0f * intValue) / maxValue;
            }
            case LatLong16bit -> {
                final int maxValue = 128 * 256 - 1;
                final int intValue = (data.get(fieldOffset) << 8)
                        + (Byte.toUnsignedInt(data.get(fieldOffset + 1)));
                return (180.0f * intValue) / maxValue;
            }
            case Tenths8bit -> {
                return Byte.toUnsignedInt(data.get(fieldOffset)) / 10.0f;
            }
            case Float16bit -> {
                return HalfPrecisionFloat.fromBits((Byte.toUnsignedInt(data.get(fieldOffset)) << 8) + Byte.toUnsignedInt(data.get(fieldOffset + 1)));
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
                final int len = data.get(fieldOffset) & 0xFF;
                if (len == 255) {
                    return null;
                }
                return data.readString(fieldOffset + 1, len);
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
                checkArgument(DataType.Unsigned8bit.contains(value), "expected range is [0..%s]", DataType.Unsigned8bit.maxValue());
                data.set(fieldOffset, (byte) value);
            }
            case Unsigned16bit -> {
                checkArgument(DataType.Unsigned16bit.contains(value), "expected range is [0..%s]", DataType.Unsigned16bit.maxValue());
                data.set(fieldOffset, (byte) (value >> 8));
                data.set(fieldOffset + 1, (byte) value);
            }
            case Unsigned24bit -> {
                checkArgument(DataType.Unsigned24bit.contains(value), "expected range is [0..%s]", DataType.Unsigned24bit.maxValue());
                data.set(fieldOffset, (byte) (value >> 16));
                data.set(fieldOffset + 1, (byte) (value >> 8));
                data.set(fieldOffset + 2, (byte) value);
            }
            case Signed16bit -> {
                checkArgument(DataType.Signed16bit.contains(value), "expected range is [%s..%s]: %s", DataType.Signed16bit.minValue(), DataType.Signed16bit.maxValue(), value);
                data.set(fieldOffset, (byte) (value >> 8));
                data.set(fieldOffset + 1, (byte) value);
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
                data.set(fieldOffset, (byte) (intValue >> 16));
                data.set(fieldOffset + 1, (byte) (intValue >> 8));
                data.set(fieldOffset + 2, (byte) intValue);
            }
            case LatLong16bit -> {
                checkArgument(-180 <= value && value <= 180, "Lat/Long Value out of range: " + value);
                final int maxValue = 128 * 256 - 1;
                final int intValue = (int) (value / 180.0 * maxValue);
                data.set(fieldOffset, (byte) (intValue >> 8));
                data.set(fieldOffset + 1, (byte) intValue);
            }
            case Tenths8bit -> {
                // 3.42 is rounded to 3.4 and is stored as 34, the value out of [0.0 .. 25.5] is an error, it is not cut
                checkArgument(!Float.isNaN(value) && 0.0f <= value && value <= 25.5f, "Tenths value out of range [0.0 .. 25.5]: " + value);
                data.set(fieldOffset, (byte) Math.round(value * 10.0));
            }
            case Float16bit -> {
                // the value out of the half float range is an error, the accuracy of the stored value depends on its size
                final int bits = HalfPrecisionFloat.toBits(value);
                data.set(fieldOffset, (byte) (bits >> 8));
                data.set(fieldOffset + 1, (byte) bits);
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
            data.fill(fieldOffset, fieldOffset + dataField.size(), (byte) 0);
            if (value == null) {
                data.set(fieldOffset, (byte) 255);
            } else {
                data.set(fieldOffset, (byte) value.length());
                data.write(fieldOffset + 1, value.getBytes(), value.length());
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

    // five bytes, the first one has the sign (it is a signed byte), the other four are the low 32 bits
    private long get40BitLongAtOffset(final int fieldOffset) {
        return ((long) data.get(fieldOffset) << 32)
                + (Integer.toUnsignedLong(getIntAtOffset(fieldOffset + 1)));
    }

    private void set40BitLongAtOffset(final int fieldOffset, final long value) {
        data.set(fieldOffset, (byte) (value >> 32));
        setIntAtOffset(fieldOffset + 1, (int) value);
    }

    private int getIntAtOffset(final int fieldOffset) {
        return data.getInt(fieldOffset);
    }

    private void setIntAtOffset(final int fieldOffset, final int value) {
        data.setInt(fieldOffset, value);
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
        final int recordHeader = data.get(getRecordHeaderOffset(recordId));
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
        private int growthStep = 1;
        private boolean growthStepIsSet = false;
        // zero means one array
        private int pageSizeBytes = 0;

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

        /**
         * How many records the storage grows by when a new record does not fit (the default is 1: exactly as much as required). A big storage
         * which gets new records all the time should have a bigger step: every growth copies the whole array.
         */
        public Builder<T> growthStep(final int records) {
            checkArgument(records >= 1, "growth step should be positive");
            this.growthStep = records;
            this.growthStepIsSet = true;
            return this;
        }

        /**
         * The bytes of the storage are the pages of the default size ({@link Storage#DEFAULT_PAGE_SIZE_BYTES}), not one array, see {@link #paged(int)}.
         */
        public Builder<T> paged() {
            return paged(DEFAULT_PAGE_SIZE_BYTES);
        }

        /**
         * The bytes of the storage are the pages of this size in bytes, not one array: a new record which does not fit adds a page, nothing is copied, and the loading and the
         * saving go page by page. A page holds a whole number of records, so it has to be not smaller than a record. For a big storage which gets new records all the time.
         * The file is the same as of a storage which is one array (the default). It does not go with {@link #growthStep(int)}, which is for the one array.
         */
        public Builder<T> paged(final int pageSizeBytes) {
            checkArgument(pageSizeBytes >= 1, "page size should be positive");
            this.pageSizeBytes = pageSizeBytes;
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
            checkState(!(growthStepIsSet && pageSizeBytes > 0), "the growth step is for the storage which is one array, it does not go with paged");
            return new Storage<>(name, instantiator, idDataType, dataFields.toArray(new DataField[0]),
                    deletedRecordQuarantineMillis, deletedRecordQuarantineIsExplicit, clock, heartbeatIndexFieldIndex, growthStep, pageSizeBytes);
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
