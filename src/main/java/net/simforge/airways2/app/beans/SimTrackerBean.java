package net.simforge.airways2.app.beans;

import net.simforge.airways2.app.tools.ThreadStatus;
import net.simforge.airways2.pilottracker.SimTracker;
import net.simforge.airways2.pilottracker.SimTrackerTools;
import net.simforge.commons.misc.Misc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class SimTrackerBean implements ApplicationRunner, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(SimTrackerBean.class);

    private static final long WORLD_READY_TIMEOUT_MS = 10 * 60 * 1000;

    private final SimTracker simTracker = new SimTracker();

    private volatile ThreadStatus threadStatus = ThreadStatus.Startup;
    private Thread thread;

    private final Queue<PosrepInfo> posrepSavingQueue = new ConcurrentLinkedQueue<>();

    @Autowired
    private WorldRunnerBean worldBean;

    @Override
    public void run(final ApplicationArguments args) {
        log.info("run called");

        thread = new Thread(() -> {
            log.info("thread started");

            if (!waitForWorldReady()) {
                return;
            }
            simTracker.setWorldAccess(worldBean);

            simTracker.restoreContexts();

            threadStatus = ThreadStatus.Running;

            long lastContextProcessing = System.currentTimeMillis();
            long lastContextSaving = System.currentTimeMillis();

            while (threadStatus == ThreadStatus.Running) {
                long now = System.currentTimeMillis();

                try {
                    savePosreps();

                    if (lastContextProcessing + 5000 < now) {
                        simTracker.processContexts();
                        lastContextProcessing = now;
                    }

                    if (lastContextSaving + 60000 < now) {
                        simTracker.saveContexts();
                        lastContextSaving = now;
                    }
                } catch (final Exception e) {
                    log.error("undetermined exception in sim tracker", e);
                    Misc.sleep(10000);
                }
                Misc.sleep(100);
            }

            log.info("cycle stopped, status is {}", threadStatus);
        });
        thread.setName("sim-tracker-thread");
        thread.start();
    }

    private void savePosreps() {
        while (!posrepSavingQueue.isEmpty()) {
            PosrepInfo posrepInfo = posrepSavingQueue.poll();
            if (posrepInfo == null)
                break;
            SimTrackerTools.savePosrep(posrepInfo.userId, posrepInfo.posrep);
        }
    }

    /**
     * Waits for the world to be ready, gives up after WORLD_READY_TIMEOUT_MS or when the bean is told to stop.
     *
     * @return true if the world is ready, false if the thread has to exit (threadStatus is set accordingly)
     */
    private boolean waitForWorldReady() {
        final long startedAt = System.currentTimeMillis();
        while (!worldBean.isReady()) {
            if (threadStatus == ThreadStatus.HaveToStopNow) {
                log.info("stopped while waiting for the world, exiting");
                threadStatus = ThreadStatus.Stopped;
                return false;
            }

            if (System.currentTimeMillis() - startedAt > WORLD_READY_TIMEOUT_MS) {
                log.error("world is not ready within {} minutes, sim tracker thread exits", WORLD_READY_TIMEOUT_MS / 60_000);
                threadStatus = ThreadStatus.TerminatedDueToError;
                return false;
            }

            Misc.sleep(100);
        }
        return true;
    }

    @Override
    public void destroy() throws Exception {
        log.info("thread was told to stop");

        threadStatus = ThreadStatus.HaveToStopNow;
        thread.join();

        // the last state of the contexts should not be lost on a regular stop (deploy etc)
        simTracker.saveContexts();

        log.info("thread stopped");
    }

    public void processPosrep(int userId, String posrep) {
        simTracker.processPosrep(userId, posrep);
        posrepSavingQueue.add(new PosrepInfo(userId, posrep));
    }

    public SimTracker.UserStatus getSimStatus(int userId) {
        return simTracker.getUserStatus(userId);
    }

    public boolean isUserConnected(int userId) {
        return simTracker.getUserStatus(userId) != null;
    }

    public void refreshContext(int userId) {
        simTracker.refreshContext(userId);
    }

    private record PosrepInfo(int userId, String posrep) { }
}
