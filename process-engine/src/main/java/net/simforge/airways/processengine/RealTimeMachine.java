/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processengine;

import net.simforge.commons.misc.JavaTime;

import java.time.LocalDateTime;

public class RealTimeMachine implements TimeMachine {
    @Override
    public LocalDateTime now() {
        return JavaTime.nowUtc();
    }
}
