package net.simforge.airways.processengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class SimulatedTimeMachine implements TimeMachine {
    private static final Logger log = LoggerFactory.getLogger(SimulatedTimeMachine.class);

    private LocalDateTime time;

    public SimulatedTimeMachine(LocalDateTime startTime) {
        this.time = startTime;
    }

    @Override
    public LocalDateTime now() {
        return time;
    }

    @Override
    public void nothingToProcess() {
        // noop todo plusMinutes can be reworked as nothingToProcess!
    }

    public void plusMinutes(long minutes) {
        time = time.plusMinutes(minutes);

        log.info("Current time is " + time);
    }
}
