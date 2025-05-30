package net.simforge.airways2.app;

import net.simforge.commons.misc.Misc;
import net.simforge.networkview.core.Network;
import net.simforge.networkview.core.report.compact.CompactifiedStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Component
public class VatsimTrackerBean implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(VatsimTrackerBean.class);
    private static final String storageRoot = "../data/network-view"; // todo ak0 move it into settings

    private volatile Status status = Status.Startup;
    private Thread thread;
    private CompactifiedStorage compactifiedStorage;

    @PostConstruct
    public void init() {
        log.info("init called");

        compactifiedStorage = CompactifiedStorage.getStorage(storageRoot, Network.VATSIM);

        String lastProcessedReport;
        try {
            lastProcessedReport = compactifiedStorage.getLastReport();
        } catch (final IOException e) {
            log.error("error on reading last processed report", e); // todo ak0 what to do here?
        }

        // todo ak0 load data

        thread = new Thread(() -> {
            log.info("thread started");

            status = Status.Running;

            while (status == Status.Running) {

                String nextReport = null;
                try {
                    nextReport = compactifiedStorage.getNextReport(lastProcessedReport);
                } catch (final IOException e) {  // todo ak0 what to do here?
                    log.error("error on reading next report");
                }

                if (nextReport == null) {
                    Misc.sleep(10000);
                    continue;
                }

                log.error("found next report {}", nextReport);

                // todo ak0 do the stuff

                lastProcessedReport = nextReport;
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