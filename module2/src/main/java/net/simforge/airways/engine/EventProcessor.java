/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine;

import net.simforge.airways.engine.entities.TaskEntity;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.processes.flight.handler.OnAllocated;
import net.simforge.airways.processes.flight.handler.OnCancelled;
import net.simforge.airways.processes.flight.handler.OnPlanned;
import net.simforge.airways.processes.transportflight.handler.OnCheckinClosed;
import net.simforge.airways.processes.transportflight.handler.OnCheckinOpens;
import net.simforge.airways.processes.transportflight.handler.OnScheduled;
import net.simforge.commons.hibernate.BaseEntity;

import java.util.ArrayList;
import java.util.List;

class EventProcessor extends Processor {
    protected EventProcessor(TaskEntity task, InjectionContext baseInjectionContext) {
        super(task, baseInjectionContext);
    }

    @Override
    ProcessingResult process() {
        Class eventClass = clazz(task.getProcessorClassName()); // todo

        List<Class<Handler>> handlerClasses = getSubscriptions(eventClass);

        Class entityClass = clazz(task.getEntityClassName());

        BaseEntity entity = (BaseEntity) session.get(entityClass, task.getEntityId());

        InjectionContext handlerInjectionContext = processorInjectionContext
                .add(entityClass, entity)
                /*.add(ActivityInfo.class, activityInfo)*/;

        handlerInjectionContext = addServicesToInjectionContext(handlerInjectionContext);

        for (Class<Handler> handlerClass : handlerClasses) {
            Handler handler = (Handler) create(handlerClass);

            handlerInjectionContext.inject(handler);

            handler.process();
        }

        task.setTaskTime(null);
        task.setStatus(TaskEntity.Status.DONE); // todo error processing?

        return null; // todo
    }

    private List<Class<Handler>> getSubscriptions(Class eventClass) {
        // todo rework it!!

        List<Class<Handler>> result = new ArrayList<>();
        Class[] handlerClasses = {OnScheduled.class, OnCheckinOpens.class, OnCheckinClosed.class, OnAllocated.class, OnCancelled.class, OnPlanned.class};
        for (Class handlerClass : handlerClasses) {
            Subscribe subscribe = (Subscribe) handlerClass.getAnnotation(Subscribe.class);
            if (subscribe == null) {
                continue;
            }
            if (!eventClass.equals(subscribe.value())) {
                continue;
            }
            result.add(handlerClass);
        }

        return result;
    }
}
