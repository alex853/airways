package net.simforge.airways2.world.processors;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Processing.heartbeat with a real storage with the heartbeat index, including the circuit breaker which stops the cycle:
 * it protects from an endless cycle and limits the load of one world step.
 */
class ProcessingHeartbeatTest {
    private static final int LIMIT = Processing.CIRCUIT_BREAKER_COUNTER_LIMIT;

    private final Storage<Integer> storage = Storage.<Integer>builder()
            .name("processing-test")
            .withInstantiator(recordId -> recordId)
            .withDataField(DataField.of(DataType.Signed32bit))
            .withHeartbeatIndex(0)
            .build();
    private final DataField heartbeatField = storage.getDataField(0);

    private int record(final int heartbeatTime) {
        final int recordId = storage.addRecord();
        storage.set(recordId, heartbeatField, heartbeatTime);
        return recordId;
    }

    private void clear(final int recordId) {
        storage.set(recordId, heartbeatField, 0);
    }

    @Test
    public void test__nothing_is_processed_when_nothing_is_due() {
        record(500);
        record(0);
        final List<Integer> processed = new ArrayList<>();

        Processing.heartbeat(() -> storage.nextForHeartbeat(100), processed::add);

        assertTrue(processed.isEmpty());
    }

    @Test
    public void test__all_due_records_are_processed_by_time_and_then_by_id_and_the_future_ones_are_left() {
        final int late = record(300);
        final int early = record(100);
        final int future = record(900);
        final int middleFirst = record(200);
        final int middleSecond = record(200);
        final List<Integer> processed = new ArrayList<>();

        Processing.heartbeat(() -> storage.nextForHeartbeat(500), id -> {
            processed.add(id);
            clear(id);
        });

        assertEquals(List.of(early, middleFirst, middleSecond, late), processed);
        assertEquals(Optional.of(future), storage.nextForHeartbeat(1000));
    }

    @Test
    public void test__record_which_the_processor_moves_to_the_future_is_processed_once() {
        final int recordId = record(100);
        final AtomicInteger calls = new AtomicInteger();

        Processing.heartbeat(() -> storage.nextForHeartbeat(150), id -> {
            calls.incrementAndGet();
            storage.set(id, heartbeatField, 400);
        });

        assertEquals(1, calls.get());
        assertEquals(Optional.of(recordId), storage.nextForHeartbeat(400));
    }

    @Test
    public void test__record_which_becomes_due_while_processing_is_processed_in_the_same_cycle() {
        final int first = record(100);
        final int second = record(900); // not due yet
        final List<Integer> processed = new ArrayList<>();

        Processing.heartbeat(() -> storage.nextForHeartbeat(200), id -> {
            processed.add(id);
            clear(id);
            if (id == first) {
                storage.set(second, heartbeatField, 150); // the first record wakes the second one up
            }
        });

        assertEquals(List.of(first, second), processed);
    }

    @Test
    public void test__circuit_breaker_stops_a_processor_which_never_clears_the_heartbeat() {
        record(100);
        final AtomicInteger calls = new AtomicInteger();

        // the same record is returned again and again, the cycle must still end
        Processing.heartbeat(() -> storage.nextForHeartbeat(200), id -> calls.incrementAndGet());

        assertEquals(LIMIT, calls.get());
    }

    @Test
    public void test__circuit_breaker_stops_a_record_which_always_asks_for_now() {
        final int recordId = record(100);
        final AtomicInteger calls = new AtomicInteger();

        Processing.heartbeat(() -> storage.nextForHeartbeat(200), id -> {
            calls.incrementAndGet();
            storage.set(id, heartbeatField, 200); // is due again at once
        });

        assertEquals(LIMIT, calls.get());
        assertEquals(Optional.of(recordId), storage.nextForHeartbeat(200));
    }

    @Test
    public void test__circuit_breaker_limits_the_load_and_the_rest_is_left_for_the_next_cycle() {
        final int total = LIMIT + 500;
        for (int i = 0; i < total; i++) {
            record(100 + i % 50);
        }
        final AtomicInteger first = new AtomicInteger();
        final AtomicInteger second = new AtomicInteger();
        final AtomicInteger third = new AtomicInteger();

        Processing.heartbeat(() -> storage.nextForHeartbeat(1000), id -> {
            first.incrementAndGet();
            clear(id);
        });
        Processing.heartbeat(() -> storage.nextForHeartbeat(1000), id -> {
            second.incrementAndGet();
            clear(id);
        });
        Processing.heartbeat(() -> storage.nextForHeartbeat(1000), id -> {
            third.incrementAndGet();
            clear(id);
        });

        assertEquals(LIMIT, first.get());
        assertEquals(500, second.get());
        assertEquals(0, third.get());
        assertEquals(List.of(), storage.checkHeartbeatIndex());
    }

    @Test
    public void test__exactly_the_limit_of_records_is_processed_without_trouble() {
        for (int i = 0; i < LIMIT; i++) {
            record(100);
        }
        final AtomicInteger calls = new AtomicInteger();

        Processing.heartbeat(() -> storage.nextForHeartbeat(200), id -> {
            calls.incrementAndGet();
            clear(id);
        });

        assertEquals(LIMIT, calls.get());
        assertTrue(storage.nextForHeartbeat(200).isEmpty());
    }

    @Test
    public void test__processor_error_stops_the_cycle_and_is_not_swallowed() {
        final int first = record(100);
        final int second = record(110);
        final List<Integer> processed = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> Processing.heartbeat(() -> storage.nextForHeartbeat(200), id -> {
            processed.add(id);
            clear(id);
            if (id == first) {
                throw new IllegalStateException("test");
            }
        }));

        assertEquals(List.of(first), processed);
        assertEquals(Optional.of(second), storage.nextForHeartbeat(200)); // the rest waits for the next cycle
    }

    @Test
    public void test__a_limited_cycle_takes_only_the_given_number_of_records_and_the_rest_is_taken_by_the_next_one() {
        final int[] ids = new int[25];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = record(100 + i);
        }
        final List<Integer> processed = new ArrayList<>();
        final Runnable cycle = () -> Processing.heartbeat(() -> storage.nextForHeartbeat(1000), id -> {
            processed.add(id);
            clear(id);
        }, 10);

        cycle.run();
        assertEquals(10, processed.size());
        assertEquals(ids[0], processed.get(0));
        assertEquals(ids[9], processed.get(9));

        cycle.run();
        cycle.run();
        assertEquals(25, processed.size());
        assertEquals(ids[24], processed.get(24));
    }

    @Test
    public void test__a_limited_cycle_is_never_above_the_circuit_breaker() {
        final int count = LIMIT + 50;
        for (int i = 0; i < count; i++) {
            record(100 + i);
        }
        final AtomicInteger calls = new AtomicInteger();

        Processing.heartbeat(() -> storage.nextForHeartbeat(100_000), id -> {
            calls.incrementAndGet();
            clear(id);
        }, Integer.MAX_VALUE);

        assertEquals(LIMIT, calls.get());
    }
}
