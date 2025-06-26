package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class FlightMissionCleanup {
    private static final Logger log = LoggerFactory.getLogger(FlightMissionCleanup.class);

    public static void process(final World world) {
        final int worldTime = world.getWorldTime();

        final Collection<FlightMissions.Mission> cancelled = world.flightMissions()
                .filter(f -> f.getStatus() == FlightMissions.Status.Cancelled
                        && f.getPlannedDepartureWorldTime() <= worldTime - 1 * Time.ONE_DAY);
        cancelled.forEach(f -> world.flightMissions().deleteById(f.getId()));

        final Collection<FlightMissions.Mission> finished = world.flightMissions()
                .filter(f -> f.getStatus() == FlightMissions.Status.Finished
                        && f.getActualArrivalWorldTime() <= worldTime - 10 * Time.ONE_DAY);
        finished.forEach(f -> world.flightMissions().deleteById(f.getId()));

        if (cancelled.size() > 0 || finished.size() > 0) {
            log.info("flight missions cleaned up - {} cancelled, {} finished", cancelled.size(), finished.size());
        }
    }
}
