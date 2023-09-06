package net.simforge.airways.processengine;

import net.simforge.airways.processengine.activity.ActivityInfo;
import net.simforge.airways.processengine.entities.TaskEntity;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Misc;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class ProcessEngine implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ProcessEngine.class);

    private Queue<TaskEntity> taskQueue = new ArrayDeque<>(); // very trivial way of queue management based on database

    private InjectionContext baseInjectionContext;
    ProcessEngineScheduling scheduling;
    TimeMachine timeMachine;
    SessionFactory sessionFactory;

    @SuppressWarnings({"unused", "FieldCanBeLocal", "FieldMayBeFinal"})
    private volatile boolean stopRequested = false;

    ProcessEngine() {
    }

    @Override
    public void run() {
        while (!isStopRequested()) {

            tick();

            Misc.sleepBM(10);

            BM.logPeriodically(true);
        }
    }

    private boolean isStopRequested() {
        return stopRequested;
    }

    public void tick() {
        buildInjectionContext();

        invalidateQueue();

        TaskEntity task = taskQueue.peek();

        if (task == null) {
            timeMachine.nothingToProcess();
            return;
        }

        if (task.getTaskTime().isAfter(timeMachine.now())) {
            timeMachine.nothingToProcess();
            return;
        } else {
            taskQueue.remove(task);
        }

        // todo check circuit breaker

        try (Session session = sessionFactory.openSession()) {
            TaskEntity _task = session.get(TaskEntity.class, task.getId());
            log.debug("Task {} - Processing for '{}' ID {} - processor '{}'", task.getId(), task.getEntityClassName(), task.getEntityId(), task.getProcessorClassName());
            if (!_task.getVersion().equals(task.getVersion())) {
                log.warn("Task {} - Version is outdated, skipped", _task.getId());
                return;
            }
            if (_task.getStatus() != TaskEntity.Status.ACTIVE) {
                log.warn("Task {} - Status is {}, it is not ACTIVE, skipped", _task.getId(), _task.getStatus());
                _task.setTaskTime(null);
                HibernateUtils.updateAndCommit(session, _task);
                return;
            }

            InjectionContext processorInjectionContext = baseInjectionContext
                    .add(Session.class, session);
            Processor processor = Processor.create(_task, processorInjectionContext);
            processorInjectionContext.inject(processor);

            HibernateUtils.transaction(session, "ProcessEngine.tick#process", () -> {
                processor.process();

                session.update(_task);
            });

        } catch (Throwable t) {
            log.error("Error on processing: {}, message '{}', see details in stacktrace log", t.getClass(), t.getMessage());
            log.error("Error details", t); // todo another logger dedicated for stacktraces

            // todo to do emergency update of the task
        }
    }

    private void invalidateQueue() {
        if (!taskQueue.isEmpty()) {
            return;
        }

        BM.start("ProcessEngine.invalidateQueue");
        try (Session session = sessionFactory.openSession()) {
            List<TaskEntity> tasks;
            LocalDateTime now = timeMachine.now();
            int maxResults = 1000;

            //noinspection unchecked
            tasks = session
                    .createQuery("from EngineTask where taskTime <= :toTime order by taskTime")
                    .setParameter("toTime", now.plusMinutes(1))
                    .setMaxResults(maxResults)
                    .list();

            taskQueue = new ArrayDeque<>(tasks); // very trivial way of queue management based on database query with sorting
        } finally {
            BM.stop();
        }
    }

    private void buildInjectionContext() {
        if (baseInjectionContext != null) {
            return;
        }

        baseInjectionContext = InjectionContext.create()
                .add(ProcessEngine.class, this)
                .add(ProcessEngineScheduling.class, scheduling)
                .add(TimeMachine.class, timeMachine)
                .add(SessionFactory.class, sessionFactory);
    }

    // todo ak p3 add audit event log entries
}
