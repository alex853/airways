package net.simforge.airways.processengine;

import net.simforge.commons.misc.JavaTime;

import java.time.LocalDateTime;

public class RealTimeMachine implements TimeMachine {
    @Override
    public LocalDateTime now() {
        return JavaTime.nowUtc();
    }

    @Override
    public void nothingToProcess() {
        // noop in case of real-time machine
    }
}
