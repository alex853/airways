package net.simforge.airways2.app.vatsimtracker;

import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.app.tools.ThreadStatus;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.legacy.misc.Settings;
import net.simforge.commons.misc.Misc;
import net.simforge.networkview.core.CompactifiedPosition;
import net.simforge.networkview.core.Network;
import net.simforge.networkview.core.Position;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.networkview.core.report.compact.CompactifiedStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

// todo ak2 do not process vatsim reports later than world time!!!
@Component
public class VatsimTrackerBean implements ApplicationRunner, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(VatsimTrackerBean.class);
    private static final String storageRoot = Settings.get("network.view.storage.root");

    private volatile ThreadStatus threadStatus = ThreadStatus.Startup;
    private Thread thread;
    private CompactifiedStorage storage;

    @Autowired
    private WorldRunnerBean worldBean;
    private static Set<String> worldIcaos;

    private static final File root = new File("./vatsim-tracker/");
    private static final File lastProcessedReportFile = new File(root, "last-processed-report");
    private static final File contextsFile = new File(root, "contexts.csv");
    private final Map<Integer, PilotContext> trackedPilots = new ConcurrentHashMap<>();
    private String lastProcessedReport = null;
    private Map<Integer, Position> lastProcessedPositions = null;

    @Override
    public void run(final ApplicationArguments args) {
        log.info("run called");

        storage = CompactifiedStorage.getStorage(storageRoot, Network.VATSIM);

        worldIcaos = worldBean.read(world -> world.airports().all().map(Airports.Airport::getIcao).collect(Collectors.toSet()));

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

            while (threadStatus == ThreadStatus.Running) {
                try {
                    String nextReport;
                    try {
                        if (lastProcessedReport == null) {
                            nextReport = storage.getLastReport();
                        } else {
                            try (final Timing.Timer ignored = Timing.label("VatsimTrackerBean - getNextReport")) {
                                nextReport = storage.getNextReport(lastProcessedReport);
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

                    log.info("report {} - found next report", nextReport);

                    final List<Position> positions;
                    try {
                        positions = storage.loadPositions(nextReport);
                    } catch (final Exception e) {
                        log.error("error on reading next report data", e);
                        Misc.sleep(60000);
                        continue;
                    }

                    //log.info("report {} - {} positions loaded", nextReport, positions.size());

                    // ugly hack injecting missing 'report' timestamp in compactified positions
                    positions.forEach(p -> injectReportIntoCompactifiedPosition(p, nextReport));

                    final String nextReportFinal = nextReport;
                    final Map<Integer, Position> pilotNumberToPosition = new HashMap<>();
                    positions.forEach(p -> pilotNumberToPosition.put(p.getPilotNumber(), p));

                    //log.info("report {} - map1 {}, map2 {}", nextReport, pilotNumberToPosition.size(), trackedPilots.size());

                    // all aircraft located in 'tracked' airports while they are in those airports
                    // when they depart, they will be tracked only if they have appropriate flight plans

                    final AtomicInteger onlinePositions = new AtomicInteger();
                    final AtomicInteger offlinePositions = new AtomicInteger();
                    final List<Integer> erroneousPositions = new ArrayList<>();
                    trackedPilots.forEach((pilotNumber, context) -> {
                        try {
                            final Position position = pilotNumberToPosition.get(pilotNumber);
                            if (position != null) {
                                //log.info("report {} - online position {}", nextReport, pilotNumber);
                                context.nextReportPosition(position);
                                onlinePositions.incrementAndGet();
                            } else {
                                //log.info("report {} - offline position {}", nextReport, pilotNumber);
                                context.noPositionInReport(nextReportFinal);
                                offlinePositions.incrementAndGet();
                            }
                        } catch (final Exception e) {
                            log.error("error on processing position of pilot #" + pilotNumber, e);
                            erroneousPositions.add(pilotNumber);
                        }
                    });

                    //log.info("report {} - tracked pilots processed, online {}, offline {}", nextReport, onlinePositions.get(), offlinePositions.get());

                    final AtomicInteger newPilots = new AtomicInteger();
                    positions.stream()
                            .filter(p -> p.isInAirport() && worldIcaos.contains(p.getAirportIcao()))
                            .forEach(p -> {
                                int pilotNumber = p.getPilotNumber();
                                if (!trackedPilots.containsKey(pilotNumber)) {
                                    try {
                                        final PilotContext pc = new PilotContext(worldBean, pilotNumber);
                                        pc.newPilotContextInAirport(p);
                                        trackedPilots.put(pilotNumber, pc);
                                        newPilots.incrementAndGet();
                                    } catch (final Exception e) {
                                        log.error("error on processing position of pilot #" + pilotNumber, e);
                                        erroneousPositions.add(pilotNumber);
                                    }
                                }
                            });

                    //log.info("report {} - new {} pilots processed", nextReport, newPilots.get());

                    final List<Integer> pilotNumbersForRemoval = trackedPilots.values().stream()
                            .filter(PilotContext::shouldBeRemoved)
                            .map(PilotContext::getPilotNumber)
                            .toList();
                    pilotNumbersForRemoval.forEach(trackedPilots::remove);

                    if (!erroneousPositions.isEmpty()) {
                        log.warn("ERRONEOUS POSITIONS FOUND FOR THE FOLLOWING PILOTS {} and their contexts will be removed", erroneousPositions);
                        erroneousPositions.forEach(trackedPilots::remove);
                    }

                    //log.info("report {} - pilot removal completed, removed {} records", nextReport, pilotNumbersForRemoval.size());

                    lastProcessedReport = nextReport;
                    lastProcessedPositions = pilotNumberToPosition;
                    try {
                        saveStatus();
                    } catch (final Exception e) {
                        log.error("unable to save status", e);
                    }

                    //log.info("report {} - all done", nextReport);
                } catch (final Exception e) {
                    log.error("undetermined exception in vatsim tracker", e);
                    Misc.sleep(10000);
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

        final Csv csv = Csv.load(contextsFile);
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

    public String getLastProcessedReport() {
        return lastProcessedReport;
    }

    public Map<Integer, Position> getLastProcessedPositions() {
        return lastProcessedPositions;
    } // todo ak2 thread safety

    public Collection<PilotContext> contexts() {
        return trackedPilots.values();
    } // todo ak2 thread safety

    public void removePilot(final int pilotNumber) { // todo ak3 thread safety?
        trackedPilots.remove(pilotNumber);
    }

    public Optional<PilotContext> getContextByFlightMissionId(int flightMissionId) {
        try {
            return trackedPilots.values().stream()
                    .filter(c -> c.getFlightMissionId() == flightMissionId)
                    .findFirst();
        } catch (ConcurrentModificationException e) {
            log.warn("Concurrency issue", e);
            return Optional.empty();
        }
    } // todo ak2 thread safety

    public static Position injectReportIntoCompactifiedPosition(final Position p, final String report) {
        try {
            final int reportSeconds = (int) ReportUtils.fromTimestampJava(report).toEpochSecond(ZoneOffset.UTC);
            final Field reportSecondsField = CompactifiedPosition.class.getDeclaredField("reportSeconds");
            reportSecondsField.setAccessible(true);
            reportSecondsField.set(p, reportSeconds);
            return p;
        } catch (final ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isUserConnected(int userId) {
        return false; // todo ak1 vatsim support
    }
}
