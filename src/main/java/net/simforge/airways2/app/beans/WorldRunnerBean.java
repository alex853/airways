package net.simforge.airways2.app.beans;

import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.app.tools.MemoryReport;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.WorldAccess;
import net.simforge.airways2.worldbuilder.World25;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.google.common.base.Preconditions.checkArgument;

@Component
public class WorldRunnerBean implements WorldAccess, ApplicationRunner, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(WorldRunnerBean.class);

    private static final int saveWorldPeriod = Time.ONE_HOUR;

    private volatile ThreadStatus status = ThreadStatus.Startup;
    private Thread thread;
    private volatile World world;
    private volatile int worldTime;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Queue<ActionContext<?>> actionQueue = new ConcurrentLinkedQueue<>();
    private long flightStatsLastSaved = 0;
    // set by requestSave(): the world cycle saves the world 5 minutes after the first request, not at the next hourly save
    private final DeferredSaveRequest saveRequest = new DeferredSaveRequest();

    @Override
    public void run(final ApplicationArguments args) {
        loadWorld();

        thread = new Thread(() -> {
            int lastSaved = (int) (System.currentTimeMillis() / 1000);
            status = ThreadStatus.Running;

            while (status == ThreadStatus.Running) {
                final int now = (int) (System.currentTimeMillis() / 1000);
                boolean needToCatchTime;

                try (final Timing.Timer ignored0 = Timing.label("WorldRunnerBean - 0 - cycle")) {

                    try (final Timing.Timer ignored = Timing.label("WorldRunnerBean - 01 - writeLock.lock"))  {
                        lock.writeLock().lock();
                    }

                    try {
                        try (final Timing.Timer ignored = Timing.label("WorldRunnerBean - 03 - world.process")) {
                            needToCatchTime = world.process(now);
                            worldTime = world.getWorldTime();
                        }

                        try (final Timing.Timer ignored = Timing.label("WorldRunnerBean - 04 - action.perform")) {
                            while (!actionQueue.isEmpty()) {
                                final ActionContext<?> actionContext = actionQueue.poll();
                                // timed by the class which made the action, to see who holds the world thread
                                try (final Timing.Timer ignoredAction = Timing.label("WorldRunnerBean - action of " + actionContext.ownerName())) {
                                    actionContext.perform(world);
                                }
                                needToCatchTime = true; // if at least one event happens, then probably vatsim tracker or anything else is active
                            }
                        }

                        final boolean requestedSaveIsDue = saveRequest.isDue(System.currentTimeMillis());
                        if (requestedSaveIsDue || lastSaved + saveWorldPeriod < now) {
                            if (requestedSaveIsDue) {
                                log.info("world is saved out of turn, it was requested");
                            }
                            // any save does the requested one
                            saveRequest.clear();
                            try (final Timing.Timer ignored = Timing.label("WorldRunnerBean - 06 - saveWorld")) {
                                saveWorld();
                            }
                            lastSaved = now;
                        }
                    } finally {
                        try (final Timing.Timer ignored = Timing.label("WorldRunnerBean - 07 - writeLock.unlock")) {
                            lock.writeLock().unlock();
                        }
                    }

                    if (lastSaved == now) {
                        try (final Timing.Timer ignored = Timing.label("WorldRunnerBean - 08 - reduceWorldBackupCounts")) {
                            reduceWorldBackupCounts();
                        }
                    }
                }

                if (flightStatsLastSaved == 0 || flightStatsLastSaved + 60000 < System.currentTimeMillis()) {
                    flightStatsLastSaved = System.currentTimeMillis();
                    FlightStats.save();
                }

                MemoryReport.print(); // once in 10 minutes

                try (final Timing.Timer ignored0 = Timing.label("WorldRunnerBean - 9 - sleep")) {
                    // parked, not slept: modifySync wakes the thread up, so an action does not wait out the pause (up to 100 ms)
                    LockSupport.parkNanos((needToCatchTime ? 1 : 100) * 1_000_000L);
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
                FlightStats.save();
            }
        });
        thread.setName("world-runner-thread");
        thread.start();
    }

    @Override
    public void destroy() throws Exception {
        log.info("world thread was told to stop");

        status = ThreadStatus.HaveToStopNow;
        thread.join();

        log.info("world thread stopped");
    }

    @Override
    public boolean isReady() {
        return world != null;
    }

    @Override
    public int getWorldTime() {
        return worldTime;
    }

    /**
     * The world without taking the lock: only for reading the change stamps of the storages
     * ({@code Storage#getLastChangedAtMillis}, they are volatile), nothing else is safe to do with it. Null before the world is loaded.
     */
    public World getWorldForChangeStamps() {
        return world;
    }

    /**
     * Makes the world cycle save the world 5 minutes after the FIRST request, not at the next hourly save. For the changes which must not be lost if the
     * server stops before the hourly save: new countries, cities and airports (see AdminGeoController). The save is deferred, so the changes which come one
     * after another (an airport, then a city half a minute later) are saved by one save; the requests which come while it waits do not move it.
     */
    public void requestSave() {
        if (saveRequest.request(System.currentTimeMillis())) {
            log.info("world save is requested, it is due in {} minutes", DeferredSaveRequest.DELAY_MILLIS / 60000);
        }
    }

    @Override
    public <T> T read(final Action<T> action) {
        checkArgument(isReady());

        try (final Timing.Timer ignored = Timing.label("WorldRunnerBean - read # lock")) {
            lock.readLock().lock();
        }
        try {
            return action.invoke(world);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public <T> T modifySync(final Action<T> action) {
        checkArgument(isReady());

        final ActionContext<T> actionContext = new ActionContext<>(action);
        actionQueue.add(actionContext);
        final Thread worldThread = thread;
        if (worldThread != null) {
            LockSupport.unpark(worldThread);
        }
        return actionContext.getResult();
    }

    private void loadWorld() {
        try {
            world = World25.load();
            worldTime = world.getWorldTime();
            log.info("world loaded, world time {}", LocalDateTime.ofEpochSecond(world.getWorldTime(), 0, ZoneOffset.UTC));
            // the pilots who were put to sleep before the wake up was scheduled get it
            world.personControl().scheduleWakeUpOfSleepingPersons();
            // the free pilots get their falling asleep
            world.personControl().scheduleAutoSleepOfIdlePersons();
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

    private void reduceWorldBackupCounts() {
        try {
            World25.diskStorageStrategy.reduceWorldBackupCounts();
        } catch (IOException e) {
            log.warn("unable to reduce world backups", e);
        }
    }

    private enum ThreadStatus {
        Startup,
        Running,
        HaveToStopNow,
        Stopped,
        TerminatedDueToError
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

        // the class which made the lambda (the lambda class name is 'Owner$$Lambda...')
        public String ownerName() {
            final String name = action.getClass().getName();
            final int lambda = name.indexOf("$$Lambda");
            final String owner = lambda >= 0 ? name.substring(0, lambda) : name;
            return owner.substring(owner.lastIndexOf('.') + 1);
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
