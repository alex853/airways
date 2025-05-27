package net.simforge.airways2.storage;

import static com.google.common.base.Preconditions.checkArgument;

public class DataField {
    private final DataType dataType;
    private final int length;
    private final DataField previousField;
    private final int offset;
    private final int size;

    private DataField(final DataType dataType,
                      final int length,
                      final DataField previousField) {
        if (dataType != DataType.PlainString) {
            checkArgument(length == 0, "length can be specified for PlainString only");
        } else {
            checkArgument(length > 0, "length should be 1 byte or higher");
            checkArgument(length <= 254, "length can not be higher than 254 bytes");
        }

        this.dataType = dataType;
        this.length = length;
        this.previousField = previousField;

        if (previousField != null) {
            offset = previousField.offsetPlusSize();
        } else {
            offset = 0;
        }

        size = calcSize(dataType, length);
    }

    public static DataField of(final DataType dataType) {
        return new DataField(
                dataType,
                dataType == DataType.PlainString ? 20 : 0,
                null);
    }

    public DataField length(final int length) {
        return new DataField(dataType, length, previousField);
    }

    public DataField after(final DataField previousField) {
        return new DataField(dataType, length, previousField);
    }

    public DataType dataType() {
        return dataType;
    }

    public int length() {
        return length;
    }

    public int size() {
        return size;
    }

    public int offset() {
        if (previousField != null) {
            return previousField.offsetPlusSize();
        } else {
            return 0;
        }
    }

    public int offsetPlusSize() {
        return offset + size;
    }

    private static int calcSize(final DataType dataType, final int length) {
        return switch (dataType) {
            case Signed32bit -> 4;
            case Unsigned24bit -> 3;
            case Unsigned16bit -> 2;
            case Unsigned8bit -> 1;
            case Float -> 4;
            case LatLong24bit -> 3;
            case LatLong16bit -> 2;
            case PlainString -> 1 + length;
            //case PackedTo6BitsString -> (length * 6) / 8 + (((length * 6) % 8) > 0 ? 1 : 0);
        };
    }
}
