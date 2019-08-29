/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

package net.simforge.airways.engine;

import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.engine.activity.ActivityInfo;
import net.simforge.airways.engine.entities.TaskEntity;
import net.simforge.airways.service.TimetableService;
import net.simforge.commons.hibernate.BaseEntity;

class ActivityProcessor extends Processor {
    protected ActivityProcessor(TaskEntity task, InjectionContext baseInjectionContext) {
        super(task, baseInjectionContext);
    }

    @Override
    ProcessingResult process() {
        Activity activity = createActivity();

        ActivityInfo activityInfo = null; // todo

        Class entityClass = getEntityClass();

        BaseEntity entity = (BaseEntity) session.get(entityClass, task.getEntityId());

        InjectionContext activityInjectionContext = processorInjectionContext
                .add(entityClass, entity)
                .add(ActivityInfo.class, activityInfo);

        TimetableService timetableService = new TimetableService();
        activityInjectionContext.inject(timetableService);
        activityInjectionContext = activityInjectionContext
                .add(TimetableService.class, timetableService);

        activityInjectionContext.inject(activity);

        if (true || activityInfo.expireTime() > timeMachine.getTimeMillis()) { // todo remove 'true ||'
            Result result = activity.act();

            task.setTaskTime(nextRunToTime(result.getNextRun()));
            // todo circuit breaker
            // todo stats
        } else {
            activity.afterExpired(); // result is ignored

            task.setTaskTime(null);
            // todo stats
        }

        return null; // todo
    }

    private Activity createActivity() {
        try {
            //noinspection unchecked
            Class<Activity> processorClass = (Class<Activity>) Class.forName(task.getProcessorClassName());
            return processorClass.newInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find processor class", e);
        } catch (InstantiationException e) {
            throw new RuntimeException("Could not instantiate processor class", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Could not access processor class", e);
        }
    }
}
