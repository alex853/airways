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

            waitForWorldReady();
            simTracker.setWorldAccess(worldBean);

            threadStatus = ThreadStatus.Running;

            while (threadStatus == ThreadStatus.Running) {
                try {
                    savePosreps();

                    // todo ak0 save contexts in that scheduled processing code
                } catch (final Exception e) {
                    log.error("undetermined exception in sim tracker", e);
                    Misc.sleep(10000);
                }
                Misc.sleep(100);
            }

            log.info("cycle stopped, status is {}", threadStatus);
        });
        thread.setName("sim-tracker-bean-thread");
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

    private void waitForWorldReady() {
        while (!worldBean.isReady()) { // todo ak1 it can go wrong, but now it will cycle indefinitely
            Misc.sleep(100);
        }
    }

    @Override
    public void destroy() throws Exception {
        log.info("thread was told to stop");

        threadStatus = ThreadStatus.HaveToStopNow;
        thread.join();

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
        return simTracker.getUserStatus(userId) != null; // todo ak0 add support for recent connections, recently lost connections, etc
    }

    public void refreshContext(int userId) {
        simTracker.refreshContext(userId);
    }

    private record PosrepInfo(int userId, String posrep) { }
}
