package net.simforge.airways2.app.vatsimtracker;

import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.world.processors.ShadowJetLogic;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.misc.Geo;
import net.simforge.networkview.core.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class PilotContext {
    private static final Logger log = LoggerFactory.getLogger(PilotContext.class);
    private static final File pilotLogsRoot = new File("./vatsim-tracker/pilot-logs/");

    private final WorldRunnerBean worldBean;
    private final Set<String> worldIcaos;
    private final int pilotNumber;
    private FlightStage flightStage;
    private PlanningStatus planningStatus;
    private String aircraftType;
    private String aircraftRegNo;
    private String plannedDeparture;
    private String plannedDestination;
    private OverallStatus overallStatus;
    private int flightMissionId;
    private boolean positionIsOnGround;
    private String positionAirportIcao;
    private double positionLatitude;
    private double positionLongitude;
    private int removalCounter;
    private boolean shouldBeRemoved;
    private final Queue<Float> distanceLegs = new LinkedList<>();

    public PilotContext(final WorldRunnerBean worldBean, final int pilotNumber) {
        this.worldBean = worldBean;
        this.worldIcaos = worldBean.read(world -> world.airports().all().stream().map(Airports.Airport::getIcao).collect(Collectors.toSet()));
        this.pilotNumber = pilotNumber;
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

    public boolean shouldBeRemoved() {
        return shouldBeRemoved;
    }

    public float getLastTrackedDistance() {
        return distanceLegs.stream().reduce(0.0f, Float::sum);
    }

    public void newPilotContextInAirport(final Position position) {
        copyPositionFields(position);

        flightStage = FlightStage.Preflight;
        planningStatus = doPreflightStatusAnalysis(position);

        if (planningStatus == PlanningStatus.AllGood) {
            overallStatus = OverallStatus.AllGood;
            aircraftType = position.getFpAircraftType();
            aircraftRegNo = position.getRegNo();
            plannedDeparture = position.getFpDeparture();
            plannedDestination = position.getFpDestination();

            final FlightMissions.Mission mission = mission_dispatchNew();
            flightMissionId = mission.getId();

            log.info("{}, {}, {} -> {} - Event 'dispatched'", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
            pilotLog("Event 'dispatched' == via new flight in airport");
        } else {
            overallStatus = OverallStatus.Restorable;
            pilotLog("new flight in Restorable status");
        }
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

                    mission_takeoff();

                    log.info("{}, {}, {} -> {} - Event 'takeoff'", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                    pilotLog("Event 'takeoff'");
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

                    mission_blocksOff();

                    log.info("{}, {}, {} -> {} - Event 'blocks-off'", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                    pilotLog("Event 'blocks-off'");
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

                        final FlightMissions.Mission mission = mission_dispatchNew();
                        flightMissionId = mission.getId();

                        log.info("{}, {}, {} -> {} - Event 'dispatched'", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                        pilotLog("Event 'dispatched' == via some correction");
                    } else {
                        planningStatus = newPlanningStatus;
                        overallStatus = OverallStatus.Restorable;

                        mission_cancelBeforeFlightIfExists();

                        log.info("{}, {}, {} -> {} - Event 'cancelled', planning status {}", pilotNumber, aircraftType, plannedDeparture, plannedDestination, newPlanningStatus);
                        pilotLog("Event 'cancelled' as flight becomes Restorable");
                    }
                }
            }
        } else if (flightStage == FlightStage.Flying) {
            if (landing && overallStatus == OverallStatus.AllGood) {
                if (plannedDestination.equals(nextPosition.getAirportIcao())) {
                    flightStage = FlightStage.Arriving;

                    mission_landing();

                    log.info("{}, {}, {} -> {} - Event 'landing'", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                    pilotLog("Event 'landing'");
                } else {
                    flightStage = FlightStage.Arrived;
                    overallStatus = OverallStatus.Irreversible;
                    removalCounter = 5;
                    // todo ak0 push to world
                    log.info("{}, {}, {} -> {} - Event 'landing' on wrong airport, removing", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                    pilotLog("Event 'landing' on wrong airport, removing");
                }
            }
        } else if (flightStage == FlightStage.Arriving) {
            if (overallStatus == OverallStatus.AllGood
                    && getLastTrackedDistance() < 0.3) {
                flightStage = FlightStage.Arrived;

                mission_blocksOn();

                log.info("{}, {}, {} -> {} - Event 'blocks-on'", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                pilotLog("Event 'blocks-on'");
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
                // todo ak0 push to world
                log.info("{}, {}, {} -> {} - Event 'blocks-on' due to OFFLINE", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                pilotLog("Event 'blocks-on' due to pilot went offline");
                shouldBeRemoved = true;
            } else {
                // todo ak0 push to world
                log.info("{}, {}, {} -> {} - Event 'OFFLINE' from AllGood, removing", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
                pilotLog("Event 'offline' from AllGood, removing");
                shouldBeRemoved = true;
            }
        } else { // Restorable
            // todo ak0 push to world
            log.info("{}, {}, {} -> {} - Event 'OFFLINE' from Restorable, removing", pilotNumber, aircraftType, plannedDeparture, plannedDestination);
            pilotLog("Event 'offline' from Restorable, removing");
            shouldBeRemoved = true;
        }
    }

    private FlightMissions.Mission mission_dispatchNew() {
        return worldBean.modifySync(world -> {
            final Aircrafts.Aircraft aircraft = ShadowJetLogic.findAvailableOrCreate(
                    world,
                    aircraftType,
                    positionAirportIcao);
            final FlightMissions.Mission mission = FlightMissionHelper.scheduleDispatchedMission(
                    world,
                    aircraft,
                    world.airports().byIcao(plannedDeparture).orElseThrow(),
                    world.airports().byIcao(plannedDestination).orElseThrow(),
                    world.getWorldTime() + Time.HALF_AN_HOUR);
            mission.setModePc(true);

            world.flightMissionControl().startOrCancel(mission);

            return mission;
        });
    }

    private void mission_blocksOff() {
        worldBean.modifySync(world -> {
            final FlightMissions.Mission mission = world.flightMissions().byId(flightMissionId).orElseThrow();

            if (mission.getStatus() == FlightMissions.Status.Preflight) {
                world.flightMissionControl().blocksOff(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            return null;
        });
    }

    private void mission_takeoff() {
        worldBean.modifySync(world -> {
            final FlightMissions.Mission mission = world.flightMissions().byId(flightMissionId).orElseThrow();

            if (mission.getStatus() == FlightMissions.Status.Preflight) {
                world.flightMissionControl().blocksOff(mission);
            }
            if (mission.getStatus() == FlightMissions.Status.Departure) {
                world.flightMissionControl().takeoff(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            return null;
        });
    }

    private void mission_landing() {
        worldBean.modifySync(world -> {
            final FlightMissions.Mission mission = world.flightMissions().byId(flightMissionId).orElseThrow();

            if (mission.getStatus() == FlightMissions.Status.Flying) {
                world.flightMissionControl().landing(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            return null;
        });
    }

    private void mission_blocksOn() {
        worldBean.modifySync(world -> {
            final FlightMissions.Mission mission = world.flightMissions().byId(flightMissionId).orElseThrow();

            if (mission.getStatus() == FlightMissions.Status.Arrival) {
                world.flightMissionControl().blocksOn(mission);
                world.flightMissionControl().finish(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            return null;
        });
    }

    private void mission_cancelBeforeFlightIfExists() {
        worldBean.modifySync(world -> {
            final FlightMissions.Mission mission = world.flightMissions().byId(flightMissionId).orElseThrow();

            if (mission.getStatus() == FlightMissions.Status.Dispatched) {
                world.flightMissionControl().cancelFromPreflight(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            return null;
        });
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

    private void pilotLog(final String message) {
        final String folder1 = (pilotNumber / 100000) + "xxxxx";
        final File folder1file = new File(pilotLogsRoot, folder1);
        final String folder2 = (pilotNumber / 1000) + "xxx";
        final File folder2file = new File(folder1file, folder2);
        final String filename = pilotNumber + ".log";
        final File pilotLogFile = new File(folder2file, filename);

        //noinspection ResultOfMethodCallIgnored
        pilotLogFile.getParentFile().mkdirs();

        final String line = LocalDateTime.now() + " | " + aircraftType + " | " + plannedDeparture + " -> " + plannedDestination + " | " + message + "\r\n";
        try {
            IOHelper.appendFile(pilotLogFile, line);
        } catch (final IOException e) {
            log.warn("unable to write pilot log", e);
        }
    }

    public static void addCsvColumns(final Csv csv) {
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
    }

    public void toCsv(final Csv csv) {
        final int row = csv.addRow();
        csv.set(row, CSV_PILOT_NUMBER, String.valueOf(pilotNumber));
        csv.set(row, CSV_FLIGHT_STAGE, flightStage.name());
        csv.set(row, CSV_PLANNING_STATUS, planningStatus.name());
        csv.set(row, CSV_AIRCRAFT_TYPE, aircraftType);
        csv.set(row, CSV_AIRCRAFT_REG_NO, aircraftRegNo);
        csv.set(row, CSV_PLANNED_DEPARTURE, plannedDeparture);
        csv.set(row, CSV_PLANNED_DESTINATION, plannedDestination);
        csv.set(row, CSV_OVERALL_STATUS, overallStatus.name());
        csv.set(row, CSV_FLIGHT_MISSION_ID, String.valueOf(flightMissionId));
        csv.set(row, CSV_POSITION_IS_ON_GROUND, String.valueOf(positionIsOnGround));
        csv.set(row, CSV_POSITION_AIRPORT_ICAO, positionAirportIcao);
        csv.set(row, CSV_POSITION_LATITUDE, String.valueOf(positionLatitude));
        csv.set(row, CSV_POSITION_LONGITUDE, String.valueOf(positionLongitude));
        csv.set(row, CSV_REMOVAL_COUNTER, String.valueOf(removalCounter));
        csv.set(row, CSV_SHOULD_BE_REMOVED, String.valueOf(shouldBeRemoved));
        csv.set(row, CSV_DISTANCE_LEGS, distanceLegs.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(":")));
    }

    public static PilotContext fromCsv(final WorldRunnerBean worldBean, final Csv csv, final int row) {
        final PilotContext c = new PilotContext(worldBean, Integer.parseInt(csv.value(row, CSV_PILOT_NUMBER)));
        c.flightStage = FlightStage.valueOf(csv.value(row, CSV_FLIGHT_STAGE));
        c.planningStatus = PlanningStatus.valueOf(csv.value(row, CSV_PLANNING_STATUS));
        c.aircraftType = csv.value(row, CSV_AIRCRAFT_TYPE);
        c.aircraftRegNo = csv.value(row, CSV_AIRCRAFT_REG_NO);
        c.plannedDeparture = csv.value(row, CSV_PLANNED_DEPARTURE);
        c.plannedDestination = csv.value(row, CSV_PLANNED_DESTINATION);
        c.overallStatus = OverallStatus.valueOf(csv.value(row, CSV_OVERALL_STATUS));
        c.flightMissionId = Integer.parseInt(csv.value(row, CSV_FLIGHT_MISSION_ID));
        c.positionIsOnGround = Boolean.parseBoolean(csv.value(row, CSV_POSITION_IS_ON_GROUND));
        c.positionAirportIcao = csv.value(row, CSV_POSITION_AIRPORT_ICAO);
        c.positionLatitude = Double.parseDouble(csv.value(row, CSV_POSITION_LATITUDE));
        c.positionLongitude = Double.parseDouble(csv.value(row, CSV_POSITION_LONGITUDE));
        c.removalCounter = Integer.parseInt(csv.value(row, CSV_REMOVAL_COUNTER));
        c.shouldBeRemoved = Boolean.parseBoolean(csv.value(row, CSV_SHOULD_BE_REMOVED));
        c.distanceLegs.addAll(Arrays.stream(csv.value(row, CSV_DISTANCE_LEGS).split(":"))
                .filter(s -> s.length() != 0)
                .map(Float::parseFloat)
                .toList());
        return c;
    }

    private static final String CSV_PILOT_NUMBER = "PilotNumber";
    private static final String CSV_FLIGHT_STAGE = "FlightStage";
    private static final String CSV_PLANNING_STATUS = "PlanningStatus";
    private static final String CSV_AIRCRAFT_TYPE = "AircraftType";
    private static final String CSV_AIRCRAFT_REG_NO = "AircraftRegNo";
    private static final String CSV_PLANNED_DEPARTURE = "PlannedDeparture";
    private static final String CSV_PLANNED_DESTINATION = "PlannedDestination";
    private static final String CSV_OVERALL_STATUS = "OverallStatus";
    private static final String CSV_FLIGHT_MISSION_ID = "FlightMissionId";
    private static final String CSV_POSITION_IS_ON_GROUND = "PositionIsOnGround";
    private static final String CSV_POSITION_AIRPORT_ICAO = "PositionAirportIcao";
    private static final String CSV_POSITION_LATITUDE = "PositionLatitude";
    private static final String CSV_POSITION_LONGITUDE = "PositionLongitude";
    private static final String CSV_REMOVAL_COUNTER = "RemovalCounter";
    private static final String CSV_SHOULD_BE_REMOVED = "ShouldBeRemoved";
    private static final String CSV_DISTANCE_LEGS = "DistanceLegs";
}
