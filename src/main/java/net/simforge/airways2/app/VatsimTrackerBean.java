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
    private static final String storageRoot = "../data/network-view"; // todo ak1 move it into settings

    private volatile ThreadStatus threadStatus = ThreadStatus.Startup;
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

        // todo ak1 load data

        thread = new Thread(() -> {
            log.info("thread started");

            String lastProcessedReport = null;

            threadStatus = ThreadStatus.Running;

            while (threadStatus == ThreadStatus.Running) {

                String nextReport = null;
                try {
                    if (lastProcessedReport == null) {
                        nextReport = compactifiedStorage.getLastReport(); // todo ak1 how much time does it take?
                    } else {
                        nextReport = compactifiedStorage.getNextReport(lastProcessedReport); // todo ak1 how much time does it take?
                    }
                } catch (final IOException e) {  // todo ak1 what to do here?
                    log.error("error on looking for a report", e);
                }

                if (nextReport == null) {
                    Misc.sleep(10000);
                    continue;
                }

                log.info("found next report {}", nextReport);

                final List<Position> positions;
                try {
                    positions = compactifiedStorage.loadPositions(nextReport);
                } catch (IOException e) { // todo ak1 what to do here
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
                                trackedPilots.put(p.getPilotNumber(), Context.newFlightInAirport(p));
                            }
                        });

                final List<Integer> pilotNumbersForRemoval = trackedPilots.values().stream()
                        .filter(Context::shouldBeRemoved)
                        .map(Context::getPilotNumber)
                        .toList();
                pilotNumbersForRemoval.forEach(trackedPilots::remove);

                lastProcessedReport = nextReport;
            }

            log.info("cycle stopped, status is {}", threadStatus);

            // todo ak1 save data
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

    public Collection<Context> contexts() {
        return trackedPilots.values();
    }

    public static class Context {

        private final int pilotNumber;
        private String flightStage; // Preflight, Departing, Flying, Arriving, Arrived
        private String planningStatus; // All Good or some issue with Flight Plan
        private String overallStatus; // Restorable, All Good, Irreversible
        private Position position;
        private int removalCounter;
        private boolean shouldBeRemoved;

        private Context(final int pilotNumber, final Position position) {
            this.pilotNumber = pilotNumber;
            this.position = position;
        }

        public static Context newFlightInAirport(final Position position) {
            final Context context = new Context(position.getPilotNumber(), position);
            context.flightStage = "Preflight"; // todo ak1 ? Departing
            context.planningStatus = context.doPreflightStatusAnalysis(position);
            if (context.planningStatus.equals("All Good")) {
                context.overallStatus = "All Good";
                // todo ak1 push to world
                log.info("{}, {}, {} -> {} - Event 'dispatched'", position.getPilotNumber(), position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
            } else {
                context.overallStatus = "Restorable";
            }
            return context;
        }

        private String doPreflightStatusAnalysis(final Position position) {
            if (position.getFpAircraftType() == null) {
                return "FP - type unknown";
            } else if (position.getFpDeparture() == null || position.getFpDestination() == null) {
                return "FP - no route";
            } else if (position.getFpDeparture() != null && !position.getFpDeparture().equals(position.getAirportIcao())) {
                return "FP - departure misaligned";
            } else if (position.getFpDestination() != null && !worldIcaos.contains(position.getFpDestination())) {
                return "FP - destination is out of world";
            } else {
                return "All Good";
            }
        }

        public int getPilotNumber() {
            return pilotNumber;
        }

        public String getFlightStage() {
            return flightStage;
        }

        public String getPlanningStatus() {
            return planningStatus;
        }

        public String getOverallStatus() {
            return overallStatus;
        }

        public Position getPosition() {
            return position;
        }

        public void nextReportPosition(final Position nextPosition) {
            if (overallStatus.equals("Irreversible")) {
                removalCounter--;
                if (removalCounter <= 0) {
                    shouldBeRemoved = true;
                }
                return;
            }

            boolean takeoff = position.isOnGround() && !nextPosition.isOnGround();
            boolean landing = !position.isOnGround() && nextPosition.isOnGround();

            if (flightStage.equals("Preflight") || flightStage.equals("Departing")) { // todo ak1 departing means starts moving
                if (takeoff) {
                    flightStage = "Flying";
                    if (overallStatus.equals("All Good")) {
                        // todo ak1 push to world
                        log.info("{}, {}, {} -> {} - Event 'takeoff'", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                    } else {
                        overallStatus = "Irreversible";
                        removalCounter = 5;
                    }
                } else {
                    final String newPlanningStatus = doPreflightStatusAnalysis(nextPosition);
                    if (!newPlanningStatus.equals(planningStatus)) {
                        if (newPlanningStatus.equals("All Good")) {
                            planningStatus = "All Good";
                            overallStatus = "All Good";
                            // todo ak1 push to world
                            log.info("{}, {}, {} -> {} - Event 'dispatched'", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                        } else {
                            planningStatus = newPlanningStatus;
                            overallStatus = "Restorable";
                            // todo ak1 push to world
                            log.info("{}, {}, {} -> {} - Event 'cancelled', planning status {}", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination(), newPlanningStatus);
                        }
                    }
                }
            } else if (flightStage.equals("Flying")) {
                if (landing && overallStatus.equals("All Good")) {
                    flightStage = "Arriving";
                    // todo ak1 push to world
                    log.info("{}, {}, {} -> {} - Event 'landing'", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                }
            } // todo ak1 arrived means stops moving for 1-2 reports

            position = nextPosition;
        }

        public void noPositionInReport(final String report) {
            // todo ak1 implement
            shouldBeRemoved = true;
            log.info("{}, {}, {} -> {} - Event 'OFFLINE', terminated", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
        }

        public boolean shouldBeRemoved() {
            return shouldBeRemoved;
        }
    }

    private enum ThreadStatus {
        Startup,
        Running,
        HaveToStopNow,
        Stopped,
        TerminatedDueToError
    }

}