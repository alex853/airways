package net.simforge.airways2.storage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The low level of a {@link Storage}: the bytes of the records, addressed by the offset from the start of the first record, and the loading and the saving of them. A record
 * (and so a field of a record) is never split between two parts of the memory, so a read or a write of a field is always one call. The file of a storage holds the records
 * only, it is the same for every implementation: {@link FlatByteStore} is one array, {@link PagedByteStore} is the pages.
 */
interface ByteStore {
    byte get(int offset);

    void set(int offset, byte value);

    int getInt(int offset);

    void setInt(int offset, int value);

    /** The range is inside one record. */
    void fill(int from, int to, byte value);

    /** The range is inside one record. */
    void write(int offset, byte[] source, int length);

    /** The range is inside one record. */
    String readString(int offset, int length);

    /** Makes the first {@code length} bytes available, the new bytes are zero. */
    void ensureLength(int length);

    /** Replaces the content with the records of the file, returns the count of the records. */
    int load(Path path, int recordSize) throws IOException;

    /** Writes the first {@code usedLength} bytes to the file. */
    void save(Path path, int usedLength) throws IOException;

    void clear();

    /** How many records are released together: the records of a page, one record for the storage which is one array. */
    int recordsPerPage();

    /** Cuts the bytes to the new length (a multiple of the page size, it is less than the old one), the cut bytes are zero when they come back. */
    void truncate(int newLength, int oldLength);

    int pageCount();

    /** The bytes are the pages, not one array. */
    boolean isPaged();
}
