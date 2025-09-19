package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.EventLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class EventLogCleanup {
    private static final Logger log = LoggerFactory.getLogger(EventLogCleanup.class);

    public static void process(final World world) {
        final int worldTime = world.getWorldTime();

        final Collection<EventLog.Event> outdated = world.eventLog()
                .filter(e -> e.getTime() <= worldTime - 7 * Time.ONE_DAY);
        outdated.forEach(f -> world.eventLog().deleteById(f.getId()));

        if (outdated.size() > 0) {
            log.info("event log cleaned up - {} removed", outdated.size());
        }
    }
}
