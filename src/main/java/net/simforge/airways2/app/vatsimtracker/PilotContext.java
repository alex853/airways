package net.simforge.airways2.app.vatsimtracker;

import net.simforge.airways2.app.WorldAccess;
import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.world.processors.ShadowJetLogic;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.misc.Geo;
import net.simforge.networkview.core.Position;
import net.simforge.networkview.core.report.ReportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PilotContext {
    private static final Logger log = LoggerFactory.getLogger(PilotContext.class);
    private static final File pilotLogsRoot = new File("./vatsim-tracker/pilot-logs/");
    public static final Set<String> worldIcaos = new TreeSet<>();

    private final WorldAccess worldAccess;
    private final int pilotNumber;
    private FlightStage flightStage;
    private Flightplan flightplan;
    private String aircraftRegNo;
    private int flightMissionId;
    private boolean positionIsOnGround;
    private String positionAirportIcao;
    private double positionLatitude;
    private double positionLongitude;
    private String positionLastSeen;
    private int removalCounter;
    private boolean shouldBeRemoved;
    private final Queue<TrackLeg> trackTail = new LinkedList<>();

    public PilotContext(final WorldAccess worldAccess, final int pilotNumber) {
        this.worldAccess = worldAccess;
        this.pilotNumber = pilotNumber;
        if (worldIcaos.isEmpty()) {
            worldIcaos.addAll(worldAccess.read(world -> world.airports().all().stream().map(Airports.Airport::getIcao).collect(Collectors.toSet())));
        }
    }

    public int getPilotNumber() {
        return pilotNumber;
    }

    public FlightStage getFlightStage() {
        return flightStage;
    }

    public Flightplan getFlightplan() {
        return flightplan;
    }

    public String getAircraftRegNo() {
        return aircraftRegNo;
    }

    public int getFlightMissionId() {
        return flightMissionId;
    }

    public boolean shouldBeRemoved() {
        return shouldBeRemoved;
    }

    public int getRemovalCounter() {
        return removalCounter;
    }

    public Queue<TrackLeg> getTrackTail() {
        return new LinkedList<>(trackTail);
    }

    public Geo.Coords getPositionCoords() {
        return Geo.coords(positionLatitude, positionLongitude);
    }

    public String getPositionLastSeen() {
        return positionLastSeen;
    }

    public void newPilotContextInAirport(final Position position) {
        flightStage = FlightStage.Preflight;
        flightplan = new Flightplan(position);

        if (flightplan.isValid()) {
            copyPositionFields(position, trackTail);
            final FlightMissions.Mission mission = mission_dispatchNewAndStart();
            flightMissionId = mission.getId();

            log.info("{} - Event 'dispatched'", missionLogHead(mission, flightplan));
            pilotLog("Event 'dispatched' == via new flight in airport");
        } else {
            pilotLog("new pilot context in non-valid state");
        }

        copyPositionFields(position, trackTail);
    }

    public void nextReportPosition(final Position newPosition) {
        final Flightplan newFlightplan = new Flightplan(newPosition);

        final boolean takeoff = positionIsOnGround && !newPosition.isOnGround();
        final boolean landing = !positionIsOnGround && newPosition.isOnGround();

        final Queue<TrackLeg> newTrackTail = (positionLatitude != 0 || positionLongitude != 0)
                ? TrackLeg.add(trackTail,
                Geo.distance(Geo.coords(positionLatitude, positionLongitude), newPosition.getCoords()),
                (double) getElapsedSecondsSinceLastSeen(newPosition.getReportInfo().getReport()) / (double) Time.ONE_HOUR)
                : new LinkedList<>();

        final double newTrackTrailDistance = TrackLeg.distance(newTrackTail);
        final TrackTailCriterion trackTailContinued = new TrackTailCriterion(this, newPosition);
        final EllipseCriterion ellipseCriterion = flightplan != null && flightplan.isValid()
                ? new EllipseCriterion(
                        worldAccess.read(world -> world.airports().byIcao(flightplan.getDeparture()).orElseThrow()).getCoords(),
                        worldAccess.read(world -> world.airports().byIcao(flightplan.getDestination()).orElseThrow()).getCoords(),
                        newPosition)
                : null;
        final boolean trackContinued = trackTailContinued.isСontinued() || (ellipseCriterion != null && ellipseCriterion.isWithinEllipse());
        final HugeJumpCriterion hugeJump = new HugeJumpCriterion(this, newPosition);

        if (flightStage == FlightStage.Preflight || flightStage == FlightStage.Departing) {
            if (takeoff) {
                flightStage = FlightStage.Flying;
                if (flightplan != null && flightplan.isValid()) {
                    final FlightMissions.Mission mission = mission_takeoff();
                    log.info("{} - Event 'takeoff'", missionLogHead(mission, flightplan));
                    pilotLog("Event 'takeoff'");
                } else {
                    if (flightMissionId != 0) {
                        log.info("{} - Event 'takeoff' with invalid flightplan, cancelling and removal", missionLogHead(mission_read(), flightplan));
                        pilotLog("Event 'takeoff' with invalid flightplan, cancelling and removal");
                        mission_cancelBeforeTakeoffIfExists();
                    }

                    resetFlightInfo();
                    shouldBeRemoved = true;
                }
            } else {
                if (flightplan != null && !newFlightplan.isSame(flightplan)) {
                    if (flightMissionId != 0) {
                        log.info("{} - Event 'cancelled', new {} differs from existing {}", missionLogHead(mission_read(), flightplan), newFlightplan, flightplan);
                        pilotLog("Event 'cancelled' as new flightplan differs");
                        mission_cancelBeforeTakeoffIfExists();
                    }

                    resetFlightInfo();
                }

                if (newFlightplan.isValid() && flightplan == null) {
                    flightplan = newFlightplan;
                    final FlightMissions.Mission mission = mission_dispatchNewAndStart();
                    flightMissionId = mission.getId();

                    log.info("{} - Event 'dispatched'", missionLogHead(mission, flightplan));
                    pilotLog("Event 'dispatched' == via some correction");
                } else {
                    flightplan = newFlightplan;
                }

                if (flightStage == FlightStage.Preflight
                        && flightplan.isValid()
                        && newTrackTrailDistance > 0.2) { // threshold
                    flightStage = FlightStage.Departing;

                    final FlightMissions.Mission mission = mission_blocksOff();

                    log.info("{} - Event 'blocks-off'", missionLogHead(mission, flightplan));
                    pilotLog("Event 'blocks-off'");
                }
            }
        } else if (flightStage == FlightStage.Flying) {
            if ((!trackContinued && !landing) || hugeJump.isDetected()) {
                final FlightMissions.Mission oldMission = mission_read();
                final Flightplan oldFlightplan = flightplan;
                mission_cancelFromFlying();
                resetFlightInfo();

                log.info("{} - Event 'JUMP IN THE AIR', {}, {}, {}, cancelling and removing", missionLogHead(oldMission, oldFlightplan), trackTailContinued, ellipseCriterion, hugeJump);
                pilotLog("Event 'JUMP IN THE AIR', cancelling and removing");

                shouldBeRemoved = true;
            } else if (landing) {
                if (flightplan.isValidDestinationLocation(newPosition.getAirportIcao())) {
                    flightStage = FlightStage.Arriving;

                    final FlightMissions.Mission mission = mission_landing(newPosition.getAirportIcao());

                    log.info("{} - Event 'landing'", missionLogHead(mission, flightplan));
                    pilotLog("Event 'landing'");
                } else if (worldIcaos.contains(newPosition.getAirportIcao())) {
                    flightStage = FlightStage.Arriving;

                    final FlightMissions.Mission mission = mission_landing(newPosition.getAirportIcao());

                    log.info("{} - Event 'landing' on WRONG airport", missionLogHead(mission, flightplan));
                    pilotLog("Event 'landing' on WRONG airport");
                } else { // landing on airport out of the world
                    final FlightMissions.Mission oldMission = mission_read();
                    final Flightplan oldFlightplan = flightplan;
                    mission_cancelFromFlying(); // todo ak3 improvement is possible here?
                    resetFlightInfo();

                    log.info("{} - Event 'landing' on airport out world, cancelling and removing", missionLogHead(oldMission, oldFlightplan));
                    pilotLog("Event 'landing' on airport out world, cancelling and removing");

                    shouldBeRemoved = true;
                }
            }
        } else if (flightStage == FlightStage.FlyingOffline) {
            if (!landing && trackContinued) { // pilot is back online and is continuing the flying roughly the same track
                flightStage = FlightStage.Flying;
                final FlightMissions.Mission mission = mission_read();

                log.info("{} - Event 'back to flying online'! {}, {}", missionLogHead(mission, flightplan), trackTailContinued, ellipseCriterion);
                pilotLog("Event 'back to flying online'");
            } // todo ak0 what if landing? or track discontinued?
        } else if (flightStage == FlightStage.Arriving) {
            if (newTrackTrailDistance < 0.3) {
                flightStage = FlightStage.Arrived;

                final FlightMissions.Mission mission = mission_blocksOnAndFinish();

                log.info("{} - Event 'blocks-on'", missionLogHead(mission, flightplan));
                pilotLog("Event 'blocks-on'");

                removalCounter = 3; // it will stay Arrived for 3 reports and then will be removed
            }

            if (newFlightplan.isValid() && !newFlightplan.isSame(flightplan)) {
                flightplan = newFlightplan;
                final FlightMissions.Mission mission = mission_dispatchNewAndStart();
                flightMissionId = mission.getId();

                log.info("{} - Event 'dispatched'", missionLogHead(mission, flightplan));
                pilotLog("Event 'dispatched' == via end of flight");
            }
        } else if (flightStage == FlightStage.Arrived) {
            if (removalCounter == 0) {
                final FlightMissions.Mission oldMission = mission_read();
                final Flightplan oldFlightplan = flightplan;
                resetFlightInfo();

                log.error("{} - Event 'completed' for Arrived flight, switching to Preflight for next flight", missionLogHead(oldMission, oldFlightplan));
                pilotLog("Event 'completed' for Arrived flight, switching to Preflight for next flight");

                flightStage = FlightStage.Preflight;
            } else {
                removalCounter--;
            }

            if (newFlightplan.isValid() && !newFlightplan.isSame(flightplan)) {
                flightplan = newFlightplan;
                final FlightMissions.Mission mission = mission_dispatchNewAndStart();
                flightMissionId = mission.getId();

                log.info("{} - Event 'dispatched'", missionLogHead(mission, flightplan));
                pilotLog("Event 'dispatched' == via end of flight");
            }
        } else {
            throw new IllegalStateException();
        }

        copyPositionFields(newPosition, newTrackTail);
    }

    public void noPositionInReport(final String report) {
        if (flightStage == FlightStage.Preflight || flightStage == FlightStage.Departing) {
            if (flightMissionId != 0) {
                log.info("{} - Event 'OFFLINE' on {} stage, cancelling and removing", missionLogHead(mission_read(), flightplan), flightStage);
                pilotLog("Event 'offline' on " + flightStage + " stage, cancelling and removing");
                mission_cancelBeforeTakeoffIfExists();
            }
            resetFlightInfo();
            shouldBeRemoved = true;
        } else if (flightStage == FlightStage.Flying) {
            log.info("{} - Event 'OFFLINE' on Flying stage, grace period started", missionLogHead(mission_read(), flightplan));
            pilotLog("Event 'offline' on Flying stage, grace period started");
            flightStage = FlightStage.FlyingOffline;
        } else if (flightStage == FlightStage.FlyingOffline) {
            if (getElapsedSecondsSinceLastSeen(report) > 10 * Time.ONE_MINUTE) {
                final FlightMissions.Mission oldMission = mission_read();
                final Flightplan oldFlightplan = flightplan;
                mission_cancelFromFlying(); // todo ak3 improvement is possible here - if aircraft is close to destination then finish flight however make a fine to a pilot
                resetFlightInfo();

                log.info("{} - Event 'CANCEL' on FlyingOffline stage, cancelling and removing", missionLogHead(oldMission, oldFlightplan));
                pilotLog("Event 'cancel' from AllGood on FlyingOffline stage, cancelling and removing");
                shouldBeRemoved = true;
            } else {
                final FlightMissions.Mission mission = mission_read();
                log.info("{} - Event 'still offline' from AllGood and on FlyingOffline stage, ", missionLogHead(mission, flightplan));
                pilotLog("Event 'still offline' from AllGood on FlyingOffline stage, cancelling and removing");
            }
        } else if (flightStage == FlightStage.Arriving) {
            final FlightMissions.Mission oldMission = mission_read();
            final Flightplan oldFlightplan = flightplan;
            mission_blocksOnAndFinish();
            resetFlightInfo();

            log.info("{} - Event 'blocks-on' due to pilot went offline", missionLogHead(oldMission, oldFlightplan));
            pilotLog("Event 'blocks-on' due to pilot went offline, finishing and removing");
            shouldBeRemoved = true;
        } else if (flightStage == FlightStage.Arrived) {
            final FlightMissions.Mission oldMission = mission_read();
            final Flightplan oldFlightplan = flightplan;
            resetFlightInfo();

            log.error("{} - Event 'OFFLINE' from AllGood, flight stage Arrived", missionLogHead(oldMission, oldFlightplan));
            pilotLog("Event 'offline' from AllGood, Arrived stage, removing");
            shouldBeRemoved = true;
        } else {
            throw new IllegalStateException();
        }
    }

    private void resetFlightInfo() {
        flightMissionId = 0;
        flightplan = null;
        trackTail.clear();
    }

    public long getElapsedSecondsSinceLastSeen(String report) {
        return Duration.between(ReportUtils.fromTimestampJava(positionLastSeen), ReportUtils.fromTimestampJava(report)).getSeconds();
    }

    private FlightMissions.Mission mission_read() {
        return flightMissionId != 0
                ? worldAccess.read(world -> world.flightMissions().byId(flightMissionId).orElseThrow())
                : null;
    }

    private FlightMissions.Mission mission_dispatchNewAndStart() {
        return worldAccess.modifySync(world -> {
            final Optional<AircraftTypes.AircraftType> requestedAircraftType = world.aircraftTypes().byIcao(flightplan.getAircraftType());
            if (requestedAircraftType.isEmpty()) {
                FlightStats.event("missingAircraftType " + flightplan.getAircraftType());
            }
            final AircraftTypes.AircraftType aircraftType = requestedAircraftType.orElseGet(() -> world.aircraftTypes().byIcao("A320").orElseThrow());
            final Airports.Airport positionAirport = world.airports().byIcao(flightplan.getFiledAt()).orElseThrow(elseThrowException(flightplan.getFiledAt()));

            final Aircrafts.Aircraft aircraft = ShadowJetLogic.findAvailableOrCreate(
                    world,
                    aircraftType,
                    positionAirport);
            final FlightMissions.Mission mission = FlightMissionHelper.scheduleDispatchedMission(
                    world,
                    aircraft,
                    world.airports().byIcao(flightplan.getDeparture()).orElseThrow(elseThrowException(flightplan.getDeparture())),
                    world.airports().byIcao(flightplan.getDestination()).orElseThrow(elseThrowException(flightplan.getDestination())),
                    world.getWorldTime() + Time.HALF_AN_HOUR);
            mission.setModePc(true);

            world.flightMissionControl().startOrCancel(mission);

            FlightStats.event("dispatchNewAndStart");

            return mission;
        });
    }

    private FlightMissions.Mission mission_blocksOff() {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.warn("erroneous case, f/m == 0, in mission_blocksOff, need to investigate <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                return null; // todo ak1 erroneous case, need to investigate
            }
            final FlightMissions.Mission mission = mission1.get();

            if (mission.getStatus() == FlightMissions.Status.Preflight) {
                world.flightMissionControl().blocksOff(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event("blocksOff");

            return mission;
        });
    }

    private FlightMissions.Mission mission_takeoff() {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.warn("erroneous case, f/m == 0, in mission_takeoff, need to investigate <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                return null; // todo ak1 erroneous case, need to investigate
            }
            final FlightMissions.Mission mission = mission1.get();

            if (mission.getStatus() == FlightMissions.Status.Preflight) {
                world.flightMissionControl().blocksOff(mission);
            }
            if (mission.getStatus() == FlightMissions.Status.Departure) {
                world.flightMissionControl().takeoff(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event("takeoff");

            return mission;
        });
    }

    private FlightMissions.Mission mission_landing(final String landingAirportIcao) {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.warn("erroneous case, f/m == 0, in mission_landing, need to investigate <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                return null; // todo ak1 erroneous case, need to investigate
            }
            final FlightMissions.Mission mission = mission1.get();

            final Airports.Airport landingAirport = world.airports().byIcao(landingAirportIcao).orElseThrow();

            if (mission.getStatus() == FlightMissions.Status.Flying) {
                world.flightMissionControl().landing(mission, landingAirport);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event("landing");

            return mission;
        });
    }

    private FlightMissions.Mission mission_blocksOnAndFinish() {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.warn("erroneous case, f/m == 0, in v, need to investigate <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                return null; // todo ak1 erroneous case, need to investigate
            }
            final FlightMissions.Mission mission = mission1.get();

            if (mission.getStatus() == FlightMissions.Status.Arrival) {
                world.flightMissionControl().blocksOn(mission);
                world.flightMissionControl().finish(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event("blocksOnAndFinish");

            return mission;
        });
    }

    private void mission_cancelBeforeTakeoffIfExists() {
        worldAccess.modifySync(world -> {
            if (flightMissionId == 0) {
                return null;
            }

            final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flightMissionId);
            if (mission.isEmpty()) {
                log.warn("erroneous case, f/m not found, in mission_cancelBeforeTakeoffIfExists, need to investigate <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                return null; // todo ak1 erroneous case, need to investigate
            }

            if (mission.get().getStatus() == FlightMissions.Status.Preflight
                    || mission.get().getStatus() == FlightMissions.Status.Departure) {
                world.flightMissionControl().cancelFlightAndReturnAircraftToDepartureAirport(mission.get());
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.get().getStatus());
            }

            FlightStats.event("cancelBeforeTakeoffIfExists");

            return null;
        });
    }

    private void mission_cancelFromFlying() {
        worldAccess.modifySync(world -> {
            if (flightMissionId == 0) {
                log.warn("erroneous case, f/m == 0, in mission_cancelFromFlying, need to rethink <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                return null; // todo ak3 erroneous case, need to rethink
            }

            final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flightMissionId);
            if (mission.isEmpty()) {
                log.warn("erroneous case, f/m not found, in mission_cancelFromFlying, need to rethink <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                return null; // todo ak3 erroneous case, need to rethink
            }

            if (mission.get().getStatus() == FlightMissions.Status.Flying) {
                world.flightMissionControl().cancelFlightAndReturnAircraftToDepartureAirport(mission.get());
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.get().getStatus());
            }

            FlightStats.event("cancelFromFlying");

            return null;
        });
    }

    private void copyPositionFields(final Position position, final Queue<TrackLeg> trackTail) {
        positionLastSeen = position.getReportInfo().getReport();
        positionIsOnGround = position.isOnGround();
        positionAirportIcao = position.isInAirport() ? position.getAirportIcao() : null;
        positionLatitude = position.getCoords().getLat();
        positionLongitude = position.getCoords().getLon();
        aircraftRegNo = position.getRegNo();
        this.trackTail.clear();
        this.trackTail.addAll(trackTail);
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

        final String line = LocalDateTime.now() + " | " + (flightplan != null
                ? flightplan.getAircraftType() + " | " + flightplan.getDeparture() + " -> " + flightplan.getDestination()
                : "no flightplan") + " | " + message + "\r\n";
        try {
            IOHelper.appendFile(pilotLogFile, line);
        } catch (final IOException e) {
            log.warn("unable to write pilot log", e);
        }
    }

    private String missionLogHead(final FlightMissions.Mission mission, final Flightplan flightplan) {
        return String.format("[%s] f/m %s, a/c %s : %s -> %s",
                pilotNumber,
                mission != null ? "#" + mission.getId() : "-",
                mission != null ? "#" + mission.getAircraftId() : "-",
                flightplan != null ? flightplan.getDeparture() : "????",
                flightplan != null ? flightplan.getDestination() : "????");
    }

    private static Supplier<RuntimeException> elseThrowException(final String what) {
        return () -> new IllegalArgumentException("Can't find by '" + what + "'");
    }

    public static void addCsvColumns(final Csv csv) {
        csv.addColumn(CSV_PILOT_NUMBER);
        csv.addColumn(CSV_FLIGHT_STAGE);
        csv.addColumn(CSV_PLAN_FILED_AT);
        csv.addColumn(CSV_AIRCRAFT_TYPE);
        csv.addColumn(CSV_AIRCRAFT_REG_NO);
        csv.addColumn(CSV_PLANNED_DEPARTURE);
        csv.addColumn(CSV_PLANNED_DESTINATION);
        csv.addColumn(CSV_FLIGHT_MISSION_ID);
        csv.addColumn(CSV_POSITION_LAST_SEEN);
        csv.addColumn(CSV_POSITION_IS_ON_GROUND);
        csv.addColumn(CSV_POSITION_AIRPORT_ICAO);
        csv.addColumn(CSV_POSITION_LATITUDE);
        csv.addColumn(CSV_POSITION_LONGITUDE);
        csv.addColumn(CSV_REMOVAL_COUNTER);
        csv.addColumn(CSV_SHOULD_BE_REMOVED);
        csv.addColumn(CSV_TRACK_TAIL);
    }

    public void toCsv(final Csv csv) {
        final int row = csv.addRow();
        csv.set(row, CSV_PILOT_NUMBER, String.valueOf(pilotNumber));
        csv.set(row, CSV_FLIGHT_STAGE, flightStage.name());
        csv.set(row, CSV_PLAN_FILED_AT, flightplan != null ? flightplan.getFiledAt() : null);
        csv.set(row, CSV_AIRCRAFT_TYPE, flightplan != null ? flightplan.getAircraftType() : null);
        csv.set(row, CSV_AIRCRAFT_REG_NO, aircraftRegNo);
        csv.set(row, CSV_PLANNED_DEPARTURE, flightplan != null ? flightplan.getDeparture() : null);
        csv.set(row, CSV_PLANNED_DESTINATION, flightplan != null ? flightplan.getDestination() : null);
        csv.set(row, CSV_FLIGHT_MISSION_ID, String.valueOf(flightMissionId));
        csv.set(row, CSV_POSITION_LAST_SEEN, positionLastSeen);
        csv.set(row, CSV_POSITION_IS_ON_GROUND, String.valueOf(positionIsOnGround));
        csv.set(row, CSV_POSITION_AIRPORT_ICAO, positionAirportIcao);
        csv.set(row, CSV_POSITION_LATITUDE, String.valueOf(positionLatitude));
        csv.set(row, CSV_POSITION_LONGITUDE, String.valueOf(positionLongitude));
        csv.set(row, CSV_REMOVAL_COUNTER, String.valueOf(removalCounter));
        csv.set(row, CSV_SHOULD_BE_REMOVED, String.valueOf(shouldBeRemoved));
        csv.set(row, CSV_TRACK_TAIL, trackTail.stream()
                .map(TrackLeg::toString)
                .collect(Collectors.joining(":")));
    }

    public static PilotContext fromCsv(final WorldRunnerBean worldBean, final Csv csv, final int row) {
        final PilotContext c = new PilotContext(worldBean, Integer.parseInt(csv.value(row, CSV_PILOT_NUMBER)));
        c.flightStage = FlightStage.valueOf(csv.value(row, CSV_FLIGHT_STAGE));
        final String filedAt = csv.value(row, CSV_PLAN_FILED_AT);
        final String aircraftType = csv.value(row, CSV_AIRCRAFT_TYPE);
        c.aircraftRegNo = csv.value(row, CSV_AIRCRAFT_REG_NO);
        final String plannedDeparture = csv.value(row, CSV_PLANNED_DEPARTURE);
        final String plannedDestination = csv.value(row, CSV_PLANNED_DESTINATION);
        if (filedAt != null && aircraftType != null) {
            c.flightplan = new Flightplan(filedAt, aircraftType, plannedDeparture, plannedDestination);
        }
        c.flightMissionId = Integer.parseInt(csv.value(row, CSV_FLIGHT_MISSION_ID));
        c.positionLastSeen = csv.value(row, CSV_POSITION_LAST_SEEN);
        c.positionIsOnGround = Boolean.parseBoolean(csv.value(row, CSV_POSITION_IS_ON_GROUND));
        c.positionAirportIcao = csv.value(row, CSV_POSITION_AIRPORT_ICAO);
        c.positionLatitude = Double.parseDouble(csv.value(row, CSV_POSITION_LATITUDE));
        c.positionLongitude = Double.parseDouble(csv.value(row, CSV_POSITION_LONGITUDE));
        c.removalCounter = Integer.parseInt(csv.value(row, CSV_REMOVAL_COUNTER));
        c.shouldBeRemoved = Boolean.parseBoolean(csv.value(row, CSV_SHOULD_BE_REMOVED));
        c.trackTail.addAll(Arrays.stream(csv.value(row, CSV_TRACK_TAIL).split(":"))
                .filter(s -> s.length() != 0)
                .map(TrackLeg::fromString)
                .toList());
        return c;
    }

    private static final String CSV_PILOT_NUMBER = "PilotNumber";
    private static final String CSV_FLIGHT_STAGE = "FlightStage";
    private static final String CSV_PLAN_FILED_AT = "PlanFiledAt";
    private static final String CSV_AIRCRAFT_TYPE = "AircraftType";
    private static final String CSV_AIRCRAFT_REG_NO = "AircraftRegNo";
    private static final String CSV_PLANNED_DEPARTURE = "PlannedDeparture";
    private static final String CSV_PLANNED_DESTINATION = "PlannedDestination";
    private static final String CSV_FLIGHT_MISSION_ID = "FlightMissionId";
    private static final String CSV_POSITION_LAST_SEEN = "PositionLastSeen";
    private static final String CSV_POSITION_IS_ON_GROUND = "PositionIsOnGround";
    private static final String CSV_POSITION_AIRPORT_ICAO = "PositionAirportIcao";
    private static final String CSV_POSITION_LATITUDE = "PositionLatitude";
    private static final String CSV_POSITION_LONGITUDE = "PositionLongitude";
    private static final String CSV_REMOVAL_COUNTER = "RemovalCounter";
    private static final String CSV_SHOULD_BE_REMOVED = "ShouldBeRemoved";
    private static final String CSV_TRACK_TAIL = "TrackTail";
}
