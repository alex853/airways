package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatIndexTest {
    private final HeartbeatIndex index = new HeartbeatIndex();

    @Test
    public void test__empty_index_has_nothing() {
        assertEquals(0, index.size());
        assertTrue(index.firstDue(Integer.MAX_VALUE).isEmpty());
        assertTrue(index.earliestHeartbeatTime().isEmpty());
        assertTrue(index.entries().isEmpty());
    }

    @Test
    public void test__earliest_heartbeat_comes_first() {
        index.add(300, 1);
        index.add(100, 2);
        index.add(200, 3);

        assertEquals(OptionalInt.of(2), index.firstDue(1000));
        assertEquals(OptionalInt.of(100), index.earliestHeartbeatTime());
    }

    @Test
    public void test__records_with_the_same_heartbeat_go_in_the_order_of_their_ids() {
        index.add(100, 30);
        index.add(100, 10);
        index.add(100, 20);

        assertEquals(OptionalInt.of(10), index.firstDue(100));
        index.remove(100, 10);
        assertEquals(OptionalInt.of(20), index.firstDue(100));
        index.remove(100, 20);
        assertEquals(OptionalInt.of(30), index.firstDue(100));
    }

    @Test
    public void test__record_is_due_only_when_the_world_time_reaches_its_heartbeat() {
        index.add(100, 1);

        assertTrue(index.firstDue(99).isEmpty());
        assertEquals(OptionalInt.of(1), index.firstDue(100));
        assertEquals(OptionalInt.of(1), index.firstDue(101));
    }

    @Test
    public void test__zero_heartbeat_is_not_indexed() {
        index.add(0, 1);

        assertEquals(0, index.size());
        assertTrue(index.firstDue(Integer.MAX_VALUE).isEmpty());
        index.remove(0, 1); // nothing to remove, no error
        assertFalse(index.contains(0, 1));
    }

    @Test
    public void test__negative_and_extreme_heartbeats_keep_the_order() {
        index.add(Integer.MAX_VALUE, 1);
        index.add(5, 2);
        index.add(-7, 3);
        index.add(Integer.MIN_VALUE, 4);

        final List<int[]> entries = index.entries();
        assertEquals(List.of(4, 3, 2, 1), entries.stream().map(e -> e[1]).toList());
        assertEquals(Integer.MIN_VALUE, entries.get(0)[0]);
        assertEquals(Integer.MAX_VALUE, entries.get(3)[0]);
        assertEquals(OptionalInt.of(4), index.firstDue(0));
    }

    @Test
    public void test__big_record_ids_are_kept() {
        index.add(10, 16_777_215); // the max id of 24 bits
        index.add(10, 2_000_000_000);

        assertEquals(OptionalInt.of(16_777_215), index.firstDue(10));
        index.remove(10, 16_777_215);
        assertEquals(OptionalInt.of(2_000_000_000), index.firstDue(10));
    }

    @Test
    public void test__adding_the_same_entry_twice_gives_one_entry() {
        index.add(100, 1);
        index.add(100, 1);

        assertEquals(1, index.size());
    }

    @Test
    public void test__removing_an_unknown_entry_does_nothing() {
        index.add(100, 1);

        index.remove(100, 2);
        index.remove(101, 1);

        assertEquals(1, index.size());
        assertTrue(index.contains(100, 1));
    }

    @Test
    public void test__update_moves_the_record() {
        index.add(100, 1);

        index.update(1, 100, 500);

        assertFalse(index.contains(100, 1));
        assertTrue(index.contains(500, 1));
        assertTrue(index.firstDue(499).isEmpty());
        assertEquals(OptionalInt.of(1), index.firstDue(500));
    }

    @Test
    public void test__update_from_and_to_zero() {
        index.update(1, 0, 100); // a new heartbeat
        assertEquals(1, index.size());

        index.update(1, 100, 0); // heartbeat is cleared
        assertEquals(0, index.size());
    }

    @Test
    public void test__clear() {
        index.add(1, 1);
        index.add(2, 2);

        index.clear();

        assertEquals(0, index.size());
        assertTrue(index.firstDue(10).isEmpty());
    }

    @Test
    public void test__random_operations_match_a_naive_model() {
        // the model is a plain set of {time, id} pairs, the first due record is found by sorting it
        final Random random = new Random(20261016);
        final Set<Long> model = new HashSet<>();

        for (int step = 0; step < 60_000; step++) {
            final int id = 1 + random.nextInt(300);
            final int time = random.nextInt(8) == 0 ? 0 : random.nextInt(200) - 20;

            switch (random.nextInt(3)) {
                case 0 -> {
                    index.add(time, id);
                    if (time != 0) {
                        model.add(((long) time << 32) | id);
                    }
                }
                case 1 -> {
                    index.remove(time, id);
                    model.remove(((long) time << 32) | id);
                }
                default -> {
                    final int newTime = random.nextInt(200) - 20;
                    index.update(id, time, newTime);
                    model.remove(((long) time << 32) | id);
                    if (newTime != 0) {
                        model.add(((long) newTime << 32) | id);
                    }
                }
            }

            if (step % 25 == 0) {
                final int worldTime = random.nextInt(220) - 20;
                assertEquals(naiveFirstDue(model, worldTime), index.firstDue(worldTime), "step " + step);
                assertEquals(model.size(), index.size(), "step " + step);
            }
        }

        final List<Long> expectedOrder = new ArrayList<>(model);
        expectedOrder.sort(Comparator.naturalOrder());
        final List<int[]> entries = index.entries();
        assertEquals(expectedOrder.size(), entries.size());
        for (int i = 0; i < entries.size(); i++) {
            assertEquals((int) (expectedOrder.get(i) >> 32), entries.get(i)[0]);
            assertEquals((int) (long) expectedOrder.get(i), entries.get(i)[1]);
        }
    }

    private static OptionalInt naiveFirstDue(final Set<Long> model, final int worldTime) {
        return model.stream()
                .filter(key -> (int) (key >> 32) <= worldTime)
                .min(Comparator.naturalOrder())
                .map(key -> OptionalInt.of((int) (long) key))
                .orElse(OptionalInt.empty());
    }
}
