package net.simforge.airways2.app;

import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
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
import java.io.File;
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

    private static final File root = new File("./vatsim-tracker/");
    private static final File lastProcessedReportFile = new File(root, "last-processed-report");
    private static final File contextsFile = new File(root, "contexts.csv");
    private final Map<Integer, Context> trackedPilots = new HashMap<>();
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
            } catch (IOException e) {
                log.error("unable to load status", e);
                throw new RuntimeException(e);
            }
            log.info("status loaded");

            threadStatus = ThreadStatus.Running;

            while (threadStatus == ThreadStatus.Running) {

                String nextReport;
                try {
                    if (lastProcessedReport == null) {
                        nextReport = compactifiedStorage.getLastReport(); // todo ak1 how much time does it take?
                    } else {
                        nextReport = compactifiedStorage.getNextReport(lastProcessedReport); // todo ak1 how much time does it take?
                    }
                } catch (final IOException e) {
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

    private void loadStatus() throws IOException {
        if (!lastProcessedReportFile.exists()
                || !contextsFile.exists()) {
            log.warn("can't find status data, vatsim tracker will start from the scratch");
            return;
        }

        final String loadedLastProcessedReport = IOHelper.loadFile(lastProcessedReportFile);

        Csv csv = Csv.load(contextsFile);
        final Map<Integer, Context> loadedTrackedPilots = new HashMap<>();
        for (int row = 0; row < csv.rowCount(); row++) {
            final Context c = new Context(Integer.parseInt(csv.value(row, CSV_PILOT_NUMBER)));
            c.flightStage = FlightStage.valueOf(csv.value(row, CSV_FLIGHT_STAGE));
            c.planningStatus = PlanningStatus.valueOf(csv.value(row, CSV_PLANNING_STATUS));
            c.aircraftType = csv.value(row, CSV_AIRCRAFT_TYPE);
            c.aircraftRegNo = csv.value(row, CSV_AIRCRAFT_REG_NO);
            c.plannedDeparture = csv.value(row, CSV_PLANNED_DEPARTURE);
            c.plannedDestination = csv.value(row, CSV_PLANNED_DESTINATION);
            c.overallStatus = OverallStatus.valueOf(csv.value(row, CSV_OVERALL_STATUS));
            c.positionIsOnGround = Boolean.parseBoolean(csv.value(row, CSV_POSITION_IS_ON_GROUND));
            c.positionAirportIcao = csv.value(row, CSV_POSITION_AIRPORT_ICAO);
            c.positionLatitude = Double.parseDouble(csv.value(row, CSV_POSITION_LATITUDE));
            c.positionLongitude = Double.parseDouble(csv.value(row, CSV_POSITION_LONGITUDE));
            c.removalCounter = Integer.parseInt(csv.value(row, CSV_REMOVAL_COUNTER));
            c.shouldBeRemoved = Boolean.parseBoolean(csv.value(row, CSV_SHOULD_BE_REMOVED));
            c.distanceLegs.addAll(Arrays.stream(csv.value(row, CSV_DISTANCE_LEGS).split(":"))
                    .map(Float::parseFloat)
                    .toList());
            loadedTrackedPilots.put(c.pilotNumber, c);
        }

        lastProcessedReport = loadedLastProcessedReport;
        trackedPilots.clear();
        trackedPilots.putAll(loadedTrackedPilots);
    }

    private void saveStatus() throws IOException {
        Csv csv = Csv.empty();
        csv.addColumn(CSV_PILOT_NUMBER);
        csv.addColumn(CSV_FLIGHT_STAGE);
        csv.addColumn(CSV_PLANNING_STATUS);
        csv.addColumn(CSV_AIRCRAFT_TYPE);
        csv.addColumn(CSV_AIRCRAFT_REG_NO);
        csv.addColumn(CSV_PLANNED_DEPARTURE);
        csv.addColumn(CSV_PLANNED_DESTINATION);
        csv.addColumn(CSV_OVERALL_STATUS);
        csv.addColumn(CSV_POSITION_IS_ON_GROUND);
        csv.addColumn(CSV_POSITION_AIRPORT_ICAO);
        csv.addColumn(CSV_POSITION_LATITUDE);
        csv.addColumn(CSV_POSITION_LONGITUDE);
        csv.addColumn(CSV_REMOVAL_COUNTER);
        csv.addColumn(CSV_SHOULD_BE_REMOVED);
        csv.addColumn(CSV_DISTANCE_LEGS);

        trackedPilots.forEach((pn, c) -> {
            final int row = csv.addRow();
            csv.set(row, CSV_PILOT_NUMBER, String.valueOf(c.pilotNumber));
            csv.set(row, CSV_FLIGHT_STAGE, c.flightStage.name());
            csv.set(row, CSV_PLANNING_STATUS, c.planningStatus.name());
            csv.set(row, CSV_AIRCRAFT_TYPE, c.aircraftType);
            csv.set(row, CSV_AIRCRAFT_REG_NO, c.aircraftRegNo);
            csv.set(row, CSV_PLANNED_DEPARTURE, c.plannedDeparture);
            csv.set(row, CSV_PLANNED_DESTINATION, c.plannedDestination);
            csv.set(row, CSV_OVERALL_STATUS, c.overallStatus.name());
            csv.set(row, CSV_POSITION_IS_ON_GROUND, String.valueOf(c.positionIsOnGround));
            csv.set(row, CSV_POSITION_AIRPORT_ICAO, c.positionAirportIcao);
            csv.set(row, CSV_POSITION_LATITUDE, String.valueOf(c.positionLatitude));
            csv.set(row, CSV_POSITION_LONGITUDE, String.valueOf(c.positionLongitude));
            csv.set(row, CSV_REMOVAL_COUNTER, String.valueOf(c.removalCounter));
            csv.set(row, CSV_SHOULD_BE_REMOVED, String.valueOf(c.shouldBeRemoved));
            csv.set(row, CSV_DISTANCE_LEGS, c.distanceLegs.stream().
                    map(String::valueOf)
                    .collect(Collectors.joining(":")));
        });

        //noinspection ResultOfMethodCallIgnored
        root.mkdirs();
        IOHelper.saveFile(contextsFile, csv.getContent());
        IOHelper.saveFile(lastProcessedReportFile, lastProcessedReport);
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
        private PlanningStatus planningStatus;
        private String aircraftType;
        private String aircraftRegNo;
        private String plannedDeparture;
        private String plannedDestination;
        private OverallStatus overallStatus;
        private boolean positionIsOnGround;
        private String positionAirportIcao;
        private double positionLatitude;
        private double positionLongitude;
        private int removalCounter;
        private boolean shouldBeRemoved;
        private final Queue<Float> distanceLegs = new LinkedList<>();

        Context(final int pilotNumber) {
            this.pilotNumber = pilotNumber;
        }

        public static Context newFlightInAirport(final Position position) {
            final Context context = new Context(position.getPilotNumber());
            context.copyPositionFields(position);

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

        private void copyPositionFields(final Position position) {
            positionIsOnGround = position.isOnGround();
            positionAirportIcao = position.isInAirport() ? position.getAirportIcao() : null;
            positionLatitude = position.getCoords().getLat();
            positionLongitude = position.getCoords().getLon();
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
            return positionAirportIcao;
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

            boolean takeoff = positionIsOnGround && !nextPosition.isOnGround();
            boolean landing = !positionIsOnGround && nextPosition.isOnGround();

            distanceLegs.add((float) Geo.distance(Geo.coords(positionLatitude, positionLongitude), nextPosition.getCoords()));
            while (distanceLegs.size() > 3) {
                distanceLegs.poll();
            }

            if (flightStage == FlightStage.Preflight || flightStage == FlightStage.Departing) {
                if (takeoff) {
                    flightStage = FlightStage.Flying;
                    if (overallStatus == OverallStatus.AllGood) {
                        // todo ak1 push to world
                        log.info("{}, {}, {} -> {} - Event 'takeoff'", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
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
                        log.info("{}, {}, {} -> {} - Event 'blocks-off'", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
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
                            log.info("{}, {}, {} -> {} - Event 'dispatched'", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                        } else {
                            planningStatus = newPlanningStatus;
                            overallStatus = OverallStatus.Restorable;
                            // todo ak1 push to world
                            log.info("{}, {}, {} -> {} - Event 'cancelled', planning status {}", pilotNumber, aircraftType, plannedDeparture, plannedDestination, newPlanningStatus);
                        }
                    }
                }
            } else if (flightStage == FlightStage.Flying) {
                if (landing && overallStatus == OverallStatus.AllGood) {
                    if (plannedDestination.equals(nextPosition.getAirportIcao())) {
                        flightStage = FlightStage.Arriving;
                        // todo ak1 push to world
                        log.info("{}, {}, {} -> {} - Event 'landing'", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                    } else {
                        flightStage = FlightStage.Arrived;
                        overallStatus = OverallStatus.Irreversible;
                        removalCounter = 5;
                        // todo ak1 push to world
                        log.info("{}, {}, {} -> {} - Event 'landing' on wrong airport, removing", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                    }
                }
            } else if (flightStage == FlightStage.Arriving) {
                if (overallStatus == OverallStatus.AllGood
                        && getLastTrackedDistance() < 0.3) {
                    flightStage = FlightStage.Arrived;
                    // todo ak1 push to world
                    log.info("{}, {}, {} -> {} - Event 'blocks-on'", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                }
            }

            copyPositionFields(nextPosition);
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
                    log.info("{}, {}, {} -> {} - Event 'blocks-on' due to OFFLINE", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                    shouldBeRemoved = true;
                } else {
                    log.info("{}, {}, {} -> {} - Event 'OFFLINE' from AllGood, removing", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                    shouldBeRemoved = true;
                }
            } else { // Restorable
                log.info("{}, {}, {} -> {} - Event 'OFFLINE' from Restorable, removing", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                shouldBeRemoved = true;
            }
        }

        public boolean shouldBeRemoved() {
            return shouldBeRemoved;
        }
    }

    public enum FlightStage {
        Preflight,
        Departing,
        Flying,
        Arriving,
        Arrived
    }

    public enum PlanningStatus {
        AllGood,
        FP_TypeUnknown,
        FP_NoRoute,
        FP_DepartureMisaligned,
        FP_DestinationIsOutOfTheWorld
    }

    public enum OverallStatus {
        AllGood,
        Restorable,
        Irreversible
    }

    private enum ThreadStatus {
        Startup,
        Running,
        HaveToStopNow,
        Stopped,
        TerminatedDueToError
    }

    private static final String CSV_PILOT_NUMBER = "PilotNumber";
    private static final String CSV_FLIGHT_STAGE = "FlightStage";
    private static final String CSV_PLANNING_STATUS = "PlanningStatus";
    private static final String CSV_AIRCRAFT_TYPE = "AircraftType";
    private static final String CSV_AIRCRAFT_REG_NO = "AircraftRegNo";
    private static final String CSV_PLANNED_DEPARTURE = "PlannedDeparture";
    private static final String CSV_PLANNED_DESTINATION = "PlannedDestination";
    private static final String CSV_OVERALL_STATUS = "OverallStatus";
    private static final String CSV_POSITION_IS_ON_GROUND = "PositionIsOnGround";
    private static final String CSV_POSITION_AIRPORT_ICAO = "PositionAirportIcao";
    private static final String CSV_POSITION_LATITUDE = "PositionLatitude";
    private static final String CSV_POSITION_LONGITUDE = "PositionLongitude";
    private static final String CSV_REMOVAL_COUNTER = "RemovalCounter";
    private static final String CSV_SHOULD_BE_REMOVED = "ShouldBeRemoved";
    private static final String CSV_DISTANCE_LEGS = "DistanceLegs";

}