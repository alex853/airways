package net.simforge.airways2.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

class StorageQuarantineTest {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    // a moment aligned to a minute, so that checks around the quarantine borders are exact
    private static final long START = 1_800_000_000_000L - (1_800_000_000_000L % MINUTE);

    private static final Duration QUARANTINE = Duration.ofHours(2);

    @TempDir
    Path tempDir;

    private AtomicLong now;

    @BeforeEach
    public void setUp() {
        now = new AtomicLong(START);
    }

    // record: 1 byte header + 4 bytes int + 11 bytes string = 16 bytes
    private Storage<Integer> storage(final Duration quarantine) {
        return storage("quarantine-test", quarantine, now::get);
    }

    private static Storage<Integer> storage(final String name, final Duration quarantine, final LongSupplier clock) {
        return Storage.<Integer>builder()
                .name(name)
                .withInstantiator(recordId -> recordId)
                .withIdOf(DataType.Unsigned24bit)
                .withDataField(DataField.of(DataType.Signed32bit))
                .withDataField(DataField.of(DataType.PlainString).length(10))
                .deletedRecordQuarantine(quarantine)
                .clock(clock)
                .build();
    }

    private static DataField intField(final Storage<Integer> storage) {
        return storage.getDataField(0);
    }

    private static DataField stringField(final Storage<Integer> storage) {
        return storage.getDataField(1);
    }

    private Storage<Integer> reload(final Storage<Integer> storage, final Duration quarantine) throws Exception {
        storage.save(tempDir);
        final Storage<Integer> loaded = storage("quarantine-test", quarantine, now::get);
        loaded.loadIfExists(tempDir);
        return loaded;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // basic behaviour

    @Test
    public void deleted_record_is_not_reused_at_once() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        final int b = storage.addRecord();
        storage.deleteRecord(a);

        final int c = storage.addRecord();

        assertNotEquals(a, c);
        assertEquals(3, c);
        assertEquals(List.of(b, c), storage.all().toList());
    }

    @Test
    public void deleted_record_is_reused_after_quarantine() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.addRecord();
        storage.deleteRecord(a);

        now.addAndGet(QUARANTINE.toMillis());

        assertEquals(a, storage.addRecord());
    }

    @Test
    public void deleted_record_is_not_reused_one_minute_before_quarantine_ends() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.addRecord();
        storage.deleteRecord(a);

        now.addAndGet(QUARANTINE.toMillis() - MINUTE);

        assertEquals(3, storage.addRecord());
    }

    @Test
    public void deleted_record_is_reused_exactly_when_quarantine_ends() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.addRecord();
        storage.deleteRecord(a);

        now.addAndGet(QUARANTINE.toMillis() - 1);
        assertEquals(1, storage.getQuarantinedDeletedRecordCount());

        now.addAndGet(1);
        assertEquals(0, storage.getQuarantinedDeletedRecordCount());
        assertEquals(1, storage.getReusableDeletedRecordCount());
        assertEquals(a, storage.addRecord());
    }

    @Test
    public void zero_quarantine_reuses_at_once_like_before() {
        final Storage<Integer> storage = storage(Duration.ZERO);
        final int a = storage.addRecord();
        storage.addRecord();
        storage.deleteRecord(a);

        assertEquals(a, storage.addRecord());
    }

    @Test
    public void default_quarantine_is_one_day() {
        final Storage<Integer> storage = Storage.<Integer>builder()
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Signed32bit))
                .build();

        assertEquals(DAY, storage.getDeletedRecordQuarantineMillis());
        assertEquals(DAY, Storage.DEFAULT_DELETED_RECORD_QUARANTINE.toMillis());

        final int a = storage.addRecord();
        storage.addRecord();
        storage.deleteRecord(a);
        assertEquals(3, storage.addRecord());
    }

    @Test
    public void default_quarantine_ends_after_a_day() {
        final Storage<Integer> storage = Storage.<Integer>builder()
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Signed32bit))
                .clock(now::get)
                .build();

        final int a = storage.addRecord();
        storage.addRecord();
        storage.deleteRecord(a);

        now.addAndGet(DAY - MINUTE);
        assertEquals(3, storage.addRecord());

        now.addAndGet(MINUTE);
        assertEquals(a, storage.addRecord());
    }

    @Test
    public void explicit_quarantine_overrides_the_default() {
        final Storage<Integer> storage = Storage.<Integer>builder()
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Signed32bit))
                .deletedRecordQuarantine(Duration.ofMinutes(5))
                .clock(now::get)
                .build();

        assertEquals(5 * MINUTE, storage.getDeletedRecordQuarantineMillis());
    }

    @Test
    public void quarantine_is_visible_in_storage() {
        assertEquals(QUARANTINE.toMillis(), storage(QUARANTINE).getDeletedRecordQuarantineMillis());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // ordering

    @Test
    public void records_are_reused_in_order_of_deletion() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int r1 = storage.addRecord();
        final int r2 = storage.addRecord();
        final int r3 = storage.addRecord();
        storage.addRecord();

        storage.deleteRecord(r3);
        now.addAndGet(MINUTE);
        storage.deleteRecord(r1);
        now.addAndGet(MINUTE);
        storage.deleteRecord(r2);

        now.addAndGet(QUARANTINE.toMillis() + HOUR);

        assertEquals(r3, storage.addRecord());
        assertEquals(r1, storage.addRecord());
        assertEquals(r2, storage.addRecord());
        assertEquals(5, storage.addRecord());
    }

    @Test
    public void only_the_ones_with_finished_quarantine_are_reused() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ids.add(storage.addRecord());
        }

        // the first three are deleted now, the last three an hour later
        for (int i = 0; i < 3; i++) {
            storage.deleteRecord(ids.get(i));
        }
        now.addAndGet(HOUR);
        for (int i = 3; i < 6; i++) {
            storage.deleteRecord(ids.get(i));
        }

        // two hours since the first deletion, one hour since the second
        now.addAndGet(HOUR);

        assertEquals(ids.get(0), storage.addRecord());
        assertEquals(ids.get(1), storage.addRecord());
        assertEquals(ids.get(2), storage.addRecord());
        // the next ones are still in the quarantine, so the storage grows
        assertEquals(7, storage.addRecord());
        assertEquals(3, storage.getQuarantinedDeletedRecordCount());
    }

    @Test
    public void quarantine_of_a_batch_ends_after_the_batch_is_deleted_again() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.addRecord();

        storage.deleteRecord(a);
        now.addAndGet(QUARANTINE.toMillis());
        assertEquals(a, storage.addRecord());

        // deleted for the second time - a new quarantine starts
        storage.deleteRecord(a);
        now.addAndGet(QUARANTINE.toMillis() - MINUTE);
        assertEquals(3, storage.addRecord());
        now.addAndGet(MINUTE);
        assertEquals(a, storage.addRecord());
    }

    @Test
    public void hundred_and_hundred_records_in_two_waves() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final List<Integer> wave1 = new ArrayList<>();
        final List<Integer> wave2 = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            (i < 100 ? wave1 : wave2).add(storage.addRecord());
        }
        storage.addRecord(); // keeps the tail alive

        wave1.forEach(storage::deleteRecord);
        now.addAndGet(HOUR);
        wave2.forEach(storage::deleteRecord);
        now.addAndGet(HOUR);

        final Set<Integer> reused = new HashSet<>();
        final Set<Integer> appended = new HashSet<>();
        for (int i = 0; i < 150; i++) {
            final int id = storage.addRecord();
            (id <= 201 ? reused : appended).add(id);
        }

        assertEquals(new HashSet<>(wave1), reused);
        assertEquals(50, appended.size());
        assertTrue(appended.stream().allMatch(id -> id > 201));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // storage is not shrunk

    @Test
    public void deleting_the_last_records_does_not_shrink_the_storage() throws Exception {
        final Storage<Integer> storage = storage(QUARANTINE);
        storage.addRecord();
        storage.addRecord();
        final int last = storage.addRecord();

        storage.save(tempDir);
        final long sizeBefore = Files.size(tempDir.resolve("quarantine-test"));

        storage.deleteRecord(last);
        storage.save(tempDir);

        assertEquals(sizeBefore, Files.size(tempDir.resolve("quarantine-test")));
        assertEquals(2, storage.getCount());
    }

    @Test
    public void new_record_after_deleting_the_tail_gets_a_new_id() {
        final Storage<Integer> storage = storage(QUARANTINE);
        storage.addRecord();
        final int last = storage.addRecord();
        storage.deleteRecord(last);

        final int added = storage.addRecord();

        assertEquals(last + 1, added);
    }

    @Test
    public void whole_storage_deleted_and_filled_again() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(storage.addRecord());
        }
        ids.forEach(storage::deleteRecord);
        assertEquals(0, storage.getCount());

        for (int i = 0; i < 10; i++) {
            assertEquals(11 + i, storage.addRecord());
        }
        assertEquals(10, storage.getCount());
        assertEquals(10, storage.getQuarantinedDeletedRecordCount());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // data of reused records

    @Test
    public void reused_record_is_clean() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.set(a, intField(storage), 123456789);
        storage.set(a, stringField(storage), "abcdefghij");
        storage.deleteRecord(a);

        now.addAndGet(QUARANTINE.toMillis());
        final int again = storage.addRecord();

        assertEquals(a, again);
        assertEquals(0, storage.getAsInt(again, intField(storage)));
        assertEquals("", storage.getAsString(again, stringField(storage)));
    }

    @Test
    public void reused_record_can_be_written_and_read() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.deleteRecord(a);
        now.addAndGet(QUARANTINE.toMillis());
        final int again = storage.addRecord();

        storage.set(again, intField(storage), -77);
        storage.set(again, stringField(storage), "hello");

        assertEquals(-77, storage.getAsInt(again, intField(storage)));
        assertEquals("hello", storage.getAsString(again, stringField(storage)));
    }

    @Test
    public void deleted_record_can_not_be_read_during_quarantine() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.set(a, intField(storage), 5);
        storage.deleteRecord(a);

        assertThrows(IllegalArgumentException.class, () -> storage.getAsInt(a, intField(storage)));
        assertTrue(storage.byId(a).isEmpty());
        assertFalse(storage.all().anyMatch(id -> id == a));
        assertFalse(storage.filter(id -> true).anyMatch(id -> id == a));
        assertTrue(storage.findFirst(id -> true).isEmpty());
    }

    @Test
    public void deleting_a_deleted_record_fails() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.deleteRecord(a);

        assertThrows(IllegalArgumentException.class, () -> storage.deleteRecord(a));
    }

    @Test
    public void deleting_an_unknown_record_fails() {
        final Storage<Integer> storage = storage(QUARANTINE);
        storage.addRecord();

        assertThrows(IllegalArgumentException.class, () -> storage.deleteRecord(2));
        assertThrows(IllegalArgumentException.class, () -> storage.deleteRecord(0));
    }

    @Test
    public void count_does_not_include_quarantined_records() {
        final Storage<Integer> storage = storage(QUARANTINE);
        for (int i = 0; i < 5; i++) {
            storage.addRecord();
        }
        storage.deleteRecord(2);
        storage.deleteRecord(4);

        assertEquals(3, storage.getCount());
        assertEquals(List.of(1, 3, 5), storage.all().toList());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // persistence

    @Test
    public void quarantine_survives_save_and_load() throws Exception {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.addRecord();
        storage.deleteRecord(a);

        final Storage<Integer> loaded = reload(storage, QUARANTINE);

        assertEquals(1, loaded.getQuarantinedDeletedRecordCount());
        assertEquals(3, loaded.addRecord());
    }

    @Test
    public void record_is_reused_after_load_when_time_has_passed() throws Exception {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.addRecord();
        storage.deleteRecord(a);
        storage.save(tempDir);

        now.addAndGet(QUARANTINE.toMillis());
        final Storage<Integer> loaded = storage("quarantine-test", QUARANTINE, now::get);
        loaded.loadIfExists(tempDir);

        assertEquals(1, loaded.getReusableDeletedRecordCount());
        assertEquals(a, loaded.addRecord());
    }

    @Test
    public void order_of_deletion_survives_save_and_load() throws Exception {
        final Storage<Integer> storage = storage(QUARANTINE);
        for (int i = 0; i < 5; i++) {
            storage.addRecord();
        }
        storage.deleteRecord(4);
        now.addAndGet(MINUTE);
        storage.deleteRecord(2);
        now.addAndGet(MINUTE);
        storage.deleteRecord(3);

        final Storage<Integer> loaded = reload(storage, QUARANTINE);
        now.addAndGet(QUARANTINE.toMillis() + HOUR);

        assertEquals(4, loaded.addRecord());
        assertEquals(2, loaded.addRecord());
        assertEquals(3, loaded.addRecord());
    }

    @Test
    public void deletion_time_is_not_refreshed_by_load() throws Exception {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.addRecord();
        storage.deleteRecord(a);

        // an hour later the storage is saved and loaded again, and again after another hour
        now.addAndGet(HOUR);
        Storage<Integer> loaded = reload(storage, QUARANTINE);
        now.addAndGet(HOUR);
        loaded = reload(loaded, QUARANTINE);

        // two hours passed since the deletion
        assertEquals(a, loaded.addRecord());
    }

    @Test
    public void loaded_storage_is_empty_when_there_is_no_file() throws Exception {
        final Storage<Integer> loaded = storage("no-such-file", QUARANTINE, now::get);
        loaded.addRecord();

        loaded.loadIfExists(tempDir);

        assertEquals(0, loaded.getCount());
        assertEquals(0, loaded.getQuarantinedDeletedRecordCount());
        assertEquals(1, loaded.addRecord());
    }

    @Test
    public void reset_forgets_deleted_records() {
        final Storage<Integer> storage = storage(QUARANTINE);
        storage.addRecord();
        storage.deleteRecord(1);

        storage.reset();

        assertEquals(0, storage.getQuarantinedDeletedRecordCount());
        assertEquals(1, storage.addRecord());
    }

    @Test
    public void records_deleted_by_old_version_get_quarantine_on_load() throws Exception {
        // an old file: 4 records of 16 bytes, #2 and #3 are deleted (header 1, body zeros, no deletion time)
        final byte[] data = new byte[4 * 16];
        data[16] = 1;
        data[32] = 1;
        Files.write(tempDir.resolve("quarantine-test"), data);

        final Storage<Integer> loaded = storage("quarantine-test", QUARANTINE, now::get);
        loaded.loadIfExists(tempDir);

        assertEquals(2, loaded.getCount());
        assertEquals(2, loaded.getQuarantinedDeletedRecordCount());
        assertEquals(5, loaded.addRecord());

        now.addAndGet(QUARANTINE.toMillis());
        // the same deletion time - the lowest id first
        assertEquals(2, loaded.addRecord());
        assertEquals(3, loaded.addRecord());
        assertEquals(6, loaded.addRecord());
    }

    @Test
    public void old_file_stamped_on_load_is_stable_after_saving() throws Exception {
        final byte[] data = new byte[3 * 16];
        data[16] = 1;
        Files.write(tempDir.resolve("quarantine-test"), data);

        Storage<Integer> loaded = storage("quarantine-test", QUARANTINE, now::get);
        loaded.loadIfExists(tempDir);
        // one hour later it is saved and loaded again, the quarantine must not be restarted
        now.addAndGet(HOUR);
        loaded = reload(loaded, QUARANTINE);
        now.addAndGet(HOUR);

        assertEquals(2, loaded.addRecord());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // records which are too small

    @Test
    public void small_records_are_not_allowed_with_quarantine() {
        final Storage.Builder<Integer> builder = Storage.<Integer>builder()
                .name("small")
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Unsigned16bit))
                .deletedRecordQuarantine(QUARANTINE);

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    public void small_records_fall_back_to_no_quarantine_by_default() {
        final Storage<Integer> storage = Storage.<Integer>builder()
                .name("small")
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Unsigned8bit))
                .build();

        assertEquals(0, storage.getDeletedRecordQuarantineMillis());

        final int a = storage.addRecord();
        storage.deleteRecord(a);

        assertEquals(a, storage.addRecord());
    }

    @Test
    public void small_records_work_with_explicit_zero_quarantine() {
        final Storage<Integer> storage = Storage.<Integer>builder()
                .name("small")
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Unsigned8bit))
                .deletedRecordQuarantine(Duration.ZERO)
                .build();

        final int a = storage.addRecord();
        storage.deleteRecord(a);

        assertEquals(a, storage.addRecord());
    }

    @Test
    public void record_of_exactly_four_bytes_is_allowed() {
        final Storage<Integer> storage = Storage.<Integer>builder()
                .name("four")
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Unsigned16bit))
                .withDataField(DataField.of(DataType.Unsigned16bit))
                .deletedRecordQuarantine(QUARANTINE)
                .clock(now::get)
                .build();

        final int a = storage.addRecord();
        storage.set(a, storage.getDataField(0), 65535);
        storage.set(a, storage.getDataField(1), 65535);
        storage.deleteRecord(a);
        now.addAndGet(QUARANTINE.toMillis());

        final int again = storage.addRecord();
        assertEquals(a, again);
        assertEquals(0, storage.getAsInt(again, storage.getDataField(0)));
        assertEquals(0, storage.getAsInt(again, storage.getDataField(1)));
    }

    @Test
    public void negative_quarantine_is_not_allowed() {
        assertThrows(IllegalArgumentException.class,
                () -> Storage.<Integer>builder().deletedRecordQuarantine(Duration.ofMinutes(-1)));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // clock

    @Test
    public void clock_going_backwards_does_not_release_records() {
        final Storage<Integer> storage = storage(QUARANTINE);
        final int a = storage.addRecord();
        storage.addRecord();
        storage.deleteRecord(a);

        now.addAndGet(-DAY);

        assertEquals(3, storage.addRecord());
        assertEquals(1, storage.getQuarantinedDeletedRecordCount());
    }

    @Test
    public void year_long_quarantine_works() {
        final Storage<Integer> storage = storage(Duration.ofDays(365));
        final int a = storage.addRecord();
        storage.addRecord();
        storage.deleteRecord(a);

        now.addAndGet(364 * DAY);
        assertEquals(3, storage.addRecord());

        now.addAndGet(DAY);
        assertEquals(a, storage.addRecord());
    }

    @Test
    public void deletion_time_has_minute_resolution_within_the_same_minute() {
        final Storage<Integer> storage = storage(Duration.ofMinutes(10));
        final int a = storage.addRecord();
        storage.addRecord();

        now.addAndGet(30_000); // 30 s after the minute starts
        storage.deleteRecord(a);

        // the record is stamped with the minute start, so it is released a bit earlier, not later
        now.addAndGet(9 * MINUTE + 30_000); // 10 minutes since the minute start
        assertEquals(a, storage.addRecord());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // diagnostics and counters

    @Test
    public void counters_follow_the_time() {
        final Storage<Integer> storage = storage(QUARANTINE);
        for (int i = 0; i < 4; i++) {
            storage.addRecord();
        }
        storage.deleteRecord(1);
        storage.deleteRecord(2);
        assertEquals(2, storage.getQuarantinedDeletedRecordCount());
        assertEquals(0, storage.getReusableDeletedRecordCount());

        now.addAndGet(HOUR);
        storage.deleteRecord(3);
        assertEquals(3, storage.getQuarantinedDeletedRecordCount());

        now.addAndGet(HOUR);
        assertEquals(1, storage.getQuarantinedDeletedRecordCount());
        assertEquals(2, storage.getReusableDeletedRecordCount());

        now.addAndGet(HOUR);
        assertEquals(0, storage.getQuarantinedDeletedRecordCount());
        assertEquals(3, storage.getReusableDeletedRecordCount());
    }

    @Test
    public void printing_of_deleted_record_info_does_not_fail() {
        final Storage<Integer> storage = storage(QUARANTINE);
        assertDoesNotThrow(storage::printDeletedRecordInfo);

        for (int i = 0; i < 5; i++) {
            storage.addRecord();
        }
        storage.deleteRecord(2);
        storage.deleteRecord(3);
        storage.deleteRecord(5);

        assertDoesNotThrow(storage::printDeletedRecordInfo);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // property test against a simple model

    @Test
    public void random_operations___ids_are_never_reused_before_quarantine_ends() {
        final Random random = new Random(20261014);
        final Storage<Integer> storage = storage(QUARANTINE);

        final Set<Integer> alive = new HashSet<>();
        final Map<Integer, Long> deletedAt = new HashMap<>();

        for (int step = 0; step < 20_000; step++) {
            now.addAndGet(random.nextInt(5 * (int) MINUTE));

            final int dice = random.nextInt(100);
            if (dice < 55 || alive.isEmpty()) {
                final int id = storage.addRecord();

                assertFalse(alive.contains(id), "an alive id was issued again: " + id);
                final Long deleted = deletedAt.remove(id);
                if (deleted != null) {
                    // the deletion time is truncated to a minute, so the real age is not less than the quarantine minus a minute
                    assertTrue(now.get() - deleted >= QUARANTINE.toMillis() - MINUTE,
                            "id " + id + " was reused after " + (now.get() - deleted) / MINUTE + " minutes");
                }
                alive.add(id);
            } else {
                final List<Integer> list = new ArrayList<>(alive);
                final int id = list.get(random.nextInt(list.size()));
                storage.deleteRecord(id);
                alive.remove(id);
                deletedAt.put(id, now.get());
            }

            if (step % 997 == 0) {
                assertEquals(alive.size(), storage.getCount());
            }
        }

        assertEquals(alive, new HashSet<>(storage.all().toList()));
    }

    @Test
    public void random_operations_with_reloads___state_is_consistent() throws Exception {
        final Random random = new Random(42);
        Storage<Integer> storage = storage(QUARANTINE);

        final Set<Integer> alive = new HashSet<>();
        final Map<Integer, Long> deletedAt = new HashMap<>();

        for (int step = 0; step < 5_000; step++) {
            now.addAndGet(random.nextInt((int) (10 * MINUTE)));

            final int dice = random.nextInt(100);
            if (dice < 50 || alive.isEmpty()) {
                final int id = storage.addRecord();
                assertFalse(alive.contains(id));
                final Long deleted = deletedAt.remove(id);
                if (deleted != null) {
                    assertTrue(now.get() - deleted >= QUARANTINE.toMillis() - MINUTE);
                }
                alive.add(id);
                storage.set(id, intField(storage), id * 3);
            } else if (dice < 98) {
                final List<Integer> list = new ArrayList<>(alive);
                final int id = list.get(random.nextInt(list.size()));
                storage.deleteRecord(id);
                alive.remove(id);
                deletedAt.put(id, now.get());
            } else {
                storage = reload(storage, QUARANTINE);
            }
        }

        assertEquals(alive, new HashSet<>(storage.all().toList()));
        for (final int id : alive) {
            assertEquals(id * 3, storage.getAsInt(id, intField(storage)));
        }
    }
}
