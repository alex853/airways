package net.simforge.airways2.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The bytes are the pages of the same size, each page holds a whole number of records (the few bytes at the end of a page which are not enough for one more record stay
 * unused and are never saved), so a record is never split between two pages. A growth adds a page and copies nothing, the loading reads the file page by page and the saving
 * writes it page by page: there is no array of the size of the whole storage at any moment. The file is the same as of {@link FlatByteStore}.
 */
final class PagedByteStore implements ByteStore {
    private final int recordSize;
    // the bytes of the records of a page, a multiple of the record size
    private final int pageBytes;
    private byte[][] pages = new byte[0][];

    PagedByteStore(final int recordSize, final int pageSizeBytes) {
        checkArgument(recordSize <= pageSizeBytes, "the record of %s bytes does not fit into the page of %s bytes", recordSize, pageSizeBytes);
        this.recordSize = recordSize;
        this.pageBytes = pageSizeBytes / recordSize * recordSize;
    }

    @Override
    public byte get(final int offset) {
        final int page = offset / pageBytes;
        return pages[page][offset - page * pageBytes];
    }

    @Override
    public void set(final int offset, final byte value) {
        final int page = offset / pageBytes;
        pages[page][offset - page * pageBytes] = value;
    }

    @Override
    public int getInt(final int offset) {
        final int page = offset / pageBytes;
        final byte[] bytes = pages[page];
        final int i = offset - page * pageBytes;
        return (bytes[i] << 24)
                + (Byte.toUnsignedInt(bytes[i + 1]) << 16)
                + (Byte.toUnsignedInt(bytes[i + 2]) << 8)
                + (Byte.toUnsignedInt(bytes[i + 3]));
    }

    @Override
    public void setInt(final int offset, final int value) {
        final int page = offset / pageBytes;
        final byte[] bytes = pages[page];
        final int i = offset - page * pageBytes;
        bytes[i] = (byte) (value >> 24);
        bytes[i + 1] = (byte) (value >> 16);
        bytes[i + 2] = (byte) (value >> 8);
        bytes[i + 3] = (byte) value;
    }

    @Override
    public void fill(final int from, final int to, final byte value) {
        final int page = from / pageBytes;
        final int pageStart = page * pageBytes;
        Arrays.fill(pages[page], from - pageStart, to - pageStart, value);
    }

    @Override
    public void write(final int offset, final byte[] source, final int length) {
        final int page = offset / pageBytes;
        System.arraycopy(source, 0, pages[page], offset - page * pageBytes, length);
    }

    @Override
    public String readString(final int offset, final int length) {
        final int page = offset / pageBytes;
        return new String(pages[page], offset - page * pageBytes, length);
    }

    @Override
    public void ensureLength(final int length) {
        final int required = (int) (((long) length + pageBytes - 1) / pageBytes);
        if (required <= pages.length) {
            return;
        }
        // only the array of the references is copied, the pages stay where they are
        final byte[][] grown = Arrays.copyOf(pages, required);
        for (int i = pages.length; i < required; i++) {
            grown[i] = new byte[pageBytes];
        }
        pages = grown;
    }

    @Override
    public int load(final Path path, final int recordSize) throws IOException {
        // a partial record at the end of the file (it should not be there) is not read
        final int records = (int) (Files.size(path) / recordSize);
        final int usedLength = records * recordSize;

        clear();
        ensureLength(usedLength);

        try (final InputStream in = Files.newInputStream(path)) {
            int remaining = usedLength;
            for (int page = 0; remaining > 0; page++) {
                final int expected = Math.min(pageBytes, remaining);
                final int read = in.readNBytes(pages[page], 0, expected);
                if (read != expected) {
                    throw new IOException("The file " + path + " is shorter than expected: page " + page + " has " + read + " bytes of " + expected);
                }
                remaining -= expected;
            }
        }
        return records;
    }

    @Override
    public void save(final Path path, final int usedLength) throws IOException {
        try (final OutputStream out = Files.newOutputStream(path,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            int remaining = usedLength;
            for (int page = 0; remaining > 0; page++) {
                final int count = Math.min(pageBytes, remaining);
                out.write(pages[page], 0, count);
                remaining -= count;
            }
        }
    }

    @Override
    public void clear() {
        pages = new byte[0][];
    }

    @Override
    public int recordsPerPage() {
        return pageBytes / recordSize;
    }

    @Override
    public void truncate(final int newLength, final int oldLength) {
        // the dropped pages go away, a page which is added later is new
        pages = Arrays.copyOf(pages, Math.min(pages.length, newLength / pageBytes));
    }

    @Override
    public boolean isPaged() {
        return true;
    }

    @Override
    public int pageCount() {
        return pages.length;
    }
}
