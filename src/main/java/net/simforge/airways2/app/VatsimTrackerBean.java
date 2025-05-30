package net.simforge.airways2.app;

import net.simforge.airways2.world.datamodel.Airports;
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
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class VatsimTrackerBean implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(VatsimTrackerBean.class);
    private static final String storageRoot = "../data/network-view"; // todo ak0 move it into settings

    private volatile Status status = Status.Startup;
    private Thread thread;
    private CompactifiedStorage compactifiedStorage;

    @Autowired
    private WorldRunnerBean worldBean;
    private static Set<String> worldIcaos;

    private final Map<Integer, Context> trackedPilots = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("init called");

        compactifiedStorage = CompactifiedStorage.getStorage(storageRoot, Network.VATSIM);

        worldIcaos = worldBean.read(world -> world.airports().all().stream().map(Airports.Airport::getIcao).collect(Collectors.toSet()));

        // todo ak0 load data

        thread = new Thread(() -> {
            log.info("thread started");

            String lastProcessedReport = null;

            status = Status.Running;

            while (status == Status.Running) {

                String nextReport = null;
                try {
                    if (lastProcessedReport == null) {
                        nextReport = compactifiedStorage.getLastReport(); // todo ak0 how much time does it take?
                    } else {
                        nextReport = compactifiedStorage.getNextReport(lastProcessedReport); // todo ak0 how much time does it take?
                    }
                } catch (final IOException e) {  // todo ak0 what to do here?
                    log.error("error on looking for a report", e);
                }

                if (nextReport == null) {
                    Misc.sleep(10000);
                    continue;
                }

                log.error("found next report {}", nextReport);

                final List<Position> positions;
                try {
                    positions = compactifiedStorage.loadPositions(nextReport);
                } catch (IOException e) { // todo ak0 what to do here
                    log.error("error on reading next report data", e);
                    Misc.sleep(10000);
                    continue;
                }

                final String nextReportFinal = nextReport;
                final Map<Integer, Position> pilotNumberToPosition = positions.stream().collect(Collectors.toMap(Position::getPilotNumber, p -> p));

                // all aircraft located in 'tracked' airports while they are in those airports
                // when they depart, they will be tracked only if they have appropriate flight plans

                trackedPilots.forEach((pilotNumber, context) -> {
                    final Position position = pilotNumberToPosition.get(pilotNumber);
                    if (position != null) {
                        context.nextReportPosition(position);
                    } else {
                        context.noPositionInReport(nextReportFinal);
                    }
                });

                positions.stream()
                        .filter(p -> p.isInAirport() && worldIcaos.contains(p.getAirportIcao()))
                        .forEach(p -> {
                            if (!trackedPilots.containsKey(p.getPilotNumber())) {
                                trackedPilots.put(p.getPilotNumber(), Context.build(p));
                            }
                        });

                final List<Integer> pilotNumbersForRemoval = trackedPilots.values().stream()
                        .filter(Context::shouldBeRemoved)
                        .map(Context::getPilotNumber)
                        .toList();
                pilotNumbersForRemoval.forEach(trackedPilots::remove);

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

    public Collection<Context> contexts() {
        return trackedPilots.values();
    }

    public static class Context {

        private final int pilotNumber;
        private String status;
        private Position position;

        private Context(final int pilotNumber, final Position position) {
            this.pilotNumber = pilotNumber;
            this.position = position;
        }

        public static Context build(final Position position) {
            final Context context = new Context(position.getPilotNumber(), position);
            context.updateStatus();
            return context;
        }

        private void updateStatus() {
            if (position.getFpAircraftType() == null) {
                status = "FP - type unknown";
            } else if (position.getFpDeparture() == null || position.getFpDestination() == null) {
                status = "FP - no route";
            } else if (position.getFpDeparture() != null && !position.getFpDeparture().equals(position.getAirportIcao())) {
                status = "FP - departure misaligned";
            } else if (position.getFpDestination() != null && !worldIcaos.contains(position.getFpDestination())) {
                status = "FP - destination is out of world";
            } else {
                status = "ALL OK";
            }
        }

        public int getPilotNumber() {
            return pilotNumber;
        }

        public String getStatus() {
            return status;
        }

        public Position getPosition() {
            return position;
        }

        public void nextReportPosition(final Position position) {
            // todo ak0 implement
        }

        public void noPositionInReport(final String report) {
            // todo ak0 implement
        }

        public boolean shouldBeRemoved() {
            return false;
        }
    }

    private enum Status {
        Startup,
        Running,
        HaveToStopNow,
        Stopped,
        TerminatedDueToError
    }

}