/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

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
import net.simforge.airways.engine.entities.TaskEntity;
import net.simforge.airways.engine.proto.ActivityStatus;
import net.simforge.airways.util.TimeMachine;
import net.simforge.commons.hibernate.BaseEntity;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.misc.Misc;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class Engine implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(Engine.class);

    private Queue<TaskEntity> taskQueue = new ArrayDeque<>(); // very trivial way of queue management based on database

    private InjectionContext baseInjectionContext;
    TimeMachine timeMachine;
    SessionFactory sessionFactory;

    Engine() {
    }

    @Override
    public void run() {
        while (!isStopRequested()) {

            tick();

            Misc.sleepBM(100);
        }
    }

    private boolean isStopRequested() {
        throw new UnsupportedOperationException("Engine.isStopRequested");
    }

    public void tick() {
        buildInjectionContext();

        invalidateQueue();

        TaskEntity task = taskQueue.poll();

        if (task == null) {
            return;
        }

        // todo check circuit breaker

        try (Session session = sessionFactory.openSession()) {
            TaskEntity _task = session.get(TaskEntity.class, task.getId());
            if (!_task.getVersion().equals(task.getVersion())) {
                throw new UnsupportedOperationException("think about it"); // todo think about it
            }

            InjectionContext processorInjectionContext = baseInjectionContext
                    .add(Session.class, session);
            Processor processor = Processor.create(_task, processorInjectionContext);
            processorInjectionContext.inject(processor);

            HibernateUtils.transaction(session, () -> {
                processor.process();

                session.update(_task);
            });

        } catch (Throwable t) {
            logger.error("Error on processing: {}, message '{}', see details in stacktrace log", t.getClass(), t.getMessage());
            logger.error("Error details", t); // todo another logger dedicated for stacktraces

            // todo to do emergency update of the task
        }
    }

    private void invalidateQueue() {
        List<TaskEntity> tasks;
        long currTime = timeMachine.getTimeMillis();
        int maxResults = 1000;
        try (Session session = sessionFactory.openSession()) {
            //noinspection unchecked,JpaQlInspection
            tasks = session
                    .createQuery("from EngineTask where taskTime <= :toTime order by taskTime")
                    .setLong("toTime", currTime + 60000)
                    .setMaxResults(maxResults)
                    .list();
        }

        taskQueue = new ArrayDeque<>(tasks); // very trivial way of queue management based on database query with sorting
    }

    private void buildInjectionContext() {
        if (baseInjectionContext != null) {
            return;
        }

        baseInjectionContext = InjectionContext.create()
                .add(Engine.class, this)
                .add(TimeMachine.class, timeMachine)
                .add(SessionFactory.class, sessionFactory);
    }

    private void reschedule(TaskEntity task) {
        throw new UnsupportedOperationException("EngineRuntime.reschedule");
    }

    public void startActivity(Class<? extends Activity> activityClass, BaseEntity entity) {
        TaskEntity task = new TaskEntity();
        task.setStatus(TaskEntity.Status.ACTIVE);
        task.setRetryCount(0);
        task.setTaskTime(timeMachine.getTimeMillis());
        task.setProcessorClassName(activityClass.getName());
        task.setEntityClassName(entity.getClass().getName());
        task.setEntityId(entity.getId());

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, task);
        }
    }

    public ActivityStatus getActivityStatus(Class<? extends Activity> activityClass, BaseEntity entity) {
        throw new UnsupportedOperationException("Engine.getActivityStatus");
    }

    public void fireEvent(Session session, Class eventClass, BaseEntity entity) {
        TaskEntity task = new TaskEntity();
        task.setStatus(TaskEntity.Status.ACTIVE);
        task.setRetryCount(0);
        task.setProcessorClassName(eventClass.getName());
        task.setEntityClassName(entity.getClass().getName());
        task.setEntityId(entity.getId());
        task.setTaskTime(timeMachine.getTimeMillis());

        session.save(task);
    }
}
