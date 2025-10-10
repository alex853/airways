package net.simforge.airways2.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class BitAccessField {
    private final Storage<?> storage;
    private final DataField dataField;
    private final int maxBits;

    private BitAccessField(final Storage<?> storage, final DataField dataField, final int maxBits) {
        this.storage = storage;
        this.dataField = dataField;
        this.maxBits = maxBits;
    }

    public static BitAccessField instance(final Storage<?> storage, final DataField dataField) {
        checkNotNull(storage);
        checkNotNull(dataField);
        checkArgument(dataField.dataType() == DataType.Signed32bit
                || dataField.dataType() == DataType.Unsigned24bit
                || dataField.dataType() == DataType.Unsigned16bit
                || dataField.dataType() == DataType.Unsigned8bit);
        return new BitAccessField(
                storage,
                dataField,
                switch (dataField.dataType()) {
                    case Signed32bit -> 32;
                    case Unsigned24bit -> 24;
                    case Unsigned16bit -> 16;
                    case Unsigned8bit -> 8;
                    default -> throw new IllegalArgumentException();
                });
    }

    public BitAccessField.Section section(final int offset, final int length) {
        checkArgument(0 <= offset && offset < maxBits);
        checkArgument(length <= maxBits);
        checkArgument(offset + length <= maxBits);
        return new Section(offset, length);
    }

    public class Section {
        private final int offset;
        private final int mask;

        private Section(final int offset, final int length) {
            this.offset = offset;
            this.mask = ((1 << length) - 1) << offset;
        }

        public int getInt(final int recordId) {
            final int raw = storage.getAsInt(recordId, dataField);
            return (raw & mask) >> offset;
        }

        public void setInt(final int recordId, final int value) {
            final int shiftedValue = value << offset;
            final int raw = storage.getAsInt(recordId, dataField);
            final int otherSections = (raw & ~mask);
            final int newRaw = shiftedValue | otherSections;
            storage.set(recordId, dataField, newRaw);
        }

        public boolean getBoolean(final int recordId) {
            final int raw = storage.getAsInt(recordId, dataField);
            return (raw & mask) != 0;
        }

        public void setBoolean(final int recordId, final boolean value) {
            final int shiftedValue = value ? mask : 0;
            final int raw = storage.getAsInt(recordId, dataField);
            final int otherSections = (raw & ~mask);
            final int newRaw = shiftedValue | otherSections;
            storage.set(recordId, dataField, newRaw);
        }
    }
}
