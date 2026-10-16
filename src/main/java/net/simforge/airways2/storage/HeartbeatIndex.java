package net.simforge.airways2.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.TreeSet;

/**
 * An ordered index of the records of a storage by their heartbeat time: it answers 'which record is the first one to be processed
 * at this world time' without a scan of the whole storage.
 * <p>
 * An entry is {heartbeat time, record id}, the entries are ordered by the time and then by the id, so the earliest
 * heartbeat is the first and records with the same heartbeat time go in the order of their ids. A zero heartbeat time means 'no heartbeat',
 * such a record is not in the index.
 * <p>
 * The index is not thread safe, it is modified and read under the lock of the world as the storages are.
 * It is maintained by the {@link Storage} itself (see {@code Storage.Builder#withHeartbeatIndex}), so no write to the storage can bypass it.
 */
public class HeartbeatIndex {
    // heartbeat time in the high 32 bits, record id in the low 32 bits: the natural order of the longs is the order of the entries,
    // also for negative times
    private final TreeSet<Long> entries = new TreeSet<>();

    private static long key(final int heartbeatTime, final int recordId) {
        return ((long) heartbeatTime << 32) | (recordId & 0xFFFFFFFFL);
    }

    private static int heartbeatTimeOf(final long key) {
        return (int) (key >> 32);
    }

    private static int recordIdOf(final long key) {
        return (int) key;
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    public void add(final int heartbeatTime, final int recordId) {
        if (heartbeatTime == 0) {
            return;
        }
        entries.add(key(heartbeatTime, recordId));
    }

    /**
     * Does nothing if there is no such entry.
     */
    public void remove(final int heartbeatTime, final int recordId) {
        if (heartbeatTime == 0) {
            return;
        }
        entries.remove(key(heartbeatTime, recordId));
    }

    public void update(final int recordId, final int oldHeartbeatTime, final int newHeartbeatTime) {
        remove(oldHeartbeatTime, recordId);
        add(newHeartbeatTime, recordId);
    }

    public boolean contains(final int heartbeatTime, final int recordId) {
        return entries.contains(key(heartbeatTime, recordId));
    }

    /**
     * The record with the earliest heartbeat time if that time is not later than the world time.
     */
    public OptionalInt firstDue(final int worldTime) {
        if (entries.isEmpty()) {
            return OptionalInt.empty();
        }

        final long first = entries.first();
        if (heartbeatTimeOf(first) > worldTime) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(recordIdOf(first));
    }

    /**
     * The earliest heartbeat time of the index, if it is not empty.
     */
    public OptionalInt earliestHeartbeatTime() {
        return entries.isEmpty() ? OptionalInt.empty() : OptionalInt.of(heartbeatTimeOf(entries.first()));
    }

    /**
     * All the entries as {heartbeat time, record id}, in the order of the index. It is intended for tests and diagnostics.
     */
    public List<int[]> entries() {
        final List<int[]> result = new ArrayList<>(entries.size());
        for (final long key : entries) {
            result.add(new int[]{heartbeatTimeOf(key), recordIdOf(key)});
        }
        return result;
    }
}
