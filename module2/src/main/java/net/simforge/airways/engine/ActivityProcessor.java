/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine;

import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.engine.activity.ActivityInfo;
import net.simforge.airways.engine.entities.TaskEntity;
import net.simforge.commons.hibernate.BaseEntity;

class ActivityProcessor extends Processor {
    protected ActivityProcessor(TaskEntity task, InjectionContext baseInjectionContext) {
        super(task, baseInjectionContext);
    }

    @Override
    ProcessingResult process() {
        Activity activity = (Activity) create(task.getProcessorClassName());

        ActivityInfo activityInfo = new ActivityInfo(task);

        Class entityClass = clazz(task.getEntityClassName());

        BaseEntity entity = (BaseEntity) session.get(entityClass, task.getEntityId());

        InjectionContext activityInjectionContext = processorInjectionContext
                .add(entityClass, entity)
                .add(ActivityInfo.class, activityInfo);

        activityInjectionContext = addServicesToInjectionContext(activityInjectionContext);

        activityInjectionContext.inject(activity);

        if (activityInfo.getExpireTime() == null
                || activityInfo.getExpireTime().isAfter(timeMachine.now())) {
            Result result = activity.act();

            task.setTaskTime(nextRunToTime(result.getNextRun()));

            // todo circuit breaker
            // todo stats
        } else {
            activity.afterExpired(); // result is ignored

            task.setTaskTime(null);
            task.setStatus(TaskEntity.Status.EXPIRED);
            // todo stats
        }

        return null; // todo
    }

}
