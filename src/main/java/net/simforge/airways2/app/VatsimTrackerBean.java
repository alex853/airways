package net.simforge.airways2.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class VatsimTrackerBean implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(VatsimTrackerBean.class);
    private volatile Status status = Status.Startup;
    private Thread thread;

    @PostConstruct
    public void init() {
        log.info("init called");
        // todo ak0 load data

        thread = new Thread(() -> {
            log.info("thread started");

            status = Status.Running;

            while (status == Status.Running) {

                // todo ak0 do the stuff

            }

            log.info("cycle stopped, status is {}", status);

            // todo ak0 save data
        });
        thread.setName("vatsim-tracker-bean-thread");
        thread.start();

    }

    @Override
    public void destroy() throws Exception {
        log.info("thread was told to stop");

        status = Status.HaveToStopNow;
        thread.join();

        log.info("thread stopped");
    }

/*    public static void main(String[] args) {
        final String storageRoot = "";
        final CompactifiedStorage storage = CompactifiedStorage.getStorage(storageRoot, Network.VATSIM);
        final String lastReport = storage.getLastReport();

        String nextReport = storage.getNextReport(lastReport);
        List<Position> positions = storage.loadPositions(nextReport);
    }*/

    private enum Status {
        Startup,
        Running,
        HaveToStopNow,
        Stopped,
        TerminatedDueToError
    }

}