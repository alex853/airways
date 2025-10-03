package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.EventsToProcess;

import java.util.Optional;
import java.util.function.Consumer;

public class EventProcessing {
    public static void process(final World world, final EventsToProcess.Type eventType, final Consumer<EventsToProcess.Event> handler) {
        final int worldTime = world.getWorldTime();
        final EventsToProcess eventsToProcess = world.eventsToProcess();

        while (true) {
            final Optional<EventsToProcess.Event> event = eventsToProcess.findFirstActiveEvent(eventType, worldTime);
            if (event.isEmpty()) {
                break;
            }

            handler.accept(event.get());

            event.get().setProcessedStatus();
        }
    }
}
