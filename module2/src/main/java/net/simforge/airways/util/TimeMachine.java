package net.simforge.airways.util;

import java.time.LocalDate;

public interface TimeMachine {
    long getTimeMillis();

    LocalDate today();
}
