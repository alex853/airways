package net.simforge.airways2.app;

import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.commons.misc.Geo;
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
                                    trackedPilots.put(p.getPilotNumber(), Context.newFlightInAirport(p));
                                } catch (final RuntimeException e) {
                                    log.error("error on processing", e);
                                }
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
        private FlightStage flightStage;
        private PlanningStatus planningStatus; // All Good or some issue with Flight Plan
        private String aircraftType;
        private String aircraftRegNo;
        private String plannedDeparture;
        private String plannedDestination;
        private OverallStatus overallStatus; // Restorable, All Good, Irreversible
        private Position position;
        private int removalCounter;
        private boolean shouldBeRemoved;
        private final Queue<Float> distanceLegs = new LinkedList<>();

        private Context(final int pilotNumber, final Position position) {
            this.pilotNumber = pilotNumber;
            this.position = position;
        }

        public static Context newFlightInAirport(final Position position) {
            final Context context = new Context(position.getPilotNumber(), position);
            context.flightStage = FlightStage.Preflight;
            context.planningStatus = context.doPreflightStatusAnalysis(position);
            if (context.planningStatus == PlanningStatus.AllGood) {
                context.overallStatus = OverallStatus.AllGood;
                context.aircraftType = position.getFpAircraftType();
                context.aircraftRegNo = position.getRegNo();
                context.plannedDeparture = position.getFpDeparture();
                context.plannedDestination = position.getFpDestination();
                // todo ak1 push to world
                log.info("{}, {}, {} -> {} - Event 'dispatched'", position.getPilotNumber(), position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
            } else {
                context.overallStatus = OverallStatus.Restorable;
            }
            return context;
        }

        private PlanningStatus doPreflightStatusAnalysis(final Position position) {
            if (position.getFpAircraftType() == null) {
                return PlanningStatus.FP_TypeUnknown;
            } else if (position.getFpDeparture() == null || position.getFpDestination() == null) {
                return PlanningStatus.FP_NoRoute;
            } else if (position.getFpDeparture() != null && !position.getFpDeparture().equals(position.getAirportIcao())) {
                return PlanningStatus.FP_DepartureMisaligned;
            } else if (position.getFpDestination() != null && !worldIcaos.contains(position.getFpDestination())) {
                return PlanningStatus.FP_DestinationIsOutOfTheWorld;
            } else {
                return PlanningStatus.AllGood;
            }
        }

        public int getPilotNumber() {
            return pilotNumber;
        }

        public FlightStage getFlightStage() {
            return flightStage;
        }

        public PlanningStatus getPlanningStatus() {
            return planningStatus;
        }

        public String getPlannedDeparture() {
            return plannedDeparture;
        }

        public String getPlannedDestination() {
            return plannedDestination;
        }

        public String getLocationAirport() {
            return position.isInAirport() ? position.getAirportIcao() : null;
        }

        public String getAircraftType() {
            return aircraftType;
        }

        public String getAircraftRegNo() {
            return aircraftRegNo;
        }

        public OverallStatus getOverallStatus() {
            return overallStatus;
        }

        public float getLastTrackedDistance() {
            return distanceLegs.stream().reduce(0.0f, Float::sum);
        }

        public void nextReportPosition(final Position nextPosition) {
            if (overallStatus == OverallStatus.Irreversible) {
                if (removalCounter == 0) {
                    shouldBeRemoved = true;
                } else {
                    removalCounter--;
                }
                return;
            }

            boolean takeoff = position.isOnGround() && !nextPosition.isOnGround();
            boolean landing = !position.isOnGround() && nextPosition.isOnGround();

            distanceLegs.add((float) Geo.distance(position.getCoords(), nextPosition.getCoords()));
            while (distanceLegs.size() > 3) {
                distanceLegs.poll();
            }

            if (flightStage == FlightStage.Preflight || flightStage == FlightStage.Departing) {
                if (takeoff) {
                    flightStage = FlightStage.Flying;
                    if (overallStatus == OverallStatus.AllGood) {
                        // todo ak1 push to world
                        log.info("{}, {}, {} -> {} - Event 'takeoff'", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                    } else {
                        overallStatus = OverallStatus.Irreversible;
                        removalCounter = 5;
                    }
                } else {
                    if (flightStage == FlightStage.Preflight
                            && planningStatus == PlanningStatus.AllGood
                            && overallStatus == OverallStatus.AllGood
                            && getLastTrackedDistance() > 0.2) { // threshold
                        flightStage = FlightStage.Departing;
                        // todo ak1 push to world
                        log.info("{}, {}, {} -> {} - Event 'blocks-off'", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                    }

                    final PlanningStatus newPlanningStatus = doPreflightStatusAnalysis(nextPosition);
                    final OverallStatus newOverallStatus = newPlanningStatus == PlanningStatus.AllGood ? OverallStatus.AllGood : OverallStatus.Restorable;
                    if (newOverallStatus != overallStatus) {
                        if (newOverallStatus == OverallStatus.AllGood) {
                            planningStatus = PlanningStatus.AllGood;
                            aircraftType = nextPosition.getFpAircraftType();
                            aircraftRegNo = nextPosition.getRegNo();
                            plannedDeparture = nextPosition.getFpDeparture();
                            plannedDestination = nextPosition.getFpDestination();
                            overallStatus = OverallStatus.AllGood;
                            // todo ak1 push to world
                            log.info("{}, {}, {} -> {} - Event 'dispatched'", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                        } else {
                            planningStatus = newPlanningStatus;
                            overallStatus = OverallStatus.Restorable;
                            // todo ak1 push to world
                            log.info("{}, {}, {} -> {} - Event 'cancelled', planning status {}", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination(), newPlanningStatus);
                        }
                    }
                }
            } else if (flightStage == FlightStage.Flying) {
                if (landing && overallStatus == OverallStatus.AllGood) {
                    if (plannedDestination.equals(nextPosition.getAirportIcao())) {
                        flightStage = FlightStage.Arriving;
                        // todo ak1 push to world
                        log.info("{}, {}, {} -> {} - Event 'landing'", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                    } else {
                        flightStage = FlightStage.Arrived;
                        overallStatus = OverallStatus.Irreversible;
                        removalCounter = 5;
                        // todo ak1 push to world
                        log.info("{}, {}, {} -> {} - Event 'landing' on wrong airport, removing", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                    }
                }
            } else if (flightStage == FlightStage.Arriving) {
                if (overallStatus == OverallStatus.AllGood
                        && getLastTrackedDistance() < 0.3) {
                    flightStage = FlightStage.Arrived;
                    // todo ak1 push to world
                    log.info("{}, {}, {} -> {} - Event 'blocks-on'", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                }
            }

            position = nextPosition;
        }

        public void noPositionInReport(final String report) {
            // todo ak1 re-implement

            if (overallStatus == OverallStatus.Irreversible) {
                if (removalCounter == 0) {
                    shouldBeRemoved = true;
                } else {
                    removalCounter--;
                }
            } else if (overallStatus == OverallStatus.AllGood) {
                if (flightStage == FlightStage.Arriving) {
                    // todo ak1 push to world
                    log.info("{}, {}, {} -> {} - Event 'blocks-on' due to OFFLINE", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                    shouldBeRemoved = true;
                } else {
                    log.info("{}, {}, {} -> {} - Event 'OFFLINE' from AllGood, removing", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                    shouldBeRemoved = true;
                }
            } else { // Restorable
                log.info("{}, {}, {} -> {} - Event 'OFFLINE' from Restorable, removing", pilotNumber, position.getFpAircraftType(), position.getFpDeparture(), position.getFpDestination());
                shouldBeRemoved = true;
            }
        }

        public boolean shouldBeRemoved() {
            return shouldBeRemoved;
        }
    }

    public enum FlightStage {Preflight, Departing, Flying, Arriving, Arrived}

    public enum PlanningStatus {AllGood, FP_TypeUnknown, FP_NoRoute, FP_DepartureMisaligned, FP_DestinationIsOutOfTheWorld}

    public enum OverallStatus {AllGood, Restorable, Irreversible}

    private enum ThreadStatus {
        Startup,
        Running,
        HaveToStopNow,
        Stopped,
        TerminatedDueToError
    }

}