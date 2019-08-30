/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine;

import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.engine.activity.ActivityInfo;
import net.simforge.airways.engine.entities.TaskEntity;
import net.simforge.airways.engine.event.Event;
import net.simforge.airways.util.TimeMachine;
import net.simforge.commons.hibernate.BaseEntity;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.misc.Misc;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
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

        TaskEntity task = taskQueue.peek();

        if (task == null) {
            return;
        }

        if (task.getTaskTime().isAfter(timeMachine.now())) {
            return;
        } else {
            taskQueue.remove(task);
        }

        // todo check circuit breaker

        try (Session session = sessionFactory.openSession()) {
            TaskEntity _task = session.get(TaskEntity.class, task.getId());
            if (!_task.getVersion().equals(task.getVersion())) {
                logger.warn("Task {} - Version is outdated, skipped", _task.getId());
                return;
            }
            if (_task.getStatus() != TaskEntity.Status.ACTIVE) {
                logger.warn("Task {} - Status is {}, it is not ACTIVE, skipped", _task.getId(), _task.getStatus());
                _task.setTaskTime(null);
                HibernateUtils.updateAndCommit(session, _task);
                return;
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
        LocalDateTime now = timeMachine.now();
        int maxResults = 1000;
        try (Session session = sessionFactory.openSession()) {
            //noinspection unchecked
            tasks = session
                    .createQuery("from EngineTask where taskTime <= :toTime order by taskTime")
                    .setParameter("toTime", now.plusMinutes(1))
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
        startActivity(activityClass, entity, null);
    }

    public void startActivity(Class<? extends Activity> activityClass, BaseEntity entity, LocalDateTime expiryTime) {
        TaskEntity task = new TaskEntity();
        task.setStatus(TaskEntity.Status.ACTIVE);
        task.setRetryCount(0);
        task.setTaskTime(timeMachine.now());
        task.setProcessorClassName(activityClass.getName());
        task.setEntityClassName(entity.getClass().getName());
        task.setEntityId(entity.getId());
        task.setExpiryTime(expiryTime);

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, task);
        }
    }

    public void scheduleActivity(Class<? extends Activity> activityClass, BaseEntity entity, LocalDateTime startTime) {
        scheduleActivity(activityClass, entity, startTime, null);
    }

    public void scheduleActivity(Class<? extends Activity> activityClass, BaseEntity entity, LocalDateTime startTime, LocalDateTime expiryTime) {
        TaskEntity task = new TaskEntity();
        task.setStatus(TaskEntity.Status.ACTIVE);
        task.setRetryCount(0);
        task.setTaskTime(startTime);
        task.setProcessorClassName(activityClass.getName());
        task.setEntityClassName(entity.getClass().getName());
        task.setEntityId(entity.getId());
        task.setExpiryTime(expiryTime);

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, task);
        }
    }

    public void fireEvent(Class eventClass, BaseEntity entity) {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {
                fireEvent(session, eventClass, entity);
            });
        }
    }

    public void fireEvent(Session session, Class eventClass, BaseEntity entity) {
        TaskEntity task = new TaskEntity();
        task.setStatus(TaskEntity.Status.ACTIVE);
        task.setRetryCount(0);
        task.setTaskTime(timeMachine.now());
        task.setProcessorClassName(eventClass.getName());
        task.setEntityClassName(entity.getClass().getName());
        task.setEntityId(entity.getId());

        session.save(task);
    }

    public void scheduleEvent(Class<? extends Event> eventClass, BaseEntity entity, LocalDateTime eventTime) {
        TaskEntity task = new TaskEntity();
        task.setStatus(TaskEntity.Status.ACTIVE);
        task.setRetryCount(0);
        task.setTaskTime(eventTime);
        task.setProcessorClassName(eventClass.getName());
        task.setEntityClassName(entity.getClass().getName());
        task.setEntityId(entity.getId());

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, task);
        }
    }

    public ActivityInfo findActivity(Class<? extends Activity> activityClass, BaseEntity entity) {
        try (Session session = sessionFactory.openSession()) {
            TaskEntity _task = (TaskEntity) session
                    .createQuery("from EngineTask " +
                            "where processorClassName = :activityClass " +
                            "and entityClassName = :entityClass " +
                            "and entityId = :entityId " +
                            "order by status asc, id desc")
                    .setString("activityClass", activityClass.getName())
                    .setString("entityClass", entity.getClass().getName())
                    .setInteger("entityId", entity.getId())
                    .setMaxResults(1) // we are looking for one row only
                    .uniqueResult();
            if (_task == null) {
                return null;
            } else {
                return new ActivityInfo(_task);
            }
        }
    }

    public void stopActivity(ActivityInfo activityInfo) {
        try (Session session = sessionFactory.openSession()) {
            TaskEntity _task = session.load(TaskEntity.class, activityInfo.getTaskId());
            _task.setTaskTime(null);
            _task.setStatus(TaskEntity.Status.STOPPED);
            HibernateUtils.updateAndCommit(session, _task);
        }

        // todo queue lock
        taskQueue.removeIf(task -> task.getId().equals(activityInfo.getTaskId()));
    }
}
