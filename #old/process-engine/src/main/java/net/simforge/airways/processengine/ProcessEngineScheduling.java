package net.simforge.airways.processengine;

import net.simforge.airways.processengine.activity.Activity;
import net.simforge.airways.processengine.activity.ActivityInfo;
import net.simforge.airways.processengine.entities.TaskEntity;
import net.simforge.airways.processengine.event.Event;
import net.simforge.commons.hibernate.BaseEntity;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.LocalDateTime;

public class ProcessEngineScheduling {

    private final SessionFactory sessionFactory;
    private final TimeMachine timeMachine;

    public ProcessEngineScheduling(SessionFactory sessionFactory, TimeMachine timeMachine) {
        this.sessionFactory = sessionFactory;
        this.timeMachine = timeMachine;
    }

    public void startActivity(Class<? extends Activity> activityClass, BaseEntity entity) {
        startActivity(activityClass, entity, null);
    }

    public void startActivity(Class<? extends Activity> activityClass, BaseEntity entity, LocalDateTime expiryTime) {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {
                startActivity(session, activityClass, entity, expiryTime);
            });
        }
    }

    public void startActivity(Session session, Class<? extends Activity> activityClass, BaseEntity entity) {
        startActivity(session, activityClass, entity, null);
    }

    public void startActivity(Session session, Class<? extends Activity> activityClass, BaseEntity entity, LocalDateTime expiryTime) {
        TaskEntity task = new TaskEntity();
        task.setStatus(TaskEntity.Status.ACTIVE);
        task.setRetryCount(0);
        task.setTaskTime(timeMachine.now());
        task.setProcessorClassName(activityClass.getName());
        task.setEntityClassName(Hibernate.getClass(entity).getName());
        task.setEntityId(entity.getId());
        task.setExpiryTime(expiryTime);

        session.save(task);
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
        task.setEntityClassName(Hibernate.getClass(entity).getName());
        task.setEntityId(entity.getId());
        task.setExpiryTime(expiryTime);

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, task);
        }
    }

    public void fireEvent(Class<? extends Event> eventClass, BaseEntity entity) {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {
                fireEvent(session, eventClass, entity);
            });
        }
    }

    public void fireEvent(Session session, Class<? extends Event> eventClass, BaseEntity entity) {
        TaskEntity task = new TaskEntity();
        task.setStatus(TaskEntity.Status.ACTIVE);
        task.setRetryCount(0);
        task.setTaskTime(timeMachine.now());
        task.setProcessorClassName(eventClass.getName());
        task.setEntityClassName(Hibernate.getClass(entity).getName());
        task.setEntityId(entity.getId());

        session.save(task);
    }

    public void scheduleEvent(Class<? extends Event> eventClass, BaseEntity entity, LocalDateTime eventTime) {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {
                scheduleEvent(session, eventClass, entity, eventTime);
            });
        }
    }

    public void scheduleEvent(Session session, Class<? extends Event> eventClass, BaseEntity entity, LocalDateTime eventTime) {
        TaskEntity task = new TaskEntity();
        task.setStatus(TaskEntity.Status.ACTIVE);
        task.setRetryCount(0);
        task.setTaskTime(eventTime);
        task.setProcessorClassName(eventClass.getName());
        task.setEntityClassName(Hibernate.getClass(entity).getName());
        task.setEntityId(entity.getId());

        session.save(task);
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
                    .setString("entityClass", Hibernate.getClass(entity).getName())
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
}
