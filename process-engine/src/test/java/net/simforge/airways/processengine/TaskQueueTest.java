/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processengine;

import net.simforge.airways.processengine.entities.TaskEntity;
import org.junit.Ignore;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

public class TaskQueueTest {
    @Test
    public void pushToEmpty() {
        TaskQueue q = new TaskQueue();

        q.push(newTask(0));

        assertEquals(0, q.poll().getTaskTime().getMinute());
    }

    @Test
    public void pushInDirectOrder() {
        TaskQueue q = new TaskQueue();

        q.push(newTask(0));
        q.push(newTask(1));
        q.push(newTask(2));
        q.push(newTask(3));

        assertEquals(0, q.poll().getTaskTime().getMinute());
        assertEquals(1, q.poll().getTaskTime().getMinute());
        assertEquals(2, q.poll().getTaskTime().getMinute());
        assertEquals(3, q.poll().getTaskTime().getMinute());
    }

    @Test
    public void pushInRandomOrder() {
        TaskQueue q = new TaskQueue();

        q.push(newTask(0));
        q.push(newTask(10));
        q.push(newTask(4));
        q.push(newTask(12));

        assertEquals(0, q.poll().getTaskTime().getMinute());
        assertEquals(4, q.poll().getTaskTime().getMinute());
        assertEquals(10, q.poll().getTaskTime().getMinute());
        assertEquals(12, q.poll().getTaskTime().getMinute());
    }

    @Test
    @Ignore
    // simple implementation does not solve all issues - changes of task data and reordering according to the changes
    // because of that this simple implementation was shelved and trivial database ordering was introduced
    public void pushExistingTask() {
        TaskQueue q = new TaskQueue();

        q.push(newTask(0));

        TaskEntity taskOfInterest = newTask(10);
        q.push(taskOfInterest);

        q.push(newTask(20));

        q.push(taskOfInterest);

        assertEquals(0, q.poll().getTaskTime().getMinute());
        assertEquals(10, q.poll().getTaskTime().getMinute());
        assertEquals(20, q.poll().getTaskTime().getMinute());
    }

    private int taskIdCounter = 0;

    private TaskEntity newTask(int time) {
        TaskEntity task = new TaskEntity();
        task.setId(taskIdCounter++);
        task.setTaskTime(LocalDateTime.of(2018, 1, 1, 0, time));
        return task;
    }
}