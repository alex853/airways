/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processengine;

import net.simforge.airways.processengine.entities.TaskEntity;

import java.util.LinkedList;
import java.util.ListIterator;

class TaskQueue {
    private LinkedList<TaskEntity> tasks = new LinkedList<>();

    public TaskEntity poll() {
        return tasks.pollFirst();
    }

    public void push(TaskEntity task) {
        if (task == null) {
            throw new IllegalArgumentException();
        }

        ListIterator<TaskEntity> it = tasks.listIterator();
        while (it.hasNext()) {
            TaskEntity next = it.next();
            if (next.getTaskTime().isAfter(task.getTaskTime())) {
                it.previous();
                it.add(task);
                return;
            }
        }

        tasks.add(task);
    }
}
