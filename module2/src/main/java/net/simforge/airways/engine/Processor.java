/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

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
import java.time.LocalDateTime;

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

    LocalDateTime nextRunToTime(Result.NextRun nextRun) {
        LocalDateTime now = timeMachine.now();
        switch (nextRun) {
            case NextDay:
                return now.plusDays(1);
            case NextHour:
                return now.plusHours(1);
            default:
                throw new IllegalArgumentException("unable to calculate next run time for " + nextRun + " mode");
        }
    }

    Class clazz(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find class by name", e);
        }
    }

    protected Object create(String className) {
        try {
            Class clazz = Class.forName(className);
            return create(clazz);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not instantiate class", e);
        }
    }

    protected Object create(Class clazz) {
        try {
            //noinspection unchecked
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Could not instantiate class", e);
        }
    }
}
