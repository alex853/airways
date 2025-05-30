package net.simforge.airways2.app;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Misc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class WorldRunnerBean implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(WorldRunnerBean.class);

    private static final int saveWorldPeriod = Time.ONE_HOUR;

    private volatile ThreadStatus status = ThreadStatus.Startup;
    private Thread thread;
    private World world;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Queue<ActionContext<?>> actionQueue = new ConcurrentLinkedQueue<>();

    @PostConstruct
    public void init() {
        loadWorld();

        thread = new Thread(() -> {
            int lastSaved = (int) (System.currentTimeMillis() / 1000);
            status = ThreadStatus.Running;

            while (status == ThreadStatus.Running) {
                final int now = (int) (System.currentTimeMillis() / 1000);
                final boolean needToCatchTime;

                lock.writeLock().lock();
                try {
                    needToCatchTime = world.process(now); // todo ak3 - monitoring - how much time does it take

                    while (!actionQueue.isEmpty()) { // todo ak3 - monitoring - how much time each action is waiting
                        final ActionContext<?> actionContext = actionQueue.poll();
                        actionContext.perform(world);
                    }

                    if (lastSaved + saveWorldPeriod < now) {
                        saveWorld(); // todo ak3 - monitoring - how much time does it take
                        lastSaved = now;
                    }
                } finally {
                    lock.writeLock().unlock();
                }

                if (needToCatchTime) {
                    Thread.yield();
                } else {
                    Misc.sleepBM(10);
                }

            }
            log.info("world cycle stopped, status is {}", status);

            if (status == ThreadStatus.HaveToStopNow) {
                lock.writeLock().lock();
                try {
                    saveWorld();
                } finally {
                    lock.writeLock().unlock();
                }
                status = ThreadStatus.Stopped;
            }
        });
        thread.setName("world-runner-bean-thread");
        thread.start();
    }

    @Override
    public void destroy() throws Exception {
        log.info("world thread was told to stop");

        status = ThreadStatus.HaveToStopNow;
        thread.join();

        log.info("world thread stopped");
    }

    public <T> T read(final Action<T> action) {
        lock.readLock().lock();
        try {
            return action.invoke(world);
        } finally {
            lock.readLock().unlock();
        }
    }

    public <T> T modifySync(final Action<T> action) {
        final ActionContext<T> actionContext = new ActionContext<>(action);
        actionQueue.add(actionContext);
        return actionContext.getResult();
    }

    private void loadWorld() {
        try {
            world = World25.load();
            log.info("world loaded, world time {}", LocalDateTime.ofEpochSecond(world.getWorldTime(), 0, ZoneOffset.UTC));
        } catch (IOException e) {
            log.error("unable to load the world", e);
            throw new RuntimeException("unable to load the world", e);
        }
    }

    private void saveWorld() {
        try {
            world.save();
            log.info("world saved, world time {}", LocalDateTime.ofEpochSecond(world.getWorldTime(), 0, ZoneOffset.UTC));
        } catch (IOException e) {
            status = ThreadStatus.TerminatedDueToError;
            log.error("unable to save the world", e);
            throw new RuntimeException("unable to save the world", e);
        }
    }

    private enum ThreadStatus {
        Startup,
        Running,
        HaveToStopNow,
        Stopped,
        TerminatedDueToError
    }

    public interface Action<T> {
        T invoke(World world);
    }

    private static class ActionContext<T> {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final Action<T> action;
        private volatile boolean finished;
        private volatile boolean getResultInvoked;
        private volatile T result;
        private volatile RuntimeException thrownException;

        public ActionContext(final Action<T> action) {
            this.action = action;
        }

        public T getResult() {
            if (getResultInvoked) {
                throw new IllegalStateException("can't read the result more than one time");
            }
            getResultInvoked = true;

            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (thrownException == null) {
                return result;
            } else {
                throw thrownException;
            }
        }

        public void perform(final World world) {
            if (finished) {
                return;
            }

            try {
                result = action.invoke(world);
            } catch (final RuntimeException e) {
                log.error("error on action execution", e);
                thrownException = e;
            }
            latch.countDown();
            finished = true;
        }
    }
}
