package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A storage with the pages (Builder#paged) is the same storage as the one which is one array: the same records, the same ids, the same file. The tests make the same changes in both and compare them.
 */
class StoragePagedTest {
    private static final String NAME = "paged-test";

    // the record is 1 + 4 + 3 + 8 + 4 = 20 bytes, so a page of 100 bytes holds 5 records and a page of 90 bytes holds 4 (10 bytes stay unused)
    private static Storage<Integer> flat() {
        return Storage.<Integer>builder()
                .name(NAME)
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Signed32bit))
                .withDataField(DataField.of(DataType.Unsigned24bit))
                .withDataField(DataField.of(DataType.PlainString).length(7))
                .withDataField(DataField.of(DataType.Float))
                .withHeartbeatIndex(0)
                .deletedRecordQuarantine(Duration.ZERO)
                .build();
    }

    private static Storage<Integer> paged(final int pageSizeBytes) {
        return Storage.<Integer>builder()
                .name(NAME)
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Signed32bit))
                .withDataField(DataField.of(DataType.Unsigned24bit))
                .withDataField(DataField.of(DataType.PlainString).length(7))
                .withDataField(DataField.of(DataType.Float))
                .withHeartbeatIndex(0)
                .deletedRecordQuarantine(Duration.ZERO)
                .paged(pageSizeBytes)
                .build();
    }

    @Test
    public void test__same_changes_give_the_same_file(@TempDir final Path dir) throws IOException {
        for (final int pageSize : new int[]{20, 21, 90, 100, 1000, Storage.DEFAULT_PAGE_SIZE_BYTES}) {
            final Storage<Integer> flat = flat();
            final Storage<Integer> paged = paged(pageSize);
            final Path flatDir = Files.createDirectories(dir.resolve("flat-" + pageSize));
            final Path pagedDir = Files.createDirectories(dir.resolve("paged-" + pageSize));

            final Random random = new Random(pageSize);
            final List<Integer> alive = new ArrayList<>();
            for (int step = 0; step < 3000; step++) {
                final int action = random.nextInt(10);
                if (action < 4 || alive.isEmpty()) {
                    final int flatId = flat.addRecord();
                    final int pagedId = paged.addRecord();
                    assertEquals(flatId, pagedId);
                    alive.add(flatId);
                    final int heartbeat = random.nextInt(1000);
                    final int number = random.nextInt(1 << 24);
                    final String text = random.nextBoolean() ? null : "t" + random.nextInt(99999);
                    final float value = random.nextFloat();
                    flat.set(flatId, flat.getDataField(0), heartbeat);
                    paged.set(pagedId, paged.getDataField(0), heartbeat);
                    flat.set(flatId, flat.getDataField(1), number);
                    paged.set(pagedId, paged.getDataField(1), number);
                    flat.set(flatId, flat.getDataField(2), text);
                    paged.set(pagedId, paged.getDataField(2), text);
                    flat.set(flatId, flat.getDataField(3), value);
                    paged.set(pagedId, paged.getDataField(3), value);
                } else if (action < 7) {
                    final int id = alive.get(random.nextInt(alive.size()));
                    final int heartbeat = random.nextInt(1000);
                    flat.set(id, flat.getDataField(0), heartbeat);
                    paged.set(id, paged.getDataField(0), heartbeat);
                } else if (action < 9) {
                    final int id = alive.get(random.nextInt(alive.size()));
                    final int number = random.nextInt(1 << 24);
                    flat.set(id, flat.getDataField(1), number);
                    paged.set(id, paged.getDataField(1), number);
                } else {
                    final int id = alive.remove(random.nextInt(alive.size()));
                    flat.deleteRecord(id);
                    paged.deleteRecord(id);
                }
            }

            assertEquals(flat.getCount(), paged.getCount());
            for (final int id : alive) {
                assertEquals(flat.getAsInt(id, flat.getDataField(0)), paged.getAsInt(id, paged.getDataField(0)));
                assertEquals(flat.getAsInt(id, flat.getDataField(1)), paged.getAsInt(id, paged.getDataField(1)));
                assertEquals(flat.getAsString(id, flat.getDataField(2)), paged.getAsString(id, paged.getDataField(2)));
                assertEquals(flat.getAsFloat(id, flat.getDataField(3)), paged.getAsFloat(id, paged.getDataField(3)));
            }
            assertTrue(paged.checkHeartbeatIndex().isEmpty());

            flat.save(flatDir);
            paged.save(pagedDir);
            assertArrayEquals(Files.readAllBytes(flatDir.resolve(NAME)), Files.readAllBytes(pagedDir.resolve(NAME)), "page size " + pageSize);
        }
    }

    @Test
    public void test__the_file_of_either_storage_loads_into_the_other(@TempDir final Path dir) throws IOException {
        final Path flatDir = Files.createDirectories(dir.resolve("flat"));
        final Path pagedDir = Files.createDirectories(dir.resolve("paged"));
        final Path backDir = Files.createDirectories(dir.resolve("back"));
        final Path back2Dir = Files.createDirectories(dir.resolve("back2"));

        final Storage<Integer> flat = flat();
        for (int i = 1; i <= 123; i++) {
            final int id = flat.addRecord();
            flat.set(id, flat.getDataField(0), i * 3);
            flat.set(id, flat.getDataField(2), "id" + i);
        }
        flat.deleteRecord(50);
        flat.save(flatDir);

        // the file of the flat storage into the paged one (123 records, a page of 4 records is not full at the end)
        final Storage<Integer> paged = paged(90);
        paged.loadIfExists(flatDir);
        assertEquals(122, paged.getCount());
        assertEquals(120 * 3, paged.getAsInt(120, paged.getDataField(0)));
        assertEquals("id77", paged.getAsString(77, paged.getDataField(2)));
        assertTrue(paged.checkHeartbeatIndex().isEmpty());
        assertEquals(1, paged.nextForHeartbeat(Integer.MAX_VALUE).orElseThrow());

        // it grows after the load
        assertEquals(50, paged.addRecord()); // the deleted record is reused
        assertEquals(124, paged.addRecord());
        paged.set(124, paged.getDataField(0), 7);

        paged.save(pagedDir);
        final Storage<Integer> flatAgain = flat();
        flatAgain.loadIfExists(pagedDir);
        assertEquals(124, flatAgain.getCount());
        assertEquals(7, flatAgain.getAsInt(124, flatAgain.getDataField(0)));
        assertEquals("id123", flatAgain.getAsString(123, flatAgain.getDataField(2)));

        flatAgain.save(backDir);
        final Storage<Integer> pagedAgain = paged(Storage.DEFAULT_PAGE_SIZE_BYTES);
        pagedAgain.loadIfExists(backDir);
        pagedAgain.save(back2Dir);
        assertArrayEquals(Files.readAllBytes(pagedDir.resolve(NAME)), Files.readAllBytes(back2Dir.resolve(NAME)));
    }

    private static Storage<Integer> pagedWithQuarantine(final int pageSizeBytes, final long[] now) {
        return Storage.<Integer>builder()
                .name(NAME)
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Signed32bit))
                .withDataField(DataField.of(DataType.Unsigned24bit))
                .withDataField(DataField.of(DataType.PlainString).length(7))
                .withDataField(DataField.of(DataType.Float))
                .withHeartbeatIndex(0)
                .deletedRecordQuarantine(Duration.ofHours(1))
                .clock(() -> now[0])
                .paged(pageSizeBytes)
                .build();
    }

    @Test
    public void test__try_trim_tail_drops_the_pages_without_live_records_even_in_the_quarantine(@TempDir final Path dir) throws IOException {
        final long[] now = {Instant.parse("2026-11-03T10:00:00Z").toEpochMilli()};
        final Storage<Integer> paged = pagedWithQuarantine(100, now); // 5 records of 20 bytes in a page
        for (int i = 1; i <= 23; i++) {
            final int id = paged.addRecord();
            paged.set(id, paged.getDataField(0), i);
        }
        assertEquals(5, paged.getPageCount());
        assertEquals(0, paged.tryTrimTail().droppedRecords());

        // the last 8 records die (the pages of 21..25 and 16..20 have no live records now), then 3 more in the middle
        for (int i = 16; i <= 23; i++) {
            paged.deleteRecord(i);
        }
        paged.deleteRecord(4);
        paged.deleteRecord(5);
        paged.deleteRecord(6);
        assertEquals(11, paged.getQuarantinedDeletedRecordCount());

        // all of them are in the quarantine, it does not matter for the pages at the end
        assertEquals(8, paged.tryTrimTail().droppedRecords());
        assertEquals(3, paged.getPageCount());
        assertEquals(12, paged.getCount());
        assertEquals(3, paged.getQuarantinedDeletedRecordCount());
        assertEquals(0, paged.tryTrimTail().droppedRecords());

        // the ids go on after the end, and the holes in the middle are reused when their quarantine is over
        assertEquals(16, paged.addRecord());
        assertEquals(0, paged.getAsInt(16, paged.getDataField(0)));
        now[0] += Duration.ofHours(2).toMillis();
        assertEquals(4, paged.addRecord());
        assertEquals(5, paged.addRecord());
        assertEquals(6, paged.addRecord());
        assertEquals(17, paged.addRecord());
        assertEquals(17, paged.getCount());

        paged.save(dir);
        assertEquals(17 * 20, Files.size(dir.resolve(NAME)));
    }

    @Test
    public void test__try_trim_tail_keeps_the_page_with_a_live_record() {
        final long[] now = {Instant.parse("2026-11-03T10:00:00Z").toEpochMilli()};
        final Storage<Integer> paged = pagedWithQuarantine(100, now);
        for (int i = 1; i <= 23; i++) {
            paged.addRecord();
        }
        // the page of 21..25 has the live record 21, the page before it has none
        for (int i = 16; i <= 20; i++) {
            paged.deleteRecord(i);
        }
        paged.deleteRecord(22);
        paged.deleteRecord(23);
        final Storage.TrimResult kept = paged.tryTrimTail();
        assertEquals(0, kept.droppedRecords());
        assertEquals(5, kept.pagesBefore());
        assertEquals(5, kept.pagesAfter());
        assertEquals(23, kept.recordsBefore());
        assertEquals(3, kept.recordsOnLastPage());
        assertEquals(1, kept.liveOnLastPage()); // the record 21
        assertEquals(5, paged.getPageCount());

        // when the last live record of the last page dies, the page goes and the one before it too
        paged.deleteRecord(21);
        assertEquals(8, paged.tryTrimTail().droppedRecords());
        assertEquals(3, paged.getPageCount());
        assertEquals(15, paged.getCount());
        assertEquals(0, paged.getQuarantinedDeletedRecordCount());
    }

    @Test
    public void test__try_trim_tail_drops_everything_when_everything_died() {
        final long[] now = {Instant.parse("2026-11-03T10:00:00Z").toEpochMilli()};
        final Storage<Integer> paged = pagedWithQuarantine(100, now);
        for (int i = 1; i <= 12; i++) {
            paged.addRecord();
        }
        for (int i = 1; i <= 12; i++) {
            paged.deleteRecord(i);
        }

        assertEquals(12, paged.tryTrimTail().droppedRecords());
        assertEquals(0, paged.getPageCount());
        assertEquals(0, paged.getCount());
        assertEquals(0, paged.getQuarantinedDeletedRecordCount() + paged.getReusableDeletedRecordCount());
        assertEquals(1, paged.addRecord());
        assertTrue(paged.checkHeartbeatIndex().isEmpty());
    }

    @Test
    public void test__a_new_storage_is_empty_and_a_missing_file_is_an_empty_storage(@TempDir final Path dir) throws IOException {
        final Storage<Integer> paged = paged(100);
        assertEquals(0, paged.getCount());

        paged.loadIfExists(dir);
        assertEquals(0, paged.getCount());

        paged.save(dir);
        assertEquals(0, Files.size(dir.resolve(NAME)));

        paged.loadIfExists(dir);
        assertEquals(1, paged.addRecord());
    }

    @Test
    public void test__a_record_is_never_split_between_the_pages() {
        // 20 bytes per record, a page of 39 bytes holds one record
        final Storage<Integer> paged = paged(39);
        for (int i = 1; i <= 50; i++) {
            final int id = paged.addRecord();
            paged.set(id, paged.getDataField(0), -i);
            paged.set(id, paged.getDataField(2), "abcdefg");
            paged.set(id, paged.getDataField(3), i / 7.0f);
        }
        for (int i = 1; i <= 50; i++) {
            assertEquals(-i, paged.getAsInt(i, paged.getDataField(0)));
            assertEquals("abcdefg", paged.getAsString(i, paged.getDataField(2)));
            assertEquals(i / 7.0f, paged.getAsFloat(i, paged.getDataField(3)));
        }
        paged.set(10, paged.getDataField(2), (String) null);
        assertNull(paged.getAsString(10, paged.getDataField(2)));
        assertEquals("abcdefg", paged.getAsString(11, paged.getDataField(2)));
    }

    @Test
    public void test__growth_step_does_not_go_with_paged() {
        assertThrows(IllegalStateException.class, () -> Storage.<Integer>builder()
                .name(NAME)
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Signed32bit))
                .growthStep(10)
                .paged()
                .build());
    }

    @Test
    public void test__a_record_which_does_not_fit_into_a_page_is_refused() {
        assertThrows(IllegalArgumentException.class, () -> paged(19));
    }

    @Test
    public void test__a_file_which_is_not_a_whole_number_of_records_loads_the_whole_ones(@TempDir final Path dir) throws IOException {
        Files.write(dir.resolve(NAME), new byte[20 * 3 + 5]);
        final Storage<Integer> paged = paged(100);
        paged.loadIfExists(dir);
        assertEquals(3, paged.getCount());
        assertEquals(4, paged.addRecord());
    }
}
