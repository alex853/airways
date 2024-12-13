package net.simforge.airways.processengine;

import java.time.LocalDateTime;

public interface TimeMachine {
    LocalDateTime now();
    void nothingToProcess();
}
