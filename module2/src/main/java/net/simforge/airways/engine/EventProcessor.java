/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine;

import net.simforge.airways.engine.entities.TaskEntity;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.commons.hibernate.BaseEntity;
import org.reflections.Reflections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

class EventProcessor extends Processor {
    protected EventProcessor(TaskEntity task, InjectionContext baseInjectionContext) {
        super(task, baseInjectionContext);
    }

    @Override
    ProcessingResult process() {
        Class eventClass = clazz(task.getProcessorClassName()); // todo

        List<Class<? extends Handler>> handlerClasses = getSubscriptions(eventClass);

        Class entityClass = clazz(task.getEntityClassName());

        BaseEntity entity = (BaseEntity) session.get(entityClass, task.getEntityId());

        InjectionContext handlerInjectionContext = processorInjectionContext
                .add(entityClass, entity)
                /*.add(ActivityInfo.class, activityInfo)*/;

        handlerInjectionContext = addServicesToInjectionContext(handlerInjectionContext);

        for (Class<? extends Handler> handlerClass : handlerClasses) {
            Handler handler = (Handler) create(handlerClass);

            handlerInjectionContext.inject(handler);

            handler.process();
        }

        task.setTaskTime(null);
        task.setStatus(TaskEntity.Status.DONE); // todo error processing?

        return null; // todo
    }

    private List<Class<? extends Handler>> getSubscriptions(Class eventClass) {
        List<Class<? extends Handler>> result = new ArrayList<>();

        Collection<Class<? extends Handler>> handlerClasses = scanHandlerClasses();

        for (Class<? extends Handler> handlerClass : handlerClasses) {
            Subscribe subscribe = handlerClass.getAnnotation(Subscribe.class);
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

    private Set<Class<? extends Handler>> scanHandlerClasses() {
        String packageName = "net.simforge.airways.processes";
        Reflections reflections = new Reflections(packageName);
        return reflections.getSubTypesOf(Handler.class);
    }
}
