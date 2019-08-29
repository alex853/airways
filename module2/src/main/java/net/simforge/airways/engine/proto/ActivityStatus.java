/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine.proto;

import java.time.LocalDateTime;

@Deprecated
public class ActivityStatus {
    public boolean isDone() {
        return false;
    }

    public LocalDateTime getLastActTime() {
        return null;
    }
}
