package net.simforge.airways2.app;

import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.legacy.misc.Settings;
import net.simforge.commons.misc.Misc;
import net.simforge.networkview.core.Network;
import net.simforge.networkview.core.Position;
import net.simforge.networkview.core.report.compact.CompactifiedStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class VatsimTrackerBean implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(VatsimTrackerBean.class);
    private static final String storageRoot = Settings.get("network.view.storage.root");

    private volatile ThreadStatus threadStatus = ThreadStatus.Startup;
    private Thread thread;
    private CompactifiedStorage compactifiedStorage;

    @Autowired
    private WorldRunnerBean worldBean;
    private static Set<String> worldIcaos;

    private static final File root = new File("./vatsim-tracker/");
    private static final File lastProcessedReportFile = new File(root, "last-processed-report");
    private static final File contextsFile = new File(root, "contexts.csv");
    private final Map<Integer, PilotContext> trackedPilots = new HashMap<>();
    private String lastProcessedReport = null;

    @PostConstruct
    public void init() {
        log.info("init called");

        compactifiedStorage = CompactifiedStorage.getStorage(storageRoot, Network.VATSIM);

        worldIcaos = worldBean.read(world -> world.airports().all().stream().map(Airports.Airport::getIcao).collect(Collectors.toSet()));

        thread = new Thread(() -> {
            log.info("thread started");

            try {
                loadStatus();
            } catch (final Exception e) {
                log.error("unable to load status", e);
                throw new RuntimeException(e);
            }
            log.info("status loaded");

            threadStatus = ThreadStatus.Running;

            int nextReportCount = 0;
            long nextReportMilliseconds = 0;

            while (threadStatus == ThreadStatus.Running) {

                String nextReport;
                try {
                    if (lastProcessedReport == null) {
                        nextReport = compactifiedStorage.getLastReport();
                    } else {
                        long before = System.nanoTime();
                        nextReport = compactifiedStorage.getNextReport(lastProcessedReport);
                        nextReportMilliseconds += (System.nanoTime() - before) / 1_000_000;
                        nextReportCount++;

                        if ((nextReportCount % 100) == 0) {
                            log.warn("next report time {} ms", (nextReportMilliseconds / nextReportCount));
                        }
                    }
                } catch (final Exception e) {
                    log.error("error on looking for a report", e);
                    Misc.sleep(60000);
                    continue;
                }

                if (nextReport == null) {
                    Misc.sleep(10000);
                    continue;
                }

                log.info("found next report {}", nextReport);

                final List<Position> positions;
                try {
                    positions = compactifiedStorage.loadPositions(nextReport);
                } catch (IOException e) {
                    log.error("error on reading next report data", e);
                    Misc.sleep(60000);
                    continue;
                }

                final String nextReportFinal = nextReport;
                final Map<Integer, Position> pilotNumberToPosition = positions.stream().collect(Collectors.toMap(Position::getPilotNumber, p -> p));

                // all aircraft located in 'tracked' airports while they are in those airports
                // when they depart, they will be tracked only if they have appropriate flight plans

                trackedPilots.forEach((pilotNumber, context) -> {
                    try {
                        final Position position = pilotNumberToPosition.get(pilotNumber);
                        if (position != null) {
                            context.nextReportPosition(position);
                        } else {
                            context.noPositionInReport(nextReportFinal);
                        }
                    } catch (final RuntimeException e) {
                        log.error("error on processing", e);
                    }
                });

                positions.stream()
                        .filter(p -> p.isInAirport() && worldIcaos.contains(p.getAirportIcao()))
                        .forEach(p -> {
                            if (!trackedPilots.containsKey(p.getPilotNumber())) {
                                try {
                                    final PilotContext pc = trackedPilots.put(p.getPilotNumber(), new PilotContext(worldBean, p.getPilotNumber()));
                                    pc.newPilotContextInAirport(p);
                                } catch (final RuntimeException e) {
                                    log.error("error on processing", e);
                                }
                            }
                        });

                final List<Integer> pilotNumbersForRemoval = trackedPilots.values().stream()
                        .filter(PilotContext::shouldBeRemoved)
                        .map(PilotContext::getPilotNumber)
                        .toList();
                pilotNumbersForRemoval.forEach(trackedPilots::remove);

                lastProcessedReport = nextReport;
                try {
                    saveStatus();
                } catch (IOException e) {
                    log.error("unable to save status", e);
                }
            }

            log.info("cycle stopped, status is {}", threadStatus);
        });
        thread.setName("vatsim-tracker-bean-thread");
        thread.start();
    }

    @Override
    public void destroy() throws Exception {
        log.info("thread was told to stop");

        threadStatus = ThreadStatus.HaveToStopNow;
        thread.join();

        log.info("thread stopped");
    }

    private void loadStatus() throws IOException {
        if (!lastProcessedReportFile.exists()
                || !contextsFile.exists()) {
            log.warn("can't find status data, vatsim tracker will start from the scratch");
            return;
        }

        final String loadedLastProcessedReport = IOHelper.loadFile(lastProcessedReportFile);

        Csv csv = Csv.load(contextsFile);
        final Map<Integer, PilotContext> loadedTrackedPilots = new HashMap<>();
        for (int row = 0; row < csv.rowCount(); row++) {
            final PilotContext c = PilotContext.fromCsv(worldBean, csv, row);
            loadedTrackedPilots.put(c.getPilotNumber(), c);
        }

        lastProcessedReport = loadedLastProcessedReport;
        trackedPilots.clear();
        trackedPilots.putAll(loadedTrackedPilots);
    }

    private void saveStatus() throws IOException {
        Csv csv = Csv.empty();
        PilotContext.addCsvColumns(csv);

        trackedPilots.forEach((pn, c) -> c.toCsv(csv));

        //noinspection ResultOfMethodCallIgnored
        root.mkdirs();
        IOHelper.saveFile(contextsFile, csv.getContent());
        IOHelper.saveFile(lastProcessedReportFile, lastProcessedReport);
    }

    public Collection<PilotContext> contexts() {
        return trackedPilots.values();
    }

    private enum ThreadStatus {
        Startup,
        Running,
        HaveToStopNow,
        Stopped,
        TerminatedDueToError
    }


}