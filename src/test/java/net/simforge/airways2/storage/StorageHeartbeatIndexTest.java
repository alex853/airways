package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageHeartbeatIndexTest {
    // the heartbeat time is the field number 1
    private static Storage.Builder<Integer> builder() {
        return Storage.<Integer>builder()
                .name("heartbeat-test")
                .withInstantiator(recordId -> recordId)
                .withDataField(DataField.of(DataType.Unsigned8bit))
                .withDataField(DataField.of(DataType.Signed32bit))
                .withDataField(DataField.of(DataType.Unsigned16bit));
    }

    private final Storage<Integer> storage = builder().withHeartbeatIndex(1).build();
    private final DataField statusField = storage.getDataField(0);
    private final DataField heartbeatField = storage.getDataField(1);
    private final DataField otherField = storage.getDataField(2);

    private int record(final int heartbeatTime) {
        final int recordId = storage.addRecord();
        storage.set(recordId, heartbeatField, heartbeatTime);
        return recordId;
    }

    private Optional<Integer> next(final int worldTime) {
        return storage.nextForHeartbeat(worldTime);
    }

    private void assertConsistent() {
        assertEquals(List.of(), storage.checkHeartbeatIndex());
    }

    @Test
    public void test__new_record_has_no_heartbeat() {
        storage.addRecord();

        assertTrue(next(Integer.MAX_VALUE).isEmpty());
        assertConsistent();
    }

    @Test
    public void test__setting_a_heartbeat_makes_the_record_due_at_that_time() {
        final int recordId = record(100);

        assertTrue(next(99).isEmpty());
        assertEquals(Optional.of(recordId), next(100));
        assertConsistent();
    }

    @Test
    public void test__earliest_record_goes_first_whatever_its_id_is() {
        final int late = record(300);
        final int early = record(100);
        final int middle = record(200);

        assertEquals(Optional.of(early), next(1000));
        storage.set(early, heartbeatField, 0);
        assertEquals(Optional.of(middle), next(1000));
        storage.set(middle, heartbeatField, 0);
        assertEquals(Optional.of(late), next(1000));
        assertConsistent();
    }

    @Test
    public void test__same_heartbeat_goes_in_the_order_of_ids() {
        final int first = record(100);
        final int second = record(100);

        assertEquals(Optional.of(first), next(100));
        storage.set(first, heartbeatField, 500);
        assertEquals(Optional.of(second), next(100));
    }

    @Test
    public void test__clearing_the_heartbeat_removes_the_record_from_the_index() {
        final int recordId = record(100);

        storage.set(recordId, heartbeatField, 0);

        assertTrue(next(1000).isEmpty());
        assertConsistent();
    }

    @Test
    public void test__changing_the_heartbeat_moves_the_record() {
        final int recordId = record(100);

        storage.set(recordId, heartbeatField, 400);

        assertTrue(next(399).isEmpty());
        assertEquals(Optional.of(recordId), next(400));
        assertConsistent();
    }

    @Test
    public void test__setting_the_same_heartbeat_again_keeps_one_entry() {
        final int recordId = record(100);

        storage.set(recordId, heartbeatField, 100);
        storage.set(recordId, heartbeatField, 100);

        assertEquals(Optional.of(recordId), next(100));
        storage.set(recordId, heartbeatField, 0);
        assertTrue(next(1000).isEmpty());
        assertConsistent();
    }

    @Test
    public void test__other_fields_do_not_touch_the_index() {
        final int recordId = record(100);

        storage.set(recordId, statusField, 7);
        storage.set(recordId, otherField, 4000);

        assertEquals(Optional.of(recordId), next(100));
        assertEquals(7, storage.getAsInt(recordId, statusField));
        assertConsistent();
    }

    @Test
    public void test__unsafe_setter_updates_the_index() {
        final int recordId = storage.addRecord();

        storage.setUnsafe(recordId, heartbeatField, 250);

        assertEquals(Optional.of(recordId), next(250));
        assertConsistent();
    }

    @Test
    public void test__deleted_record_leaves_the_index() {
        final int deleted = record(100);
        final int kept = record(200);

        storage.deleteRecord(deleted);

        assertEquals(Optional.of(kept), next(1000));
        assertConsistent();
        storage.deleteRecord(kept);
        assertTrue(next(1000).isEmpty());
        assertConsistent();
    }

    @Test
    public void test__reused_record_starts_without_a_heartbeat() {
        final Storage<Integer> reusing = builder().withHeartbeatIndex(1).deletedRecordQuarantine(Duration.ZERO).build();
        final DataField heartbeat = reusing.getDataField(1);
        final int id = reusing.addRecord();
        reusing.set(id, heartbeat, 100);
        reusing.deleteRecord(id);

        final int reused = reusing.addRecord();

        assertEquals(id, reused);
        assertTrue(reusing.nextForHeartbeat(1000).isEmpty());
        assertEquals(List.of(), reusing.checkHeartbeatIndex());
        reusing.set(reused, heartbeat, 300);
        assertEquals(Optional.of(reused), reusing.nextForHeartbeat(300));
        assertEquals(List.of(), reusing.checkHeartbeatIndex());
    }

    @Test
    public void test__extreme_heartbeat_values() {
        final int negative = record(-5);
        final int max = record(Integer.MAX_VALUE);

        assertEquals(Optional.of(negative), next(0));
        storage.set(negative, heartbeatField, 0);
        assertTrue(next(Integer.MAX_VALUE - 1).isEmpty());
        assertEquals(Optional.of(max), next(Integer.MAX_VALUE));
        assertConsistent();
    }

    @Test
    public void test__index_is_rebuilt_after_the_loading(@TempDir final Path dir) throws IOException {
        final int due = record(100);
        final int future = record(900);
        record(0);
        final int deleted = record(50);
        storage.deleteRecord(deleted);
        storage.save(dir);

        final Storage<Integer> loaded = builder().withHeartbeatIndex(1).build();
        loaded.loadIfExists(dir);

        assertEquals(Optional.of(due), loaded.nextForHeartbeat(100));
        assertTrue(loaded.nextForHeartbeat(99).isEmpty()); // the deleted one with 50 is not there
        loaded.set(due, loaded.getDataField(1), 0);
        assertEquals(Optional.of(future), loaded.nextForHeartbeat(1000));
        assertEquals(List.of(), loaded.checkHeartbeatIndex());
    }

    @Test
    public void test__loading_without_a_file_empties_the_index(@TempDir final Path dir) throws IOException {
        record(100);

        storage.loadIfExists(dir); // nothing is saved there

        assertTrue(next(1000).isEmpty());
        assertConsistent();
    }

    @Test
    public void test__reset_empties_the_index() {
        record(100);

        storage.reset();

        assertTrue(next(1000).isEmpty());
        assertConsistent();
    }

    @Test
    public void test__storage_without_the_index_refuses_to_answer() {
        final Storage<Integer> plain = builder().build();

        assertThrows(IllegalStateException.class, () -> plain.nextForHeartbeat(100));
        assertThrows(IllegalStateException.class, plain::checkHeartbeatIndex);
    }

    @Test
    public void test__index_field_must_exist_and_be_signed_32_bit() {
        assertThrows(IllegalArgumentException.class, () -> builder().withHeartbeatIndex(3).build()); // no such field
        assertThrows(IllegalArgumentException.class, () -> builder().withHeartbeatIndex(0).build()); // 8 bits
        assertThrows(IllegalArgumentException.class, () -> builder().withHeartbeatIndex(2).build()); // 16 bits
        assertThrows(IllegalArgumentException.class, () -> builder().withHeartbeatIndex(-1));
    }

    @Test
    public void test__next_for_heartbeat_gives_the_same_as_the_scan_with_the_condition() {
        // the index must say what the scan used to say: the first due record, only the order of the records is by the time
        final Random random = new Random(7);
        final List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            ids.add(record(random.nextInt(3) == 0 ? 0 : 1 + random.nextInt(1000)));
        }

        for (int worldTime = 1; worldTime <= 1100; worldTime += 37) {
            final int time = worldTime;
            final Optional<Integer> byIndex = next(time);
            final Optional<Integer> byScan = storage.filter(storage.nextForHeartbeatCondition(heartbeatField, time))
                    .min(Comparator.comparingInt((Integer id) -> storage.getAsInt(id, heartbeatField)).thenComparingInt(id -> id));
            assertEquals(byScan, byIndex, "world time " + time);
        }
    }

    @Test
    public void test__random_life_of_the_storage_keeps_the_index_consistent() {
        final Storage<Integer> life = builder().withHeartbeatIndex(1).deletedRecordQuarantine(Duration.ZERO).build();
        final DataField heartbeat = life.getDataField(1);
        final Random random = new Random(20261016);
        final List<Integer> alive = new ArrayList<>();

        for (int step = 0; step < 40_000; step++) {
            final int action = random.nextInt(10);
            if (action < 3 || alive.isEmpty()) {
                final int id = life.addRecord();
                alive.add(id);
                if (random.nextBoolean()) {
                    life.set(id, heartbeat, 1 + random.nextInt(500));
                }
            } else if (action < 8) {
                final int id = alive.get(random.nextInt(alive.size()));
                life.set(id, heartbeat, random.nextInt(4) == 0 ? 0 : 1 + random.nextInt(500));
            } else {
                final int id = alive.remove(random.nextInt(alive.size()));
                life.deleteRecord(id);
            }

            if (step % 200 == 0) {
                assertEquals(List.of(), life.checkHeartbeatIndex(), "step " + step);

                final int worldTime = 1 + random.nextInt(520);
                final Optional<Integer> expected = alive.stream()
                        .filter(id -> life.getAsInt(id, heartbeat) != 0 && life.getAsInt(id, heartbeat) <= worldTime)
                        .min(Comparator.comparingInt((Integer id) -> life.getAsInt(id, heartbeat)).thenComparingInt(id -> id));
                assertEquals(expected, life.nextForHeartbeat(worldTime), "step " + step);
            }
        }
        assertEquals(List.of(), life.checkHeartbeatIndex());
    }

    @Test
    public void test__index_changes_change_the_stamp_of_the_storage_too() {
        final int recordId = storage.addRecord();
        final long before = storage.getLastChangedAtMillis();

        storage.set(recordId, heartbeatField, 100);

        assertTrue(storage.getLastChangedAtMillis() > before);
    }
}
