/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

package net.simforge.airways.engine;

import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.engine.entities.TaskEntity;
import net.simforge.airways.engine.event.Event;
import net.simforge.airways.util.TimeMachine;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

abstract class Processor {
    private static Logger logger = LoggerFactory.getLogger(Processor.class);

    final TaskEntity task;
    final InjectionContext processorInjectionContext;

    @Inject
    protected Engine engine;
    @Inject
    Session session;
    @Inject
    TimeMachine timeMachine;

    Processor(TaskEntity event, InjectionContext processorInjectionContext) {
        this.task = event;
        this.processorInjectionContext = processorInjectionContext;
    }

    abstract ProcessingResult process();

    Class getEntityClass() {
        try {
            return Class.forName(task.getEntityClassName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find processor class", e);
        }
    }

    static Processor create(TaskEntity task, InjectionContext baseInjectionContext) {
        Class<?> processorClass;
        try {
            processorClass = Class.forName(task.getProcessorClassName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find processor class", e);
        }

        if (Activity.class.isAssignableFrom(processorClass)) {
            return new ActivityProcessor(task, baseInjectionContext);
        } else if (Event.class.isAssignableFrom(processorClass)) {
            return new EventProcessor(task, baseInjectionContext);
        } else {
            String msg = String.format("Unable to create processor for task %s, class name %s", task.getId(), task.getProcessorClassName());
            logger.error(msg);
            throw new IllegalArgumentException(msg);
        }
    }

    long nextRunToTime(Result.NextRun nextRun) {
        long now = timeMachine.getTimeMillis();
        switch (nextRun) {
            case NextDay:
                return now + 24 * 60 * 60 * 1000;
            case NextHour:
                return now + 60 * 60 * 1000;
            default:
                throw new IllegalArgumentException("unable to calculate next run time for " + nextRun + " mode");
        }
    }
}
