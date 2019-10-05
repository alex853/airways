/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class SimulatedTimeMachine implements TimeMachine {
    private static Logger logger = LoggerFactory.getLogger(SimulatedTimeMachine.class);

    private LocalDateTime time;

    public SimulatedTimeMachine(LocalDateTime startTime) {
        this.time = startTime;
    }

    @Override
    public LocalDateTime now() {
        return time;
    }

    public void plusMinutes(long minutes) {
        time = time.plusMinutes(minutes);

        logger.info("Current time is " + time);
    }
}
