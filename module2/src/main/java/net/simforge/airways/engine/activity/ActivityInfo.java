/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine.activity;

import net.simforge.airways.engine.entities.TaskEntity;

import java.time.LocalDateTime;

public class ActivityInfo {
    private TaskEntity taskEntity;

    public ActivityInfo(TaskEntity taskEntity) {
        this.taskEntity = taskEntity;
    }

    public LocalDateTime getExpireTime() {
        // todo p2 expiration support
        return null;
    }

    public boolean isFinished() {
        return taskEntity.getStatus() != TaskEntity.Status.ACTIVE;
    }

    public Integer getTaskId() {
        return taskEntity.getId();
    }
}
