/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processengine;

import java.time.LocalDateTime;

public interface TimeMachine {
    LocalDateTime now();
}
