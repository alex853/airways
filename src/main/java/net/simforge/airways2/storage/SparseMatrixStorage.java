package net.simforge.airways2.storage;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Optional;

import static com.google.common.base.Preconditions.*;

public class SparseMatrixStorage<T> {
    private static final int storageHeaderSize = 16;
    private static final int recordHeaderSize = 1;
    private static final int minimalRecordCountIncrease = 16;

    private static final int RECORD_EXISTS = 0;
    private static final int RECORD_EMPTY_OR_DELETED = 1;
    private final String name;
    private final Instantiator<T> instantiator;
    private final DataField[] dataFields;
    private final int recordSize;

    private final DataField[] sortedDataFields;
    private final int[] sortedDataFieldHashs;

    private final DataField id1DataField;
    private final DataField id2DataField;

    private byte[] data;
    private int totalStoredRecordCount; // total count of existing and deleted and reserved records in the 'data' array
    private int existingRecordCount;

    private SparseMatrixStorage(final String name,
                                final Instantiator<T> instantiator,
                                final DataField[] dataFields) {
        checkNotNull(name);
        checkNotNull(instantiator);
        checkNotNull(dataFields);
        checkState(dataFields.length >= 2);

        this.name = name;
        this.instantiator = instantiator;
        this.dataFields = dataFields; // todo ak3 check all required fields are initialised correctly

        this.id1DataField = dataFields[0];
        this.id2DataField = dataFields[1];

        this.recordSize = recordHeaderSize + dataFields[dataFields.length - 1].offsetPlusSize();

        // todo ak0 refactor it in both implementations
        sortedDataFields = Arrays.copyOf(dataFields, dataFields.length);
        Arrays.sort(sortedDataFields, Comparator.comparingInt(Object::hashCode));
        sortedDataFieldHashs = new int[sortedDataFields.length];
        for (int i = 0; i < sortedDataFields.length; i++) {
            sortedDataFieldHashs[i] = sortedDataFields[i].hashCode();
        }

        totalStoredRecordCount = minimalRecordCountIncrease;
        existingRecordCount = 0;
        data = new byte[storageHeaderSize + totalStoredRecordCount * recordSize];
        for (int i = 0; i < totalStoredRecordCount; i++) {
            markAsEmptyDeleted(i);
        }
    }

    public static <T> Builder<T> builder(final DataType id1DataType,
                                         final DataType id2DataType) {
        return new Builder<T>(id1DataType, id2DataType);
    }

    public DataField getDataField(final int fieldIndex) {
        return dataFields[fieldIndex];
    }

    public int getCount() {
        return existingRecordCount;
    }

    public Optional<T> byIds(final int id1, final int id2) {
        throw new UnsupportedOperationException("SparseMatrixStorage.byIds not implemented");
    }

    public T addRecord(final int id1, final int id2) {
        throw new UnsupportedOperationException("SparseMatrixStorage.addRecord not implemented");
    }

    public void deleteRecord(final int id1, final int id2) {
        throw new UnsupportedOperationException("SparseMatrixStorage.deleteRecord not implemented");
    }

    // -----------------------------------------------------------------------------------------------------------------
    public int getAsInt(final int id1, final int id2, final DataField dataField) {
        throw new UnsupportedOperationException("SparseMatrixStorage.getAsInt not implemented");
    }

    public void set(final int id1, final int id2, final DataField dataField, final int value) {
        throw new UnsupportedOperationException("SparseMatrixStorage.set not implemented");
    }

    // -----------------------------------------------------------------------------------------------------------------
    private void markAsEmptyDeleted(final int recordIndex) {
        data[getRecordHeaderOffsetByIndex(recordIndex)] = RECORD_EMPTY_OR_DELETED;
    }

    private int getRecordHeaderOffsetByIndex(final int recordIndex) {
        return storageHeaderSize + recordIndex * recordSize;
    }

    // -----------------------------------------------------------------------------------------------------------------
    public static class Builder<T> {
        private String name;
        private Instantiator<T> instantiator;
        private final LinkedList<DataField> dataFields = new LinkedList<>();

        private Builder(final DataType id1DataType, final DataType id2DataType) {
            checkNotNull(id1DataType);
            checkNotNull(id2DataType);

            withDataField(DataField.of(id1DataType));
            withDataField(DataField.of(id2DataType));
        }

        public Builder<T> name(final String name) {
            this.name = name;
            return this;
        }

        public Builder<T> withInstantiator(final Instantiator<T> instantiator) {
            this.instantiator = instantiator;
            return this;
        }

        public Builder<T> withDataField(final DataField dataField) {
            dataFields.add(dataFields.isEmpty()
                    ? dataField
                    : dataField.after(dataFields.getLast()));
            return this;
        }

        public SparseMatrixStorage<T> build() {
            checkState(dataFields.size() >= 2);
            return new SparseMatrixStorage<T>(name, instantiator, dataFields.toArray(new DataField[0]));
        }
    }

    public interface Instantiator<T> {
        T create(int id1, int id2);
    }
}
