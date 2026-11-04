package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Journeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Once an hour (by the real time, the first time at the start) writes to the log how many journeys are in each status: the same numbers as the top of the page tech/journeys
 * (GET /admin/journey/stats), the passengers (the sizes of the groups) by the statuses, and the journeys themselves. The line starts with "j/y - stats by statuses", so it is easy
 * to find in the log and to compare with the hours before.
 */
public class JourneyStats {
    private static final Logger log = LoggerFactory.getLogger(JourneyStats.class);
    private static final long PERIOD_MILLIS = 3600000;

    private static long lastExecution;

    public static void process(final World world) {
        if (System.currentTimeMillis() - lastExecution < PERIOD_MILLIS) {
            return;
        }
        lastExecution = System.currentTimeMillis();

        logStats(world);
    }

    static void logStats(final World world) {
        final Journeys.Status[] statuses = Journeys.Status.values();
        final long[] passengers = new long[statuses.length];
        final long[] journeys = new long[statuses.length];
        world.journeys().all().forEach(journey -> {
            final int index = journey.getStatus().ordinal();
            passengers[index] += journey.getGroupSize();
            journeys[index]++;
        });

        log.info("j/y - stats by statuses: passengers total {}: {}; journeys total {}: {}",
                IntStream.range(0, statuses.length).mapToLong(i -> passengers[i]).sum(), byStatuses(statuses, passengers),
                IntStream.range(0, statuses.length).mapToLong(i -> journeys[i]).sum(), byStatuses(statuses, journeys));
    }

    private static String byStatuses(final Journeys.Status[] statuses, final long[] values) {
        return IntStream.range(0, statuses.length)
                .mapToObj(i -> statuses[i].name() + "=" + values[i])
                .collect(Collectors.joining(", "));
    }
}
