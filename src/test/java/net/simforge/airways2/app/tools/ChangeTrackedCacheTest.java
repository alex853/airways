package net.simforge.airways2.app.tools;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChangeTrackedCacheTest {
    private final AtomicLong stamp = new AtomicLong(1);
    private final AtomicInteger builds = new AtomicInteger();

    private final ChangeTrackedCache<Object> cache = new ChangeTrackedCache<>(
            "test",
            () -> new long[]{stamp.get()},
            () -> {
                builds.incrementAndGet();
                return new Object();
            });

    @Test
    public void test__value_is_built_once_while_the_stamps_are_the_same() {
        final Object first = cache.get();

        assertSame(first, cache.get());
        assertSame(first, cache.get());
        assertEquals(1, builds.get());
        assertEquals(1, cache.getRebuildCount());
    }

    @Test
    public void test__value_is_rebuilt_when_the_stamps_change() {
        final Object first = cache.get();

        stamp.incrementAndGet();
        final Object second = cache.get();

        assertNotSame(first, second);
        assertSame(second, cache.get());
        assertEquals(2, builds.get());
    }

    @Test
    public void test__every_stamp_of_several_matters() {
        final AtomicLong first = new AtomicLong(1);
        final AtomicLong second = new AtomicLong(1);
        final AtomicInteger counter = new AtomicInteger();
        final ChangeTrackedCache<Integer> two = new ChangeTrackedCache<>(
                "two", () -> new long[]{first.get(), second.get()}, counter::incrementAndGet);

        assertEquals(1, two.get());
        assertEquals(1, two.get());
        first.incrementAndGet();
        assertEquals(2, two.get());
        second.incrementAndGet();
        assertEquals(3, two.get());
        assertEquals(3, two.get());
    }

    @Test
    public void test__change_during_the_build_leads_to_one_more_build() {
        // the stamps are read before the build, so a change made while the value is being built is not lost
        final AtomicInteger counter = new AtomicInteger();
        final ChangeTrackedCache<Integer> racing = new ChangeTrackedCache<>(
                "racing",
                () -> new long[]{stamp.get()},
                () -> {
                    final int number = counter.incrementAndGet();
                    if (number == 1) {
                        stamp.incrementAndGet(); // the data changes in the middle of the first build
                    }
                    return number;
                });

        assertEquals(1, racing.get());
        assertEquals(2, racing.get()); // the first value was built with the old stamp, it must be rebuilt
        assertEquals(2, racing.get());
    }

    @Test
    public void test__concurrent_requests_cause_one_build() throws Exception {
        final CountDownLatch builderStarted = new CountDownLatch(1);
        final CountDownLatch releaseBuilder = new CountDownLatch(1);
        final AtomicInteger counter = new AtomicInteger();
        final ChangeTrackedCache<Integer> slow = new ChangeTrackedCache<>(
                "slow",
                () -> new long[]{stamp.get()},
                () -> {
                    final int number = counter.incrementAndGet();
                    builderStarted.countDown();
                    try {
                        releaseBuilder.await(5, TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return number;
                });

        final ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            final java.util.List<Future<Integer>> futures = new java.util.ArrayList<>();
            futures.add(executor.submit(slow::get));
            builderStarted.await(5, TimeUnit.SECONDS);
            for (int i = 0; i < 7; i++) {
                futures.add(executor.submit(slow::get));
            }
            releaseBuilder.countDown();

            for (final Future<Integer> future : futures) {
                assertEquals(1, future.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, counter.get());
        } finally {
            executor.shutdownNow();
        }
    }
}
