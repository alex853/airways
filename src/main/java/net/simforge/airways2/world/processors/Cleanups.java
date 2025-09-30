package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.EventLog;
import net.simforge.airways2.world.datamodel.EventsToProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static net.simforge.airways2.world.datamodel.EventsToProcess.Status.Processed;

public class Cleanups {
    private static final Logger log = LoggerFactory.getLogger(Cleanups.class);

    public static void process(final World world) {
        cleanupEventLog(world);
        cleanupEventsToProcess(world);
    }

    private static void cleanupEventLog(final World world) {
        final EventLog storage = world.eventLog();
        final Collection<EventLog.Event> outdated = storage.filter(e -> e.getTime() <= world.getWorldTime() - 7 * Time.ONE_DAY);
        outdated.forEach(f -> storage.deleteById(f.getId()));
        if (outdated.size() > 0) {
            log.info("event log cleaned up - {} removed", outdated.size());
            storage.printDeletedRecordInfo();
        }
    }

    private static void cleanupEventsToProcess(final World world) {
        final EventsToProcess storage = world.eventsToProcess();
        final Collection<EventsToProcess.Event> outdated = storage.filter(e -> (e.getTime() <= world.getWorldTime() - 7 * Time.ONE_DAY) && e.getStatus() == Processed);
        outdated.forEach(f -> storage.deleteById(f.getId()));
        if (outdated.size() > 0) {
            log.info("events-to-process cleaned up - {} removed", outdated.size());
            storage.printDeletedRecordInfo();
        }
    }
}
