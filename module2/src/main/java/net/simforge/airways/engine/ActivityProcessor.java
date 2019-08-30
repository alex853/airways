/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine;

import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.engine.activity.ActivityInfo;
import net.simforge.airways.engine.entities.TaskEntity;
import net.simforge.commons.hibernate.BaseEntity;

import java.time.LocalDateTime;

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

        if (!isActivityExpired(activityInfo)) {
            Result result = activity.act();

            LocalDateTime now = timeMachine.now();
            switch (result.getNextRun()) {
                case NextDay:
                    task.setTaskTime(now.plusDays(1));
                    break;
                case NextHour:
                    task.setTaskTime(now.plusHours(1));
                    break;
                case NextMinute:
                    task.setTaskTime(now.plusMinutes(1));
                    break;
                case DoNotRun:
                    if (task.getExpiryTime() != null) {
                        task.setTaskTime(task.getExpiryTime());
                    } else {
                        task.setTaskTime(null);
                        task.setStatus(TaskEntity.Status.DONE);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("unable to calculate next run time for " + result.getNextRun() + " mode");
            }


            // todo circuit breaker
            // todo stats
        } else {
            activity.onExpiry(); // result is ignored

            task.setTaskTime(null);
            task.setStatus(TaskEntity.Status.EXPIRED);
            // todo stats
        }

        return null; // todo
    }

    private boolean isActivityExpired(ActivityInfo activityInfo) {
        LocalDateTime expiryTime = activityInfo.getExpiryTime();
        if (expiryTime == null) {
            return false;
        }

        LocalDateTime now = timeMachine.now();
        return now.equals(expiryTime) || now.isAfter(expiryTime);
    }
}
