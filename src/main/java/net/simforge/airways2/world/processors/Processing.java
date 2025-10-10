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
    private static final Logger log = LoggerFactory.getLogger(FlightMissionProcessor.class);
    private static final int CIRCUIT_BREAKER_COUNTER_LIMIT = 1_000_000;

    public static <T> void heartbeat(final Supplier<Optional<T>> nextForHeartbeat,
                                     final Consumer<T> processor) {
        int circuitBreakerCounter = 0;

        while (true) {
            final Optional<T> next = nextForHeartbeat.get();
            if (next.isEmpty()) {
                break;
            }

            if (circuitBreakerCounter == CIRCUIT_BREAKER_COUNTER_LIMIT) {
                log.warn("too many objects to process, the last one is {}", next.get());
                break;
            }
            circuitBreakerCounter++;

            try (final Timing.Timer ignored = Timing.label("Processing.heartbeat - " + extractClassName(processor.getClass().getName()))) {
                processor.accept(next.get());
            } catch (final RuntimeException e) {
                log.warn("heartbeat processing error for object {}", next.get(), e);
                throw e;
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

    public static void event(final World world,
                             final EventsToProcess.Type eventType,
                             final Consumer<EventsToProcess.Event> handler) {
        final int worldTime = world.getWorldTime();
        final EventsToProcess eventsToProcess = world.eventsToProcess();

        int circuitBreakerCounter = 0;

        while (true) {
            final Optional<EventsToProcess.Event> event = eventsToProcess.findFirstActiveEvent(eventType, worldTime);
            if (event.isEmpty()) {
                break;
            }

            if (circuitBreakerCounter == CIRCUIT_BREAKER_COUNTER_LIMIT) {
                log.warn("too many events to process, the last one is {}", event.get());
                break;
            }
            circuitBreakerCounter++;

            try (final Timing.Timer ignored = Timing.label("Processing.event - " + eventType.name())) {
                handler.accept(event.get());
            }

            event.get().setProcessedStatus();
        }
    }
}
