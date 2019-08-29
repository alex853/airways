/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine.activity;

import java.time.LocalDateTime;

// todo ActivityInfo vs ActivityStatus???
@Deprecated
public class ActivityStatus {
    public boolean isDone() {
        return false;
    }

    public LocalDateTime getLastActTime() {
        return null;
    }
}
