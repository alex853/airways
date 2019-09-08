/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public interface TimeMachine {
    long getTimeMillis();

    LocalDate today();

    default LocalDateTime now() {
        return LocalDateTime.ofEpochSecond(getTimeMillis() / 1000, 0, ZoneOffset.UTC);
    }
}
