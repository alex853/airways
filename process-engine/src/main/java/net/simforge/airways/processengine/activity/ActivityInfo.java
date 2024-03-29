/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processengine.activity;

import net.simforge.airways.processengine.entities.TaskEntity;

import java.time.LocalDateTime;

public class ActivityInfo {
    private TaskEntity taskEntity;

    public ActivityInfo(TaskEntity taskEntity) {
        this.taskEntity = taskEntity;
    }

    public LocalDateTime getExpiryTime() {
        return taskEntity.getExpiryTime();
    }

    public boolean isFinished() {
        return taskEntity.getStatus() != TaskEntity.Status.ACTIVE;
    }

    public boolean isDone() {
        return taskEntity.getStatus() == TaskEntity.Status.DONE;
    }

    public boolean isExpired() {
        return taskEntity.getStatus() == TaskEntity.Status.EXPIRED;
    }

    public Integer getTaskId() {
        return taskEntity.getId();
    }
}
