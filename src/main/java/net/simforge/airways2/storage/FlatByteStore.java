package net.simforge.airways2.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * All the bytes are one array. A growth copies the whole array, so a storage which gets new records all the time grows by a bigger step (Storage.Builder#growthStep).
 */
final class FlatByteStore implements ByteStore {
    // the zero reserve which is added to the required length when the array has to grow
    private final int growthReserveBytes;
    // bytes.length is the capacity, the records take the first part of it
    private byte[] bytes = new byte[0];

    FlatByteStore(final int growthReserveBytes) {
        this.growthReserveBytes = growthReserveBytes;
    }

    @Override
    public byte get(final int offset) {
        return bytes[offset];
    }

    @Override
    public void set(final int offset, final byte value) {
        bytes[offset] = value;
    }

    @Override
    public int getInt(final int offset) {
        return (bytes[offset] << 24)
                + (Byte.toUnsignedInt(bytes[offset + 1]) << 16)
                + (Byte.toUnsignedInt(bytes[offset + 2]) << 8)
                + (Byte.toUnsignedInt(bytes[offset + 3]));
    }

    @Override
    public void setInt(final int offset, final int value) {
        bytes[offset] = (byte) (value >> 24);
        bytes[offset + 1] = (byte) (value >> 16);
        bytes[offset + 2] = (byte) (value >> 8);
        bytes[offset + 3] = (byte) value;
    }

    @Override
    public void fill(final int from, final int to, final byte value) {
        Arrays.fill(bytes, from, to, value);
    }

    @Override
    public void write(final int offset, final byte[] source, final int length) {
        System.arraycopy(source, 0, bytes, offset, length);
    }

    @Override
    public String readString(final int offset, final int length) {
        return new String(bytes, offset, length);
    }

    @Override
    public void ensureLength(final int length) {
        if (length > bytes.length) {
            bytes = Arrays.copyOf(bytes, length + growthReserveBytes);
        }
    }

    @Override
    public int load(final Path path, final int recordSize) throws IOException {
        bytes = Files.readAllBytes(path);
        return bytes.length / recordSize;
    }

    @Override
    public void save(final Path path, final int usedLength) throws IOException {
        // the reserve of the capacity is not saved, the file holds the records only
        final byte[] toWrite = usedLength == bytes.length ? bytes : Arrays.copyOf(bytes, usedLength);
        Files.write(path, toWrite, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    @Override
    public void clear() {
        bytes = new byte[0];
    }

    @Override
    public int recordsPerPage() {
        return 1;
    }

    @Override
    public void truncate(final int newLength, final int oldLength) {
        // the array stays, the cut records are zero for the records which take their places
        Arrays.fill(bytes, newLength, Math.min(oldLength, bytes.length), (byte) 0);
    }

    @Override
    public boolean isPaged() {
        return false;
    }

    @Override
    public int pageCount() {
        return bytes.length == 0 ? 0 : 1;
    }
}
