package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlightMissionCleanup {
    private static final Logger log = LoggerFactory.getLogger(FlightMissionCleanup.class);

    public static void process(final World world) {
        final int worldTime = world.getWorldTime();
        final int cancelledCleanedUp = (int) world.flightMissions()
                .filter(f -> f.getStatus() == FlightMissions.Status.Cancelled
                        && f.getPlannedDepartureWorldTime() <= worldTime - 2 * Time.ONE_DAY).stream()
                .peek(f -> world.flightMissions().deleteById(f.getId()))
                .count();
        final int finishedCleanedUp = 0;
        if (cancelledCleanedUp > 0) {
            log.info("flight missions cleaned up - {} cancelled, {} finished", cancelledCleanedUp, finishedCleanedUp);
        }
    }
}
