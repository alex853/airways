/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

package net.simforge.airways;

import net.simforge.airways.util.TimeMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class SimulatedTimeMachine implements TimeMachine {
    private static Logger logger = LoggerFactory.getLogger(SimulatedTimeMachine.class);

    private LocalDateTime time;

    public SimulatedTimeMachine(LocalDateTime startTime) {
        this.time = startTime;
    }

    @Override
    public long getTimeMillis() {
        return time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Override
    public LocalDate today() {
        return time.toLocalDate();
    }

    public void plusMinutes(long minutes) {
        time = time.plusMinutes(minutes);

        logger.info("Current time is " + time);
    }
}
