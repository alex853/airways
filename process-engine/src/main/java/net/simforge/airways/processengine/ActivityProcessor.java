/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processengine;

import net.simforge.airways.processengine.activity.Activity;
import net.simforge.airways.processengine.activity.ActivityInfo;
import net.simforge.airways.processengine.entities.TaskEntity;
import net.simforge.commons.hibernate.BaseEntity;
import net.simforge.commons.misc.Misc;

import java.time.LocalDateTime;

class ActivityProcessor extends Processor {
    ActivityProcessor(TaskEntity task, InjectionContext baseInjectionContext) {
        super(task, baseInjectionContext);
    }

    @Override
    ProcessingResult process() {
        Activity activity = (Activity) create(task.getProcessorClassName());

        ActivityInfo activityInfo = new ActivityInfo(task);

        Class entityClass = clazz(task.getEntityClassName());

        //noinspection unchecked
        BaseEntity entity = (BaseEntity) session.get(entityClass, task.getEntityId());

        InjectionContext activityInjectionContext = processorInjectionContext
                .add(entityClass, entity)
                .add(ActivityInfo.class, activityInfo);

        activityInjectionContext = addServicesToInjectionContext(activityInjectionContext);

        activityInjectionContext.inject(activity);

        if (!isActivityExpired(activityInfo)) {
            Result result = activity.act();

            if (result.getAction() == Result.Action.Resume) {
                LocalDateTime now = timeMachine.now();
                switch (result.getWhen()) {
                    case NextDay:
                        task.setTaskTime(now.plusDays(1));
                        break;
                    case NextHour:
                        task.setTaskTime(now.plusHours(1));
                        break;
                    case NextMinute:
                        task.setTaskTime(now.plusMinutes(1));
                        break;
                    case FewTimesPerHour:
                        task.setTaskTime(now.plusMinutes(Misc.random(10, 30)));
                        break;
                    case FewTimesPerDay:
                        task.setTaskTime(now.plusHours(Misc.random(3, 8)));
                        break;
                    default:
                        throw new IllegalArgumentException("unable to calculate next run time for " + result.getWhen() + " mode");
                }
                if (task.getExpiryTime() != null) {
                    if (task.getExpiryTime().isBefore(task.getTaskTime())) {
                        task.setTaskTime(task.getExpiryTime());
                    }
                }
            } else if (result.getAction() == Result.Action.Done) {
                task.setTaskTime(null);
                task.setStatus(TaskEntity.Status.DONE);
            } else if (result.getAction() == Result.Action.Sleep) {
                if (task.getExpiryTime() != null) {
                    task.setTaskTime(task.getExpiryTime());
                } else {
                    task.setTaskTime(null);
                }
            } else {
                throw new IllegalArgumentException("don't know what to do when resulted action is " + result.getAction());
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
