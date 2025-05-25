package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class ScheduledFlightMissionGenerator {
    private static final Logger log = LoggerFactory.getLogger(ScheduledFlightMissionGenerator.class);
    private static long lastExecution;

    public static void process(final World world, final int worldTime) {
        if (LocalDateTime.now().getMinute() != 0) {
            return;
        }
        if (System.currentTimeMillis() - lastExecution < 3600000) {
            return;
        }
        lastExecution = System.currentTimeMillis();

        // todo ak0 aw/auw
    }
}
