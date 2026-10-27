package net.simforge.airways2.world.processors;

import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.EventsToProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Processing {
    private static final Logger log = LoggerFactory.getLogger(Processing.class);
    /**
     * The circuit breaker of the processing cycles: at most so many objects (or events) are processed by one processor in one
     * world step. It protects the world from an endless cycle (a processor which does not clear the heartbeat of its object
     * would get the same object again and again) and works as a limiter of the load when a lot of objects are due at once
     * (the rest is processed in the next steps). The limit was introduced with the generic processing tools
     * (commit b187d2e8, 2025-10-10) and raised from 1000 to 2000 on 2026-04-11 (commit e2e96242). It is covered
     * by ProcessingHeartbeatTest, see also docs/heartbeat-index.md.
     */
    static final int CIRCUIT_BREAKER_COUNTER_LIMIT = 2_000;

    public static <T> void heartbeat(final Supplier<Optional<T>> nextForHeartbeat,
                                     final Consumer<T> processor) {
        heartbeat(nextForHeartbeat, processor, CIRCUIT_BREAKER_COUNTER_LIMIT, true);
    }

    /**
     * The same cycle, but a processor which has to work at a given pace takes at most {@code maxObjectsPerStep} objects in one world step. The rest stays
     * due and is taken in the next steps; it is not an error (unlike the circuit breaker), so nothing is logged when the limit is reached.
     */
    public static <T> void heartbeat(final Supplier<Optional<T>> nextForHeartbeat,
                                     final Consumer<T> processor,
                                     final int maxObjectsPerStep) {
        heartbeat(nextForHeartbeat, processor, Math.min(maxObjectsPerStep, CIRCUIT_BREAKER_COUNTER_LIMIT), false);
    }

    private static <T> void heartbeat(final Supplier<Optional<T>> nextForHeartbeat,
                                      final Consumer<T> processor,
                                      final int limit,
                                      final boolean limitIsAnError) {
        String processorName = extractClassName(processor.getClass().getName());

        try (Timing.Timer ignored0 = Timing.label("Processing.heartbeat - " + processorName + " - cycle")) {
            int circuitBreakerCounter = 0;

            while (true) {
                final Optional<T> next = nextForHeartbeat.get();
                if (next.isEmpty()) {
                    break;
                }

                if (circuitBreakerCounter == limit) {
                    if (limitIsAnError) {
                        log.error("too many objects to process, the last one is {}", next.get());
                    }
                    break;
                }
                circuitBreakerCounter++;

                try (final Timing.Timer ignored = Timing.label("Processing.heartbeat - " + processorName + " - PROCESS")) {
                    processor.accept(next.get());
                } catch (final RuntimeException e) {
                    log.error("heartbeat processing error for object {}", next.get(), e);
                    throw e;
                }
            }
        }
    }

    public static void event(final World world,
                             final EventsToProcess.Type eventType,
                             final Consumer<EventsToProcess.Event> handler) {
        try (Timing.Timer ignored0 = Timing.label("Processing.event - " + eventType + " - cycle")) {
            final int worldTime = world.getWorldTime();

            int circuitBreakerCounter = 0;

            while (true) {
                final Optional<EventsToProcess.Event> event = world.eventsToProcess().findFirstActiveEvent(eventType, worldTime);
                if (event.isEmpty()) {
                    break;
                }

                if (circuitBreakerCounter == CIRCUIT_BREAKER_COUNTER_LIMIT) {
                    log.error("too many events to process, the last one is {}", event.get());
                    break;
                }
                circuitBreakerCounter++;

                try (final Timing.Timer ignored = Timing.label("Processing.event - " + eventType.name() + " - PROCESS")) {
                    handler.accept(event.get());
                }

                event.get().setProcessedStatus();
            }
        }
    }

    private static String extractClassName(final String name) {
        final int lastDot = name.lastIndexOf('.');
        final int firstDollar = name.indexOf('$', lastDot);
        return (lastDot >= 0 && firstDollar >= 0)
                ? name.substring(lastDot+1, firstDollar)
                : name;
    }
}
