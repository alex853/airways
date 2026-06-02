package net.simforge.airways2.app.vatsimtracker;

import net.simforge.airways2.world.WorldAccess;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.pilottracker.track.TrackLeg;
import net.simforge.airways2.world.AircraftTypeRemapping;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.world.processors.ShadowJetLogic;
import net.simforge.commons.io.Csv;
import net.simforge.commons.misc.Geo;
import net.simforge.networkview.core.Position;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.refdata.aircrafts.apd.AircraftPerformanceDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PilotContext {
    private static final Logger log = LoggerFactory.getLogger(PilotContext.class);
    @SuppressWarnings("unused")
    private static final File pilotLogsRoot = new File("./vatsim-tracker/pilot-logs/");
    public static final Set<String> worldIcaos = new TreeSet<>();

    private static final int MAX_ALLOWED_OFFLINE_TIME_MINUTES = 60;

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
            worldIcaos.addAll(worldAccess.read(world -> world.airports().all().map(Airports.Airport::getIcao).collect(Collectors.toSet())));
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

    @SuppressWarnings("unused")
    public String getPositionLastSeen() {
        return positionLastSeen;
    }

    public void newPilotContextInAirport(final Position position) {
        flightplan = new Flightplan(position);
        flightStage = FlightStage.Preflight;

        if (flightplan.isValid()) {
            copyPositionFields(position, trackTail);
            final FlightMissions.Mission mission = mission_dispatchNewAndStart();
            flightMissionId = mission.getId();

            log.info("{} - Event 'dispatched'", missionLogHead(mission, flightplan));
            pilotLog("Event 'dispatched' == via new flight in airport");
            FlightStats.event("vatsim - new context - dispatched");
        } else {
            pilotLog("new pilot context in non-valid state");
            FlightStats.event("vatsim - new context - flightplan invalid");
            countDestinationIfMissing(flightplan);
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

        final double newTrackTailDistance = TrackLeg.distance(newTrackTail);
        final TrackTailCriterion trackTailContinued = new TrackTailCriterion(this, newPosition);
        final EllipseCriterion ellipseCriterion = flightplan != null && flightplan.isValid()
                ? new EllipseCriterion(
                        worldAccess.read(world -> world.airports().byIcao(flightplan.getDeparture()).orElseThrow()).getCoords(),
                        worldAccess.read(world -> world.airports().byIcao(flightplan.getDestination()).orElseThrow()).getCoords(),
                        newPosition)
                : null;
        final boolean trackContinued = trackTailContinued.isContinued() || (ellipseCriterion != null && ellipseCriterion.isWithinEllipse());
        final HugeJumpCriterion hugeJump = new HugeJumpCriterion(this, newPosition);

        if (flightStage == FlightStage.Preflight || flightStage == FlightStage.Departing) {
            if (takeoff) {
                flightStage = FlightStage.Flying;
                if (flightplan != null && flightplan.isValid()) {
                    final FlightMissions.Mission mission = mission_takeoff();
                    log.info("{} - Event 'takeoff'", missionLogHead(mission, flightplan));
                    pilotLog("Event 'takeoff'");
                    FlightStats.event("vatsim - preflight - takeoff with valid flightplan");
                } else {
                    if (flightMissionId != 0) {
                        log.info("{} - Event 'takeoff' with invalid flightplan, cancelling and removal", missionLogHead(mission_read(), flightplan));
                        pilotLog("Event 'takeoff' with invalid flightplan, cancelling and removal");
                        mission_cancelBeforeTakeoffIfExists();
                        FlightStats.event("vatsim - preflight - takeoff with invalid flightplan - fm cancelled");
                    } else {
                        FlightStats.event("vatsim - preflight - takeoff with invalid flightplan - no fm found!");
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
                        FlightStats.event("vatsim - preflight - flightplan changed - fm cancelled");
                    } else {
                        FlightStats.event("vatsim - preflight - flightplan changed - no fm found!");
                    }

                    resetFlightInfo();
                }

                if (newFlightplan.isValid() && flightplan == null) {
                    flightplan = newFlightplan;
                    final FlightMissions.Mission mission = mission_dispatchNewAndStart();
                    flightMissionId = mission.getId();

                    log.info("{} - Event 'dispatched'", missionLogHead(mission, flightplan));
                    pilotLog("Event 'dispatched' == via some correction");
                    FlightStats.event("vatsim - preflight - dispatched - new valid flightplan");
                } else {
                    flightplan = newFlightplan;
                    countDestinationIfMissing(flightplan);
                }

                if (flightStage == FlightStage.Preflight
                        && flightplan.isValid()
                        && newTrackTailDistance > 0.2) { // threshold
                    flightStage = FlightStage.Departing;

                    final FlightMissions.Mission mission = mission_blocksOff();

                    log.info("{} - Event 'blocks-off'", missionLogHead(mission, flightplan));
                    pilotLog("Event 'blocks-off'");
                    FlightStats.event("vatsim - preflight - blocks-off with valid flightplan");
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
                FlightStats.event("vatsim - flying - discontinuity-or-jump - fm cancelled");

                shouldBeRemoved = true;
            } else if (landing) {
                landingFromFlyingStage(newPosition);
            } else {
                mission_updateAircraftCoords(newPosition);
            }
        } else if (flightStage == FlightStage.FlyingOffline) {
            final FlightMissions.Mission mission = mission_read();
            if (!landing && trackContinued) { // pilot is back online and is continuing the flying roughly the same track
                flightStage = FlightStage.Flying;

                log.info("{} - Event 'back to flying online'! {}, {}", missionLogHead(mission, flightplan), trackTailContinued, ellipseCriterion);
                pilotLog("Event 'back to flying online'");
                FlightStats.event("vatsim - flying-offline - back online successfully");

                final long minutesOffline = getElapsedSecondsSinceLastSeen(newPosition.getReportInfo().getReport()) / Time.ONE_MINUTE;
                final long range = ((minutesOffline / 10) + 1) * 10;
                FlightStats.event("vatsim - flying-offline - duration " + range);

                mission_updateAircraftCoords(newPosition);
            } else if (!landing) { // still flying and track discontinued
                final Flightplan flightplanCopy = flightplan;
                mission_cancelFromFlying();
                resetFlightInfo();

                log.warn("{} - Event 'back to flying' HOWEVER track discontinued, {}, {}, cancelling and removing", missionLogHead(mission, flightplanCopy), trackTailContinued, ellipseCriterion);
                pilotLog("Event 'back to flying' HOWEVER track discontinued, cancelling and removing");
                FlightStats.event("vatsim - flying-offline - track discontinued, fm cancelled");

                shouldBeRemoved = true;
            } else { // landing
                flightStage = FlightStage.Flying;

                log.warn("{} - Event 'back to flying AND LANDING at the same time', {}, {}", missionLogHead(mission, flightplan), trackTailContinued, ellipseCriterion);
                pilotLog("Event 'back to flying AND LANDING at the same time'");
                FlightStats.event("vatsim - flying-offline - online and land successfully");

                landingFromFlyingStage(newPosition);
            }
        } else if (flightStage == FlightStage.Arriving) {
            final boolean newFlightMissionDueToNewFlightplan = newFlightplan.isValid() && !newFlightplan.isSame(flightplan);
            if (newTrackTailDistance < 0.3 || newFlightMissionDueToNewFlightplan) {
                flightStage = FlightStage.Arrived;

                final FlightMissions.Mission mission = mission_blocksOnAndFinish();

                log.info("{} - Event 'blocks-on'", missionLogHead(mission, flightplan));
                pilotLog("Event 'blocks-on'");
                FlightStats.event("vatsim - arriving - blocks-on and finish");

                removalCounter = 5; // it will stay Arrived for some time
            }

            if (newFlightMissionDueToNewFlightplan) {
                resetFlightInfo();

                flightplan = newFlightplan;
                flightStage = FlightStage.Preflight;
                final FlightMissions.Mission mission = mission_dispatchNewAndStart();
                flightMissionId = mission.getId();

                log.info("{} - Event 'dispatched' == via end of Arriving flight", missionLogHead(mission, flightplan));
                pilotLog("Event 'dispatched' == via end of Arriving flight");
                FlightStats.event("vatsim - arriving - dispatched");
            }
        } else if (flightStage == FlightStage.Arrived) {
            final boolean newFlightMissionDueToNewFlightplan = newFlightplan.isValid() && !newFlightplan.isSame(flightplan);
            if (removalCounter == 0 || newFlightMissionDueToNewFlightplan) {
                final FlightMissions.Mission oldMission = mission_read();
                final Flightplan oldFlightplan = flightplan;

                resetFlightInfo(); // flight mission has been finished in Arriving section

                flightplan = newFlightplan;
                flightStage = FlightStage.Preflight;

                log.info("{} - Event 'completed' for Arrived flight, switching to Preflight for next flight", missionLogHead(oldMission, oldFlightplan));
                pilotLog("Event 'completed' for Arrived flight, switching to Preflight for next flight");
                FlightStats.event("vatsim - arrived - completed");
            } else {
                removalCounter--;
            }

            if (newFlightMissionDueToNewFlightplan) {
                resetFlightInfo();

                flightplan = newFlightplan;
                flightStage = FlightStage.Preflight;
                final FlightMissions.Mission mission = mission_dispatchNewAndStart();
                flightMissionId = mission.getId();

                log.info("{} - Event 'dispatched' == via end of Arrived flight", missionLogHead(mission, flightplan));
                pilotLog("Event 'dispatched' == via end of Arrived flight");
                FlightStats.event("vatsim - arrived - dispatched");
            }
        } else {
            throw new IllegalStateException();
        }

        copyPositionFields(newPosition, newTrackTail);
    }

    private void landingFromFlyingStage(Position newPosition) {
        final String landingAirportIcao = newPosition.getAirportIcao();
        if (landingAirportIcao == null) {
            final FlightMissions.Mission oldMission = mission_read();
            final Flightplan oldFlightplan = flightplan;
            mission_cancelFromFlying();
            resetFlightInfo();

            log.warn("{} - Event 'landing' on NULL airport, cancelling and removing", missionLogHead(oldMission, oldFlightplan));
            pilotLog("Event 'landing' on NULL airport, cancelling and removing");
            FlightStats.event("vatsim - landing - null airport");

            shouldBeRemoved = true;
        } else if (flightplan.isValidDestinationLocation(landingAirportIcao)) {
            flightStage = FlightStage.Arriving;

            final FlightMissions.Mission mission = mission_landing(newPosition.getAirportIcao());

            log.info("{} - Event 'landing' at planned destination airport", missionLogHead(mission, flightplan));
            pilotLog("Event 'landing' at planned destination airport");
            FlightStats.event("vatsim - landing - planned airport");
            // this stat data is not needed so far FlightStats.event("vatsim - route " + flightplanToRoute(flightplan));
        } else if (worldIcaos.contains(landingAirportIcao)) {
            flightStage = FlightStage.Arriving;

            final FlightMissions.Mission mission = mission_landing(newPosition.getAirportIcao());

            log.warn("{} - Event 'landing' on WRONG airport {}", missionLogHead(mission, flightplan), landingAirportIcao);
            pilotLog("Event 'landing' on WRONG airport " + landingAirportIcao);
            FlightStats.event("vatsim - landing - wrong airport");
        } else { // landing on airport out of the world
            final FlightMissions.Mission oldMission = mission_read();
            final Flightplan oldFlightplan = flightplan;
            mission_cancelFromFlying();
            resetFlightInfo();

            log.warn("{} - Event 'landing' on airport {} out of the world, cancelling and removing", missionLogHead(oldMission, oldFlightplan), landingAirportIcao);
            pilotLog("Event 'landing' on airport " + landingAirportIcao + " out of the world, cancelling and removing");
            FlightStats.event("vatsim - landing - out of the world");

            shouldBeRemoved = true;
        }
    }

    public void noPositionInReport(final String report) {
        if (flightStage == FlightStage.Preflight || flightStage == FlightStage.Departing) {
            if (flightMissionId != 0) {
                log.info("{} - Event 'OFFLINE' on {} stage, cancelling and removing", missionLogHead(mission_read(), flightplan), flightStage);
                pilotLog("Event 'offline' on " + flightStage + " stage, cancelling and removing");
                mission_cancelBeforeTakeoffIfExists();
                FlightStats.event("vatsim - preflight - offline - fm cancelled");
            } else {
                FlightStats.event("vatsim - preflight - offline - no fm found!");
            }
            resetFlightInfo();
            shouldBeRemoved = true;
        } else if (flightStage == FlightStage.Flying) {
            log.info("{} - Event 'OFFLINE' on Flying stage, grace period started", missionLogHead(mission_read(), flightplan));
            pilotLog("Event 'offline' on Flying stage, grace period started");
            flightStage = FlightStage.FlyingOffline;
            FlightStats.event("vatsim - flying - pilot went offline while flying");
        } else if (flightStage == FlightStage.FlyingOffline) {
            final long minutesOffline = getElapsedSecondsSinceLastSeen(report) / Time.ONE_MINUTE;
            if (minutesOffline > MAX_ALLOWED_OFFLINE_TIME_MINUTES) {
                final FlightMissions.Mission oldMission = mission_read();
                final Flightplan oldFlightplan = flightplan;
                mission_cancelFromFlying(); // todo ak3 improvement is possible here - if aircraft is close to destination then finish flight however make a fine to a pilot
                resetFlightInfo();

                log.info("{} - Event 'CANCEL' on FlyingOffline stage, offline for {} mins, cancelling and removing", missionLogHead(oldMission, oldFlightplan), minutesOffline);
                pilotLog("Event 'cancel' on FlyingOffline stage, offline for " + minutesOffline + " mins, cancelling and removing");
                FlightStats.event("vatsim - flying-offline - allowed offline period exceeded - fm cancelled");
                shouldBeRemoved = true;
            } else {
                final FlightMissions.Mission mission = mission_read();
                log.info("{} - Event 'still offline' on FlyingOffline stage, offline for {} mins, waiting", missionLogHead(mission, flightplan), minutesOffline);
                pilotLog("Event 'still offline' on FlyingOffline stage, offine for " + minutesOffline + " mins, waiting");
            }
        } else if (flightStage == FlightStage.Arriving) {
            final FlightMissions.Mission oldMission = mission_read();
            final Flightplan oldFlightplan = flightplan;
            mission_blocksOnAndFinish();
            resetFlightInfo();

            log.info("{} - Event 'blocks-on' due to pilot went offline", missionLogHead(oldMission, oldFlightplan));
            pilotLog("Event 'blocks-on' due to pilot went offline, finishing and removing");
            FlightStats.event("vatsim - arriving - blocks-on and finish as pilot went offline");
            shouldBeRemoved = true;
        } else if (flightStage == FlightStage.Arrived) {
            final FlightMissions.Mission oldMission = mission_read();
            final Flightplan oldFlightplan = flightplan;
            resetFlightInfo();

            log.info("{} - Event 'OFFLINE' for Arrived flight", missionLogHead(oldMission, oldFlightplan));
            pilotLog("Event 'offline' for Arrived flight, removing");
            FlightStats.event("vatsim - arrived - completed as pilot went offline");
            shouldBeRemoved = true;
        } else {
            throw new IllegalStateException();
        }
    }

    private void resetFlightInfo() {
        flightMissionId = 0;
        flightplan = null;
        trackTail.clear();
        removalCounter = 0;
    }

    private void countDestinationIfMissing(final Flightplan flightplan) {
        if (flightplan == null) {
            return;
        }
        if (flightplan.getDestination() == null) {
            return;
        }
        if (worldIcaos.contains(flightplan.getDestination())) {
            return;
        }
        FlightStats.event("vatsim - missingAirport " + flightplan.getDestination());
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
            String filedAircraftTypeCode = flightplan.getAircraftType();
            String requestedAircraftTypeCode = AircraftTypeRemapping.remap(filedAircraftTypeCode);
            if (!requestedAircraftTypeCode.equals(filedAircraftTypeCode)) {
                FlightStats.event("aircraft type remapped " + filedAircraftTypeCode);
            }

            Optional<AircraftTypes.AircraftType> requestedAircraftType = world.aircraftTypes().byIcao(requestedAircraftTypeCode);
            if (requestedAircraftType.isEmpty()) {
                FlightStats.event("aircraft type missing " + requestedAircraftTypeCode);
            }

            if (AircraftPerformanceDatabase.getPerformance(requestedAircraftTypeCode).isEmpty()) {
                FlightStats.event("aircraft type performance missing " + requestedAircraftTypeCode);
            }

            final AircraftTypes.AircraftType aircraftType = requestedAircraftType.orElseGet(() -> world.aircraftTypes().byIcao("A320").orElseThrow());
            final Airports.Airport positionAirport = world.airports().byIcao(flightplan.getFiledAt()).orElseThrow(elseThrowException(flightplan.getFiledAt()));

            final Aircrafts.Aircraft aircraft = ShadowJetLogic.findAvailableAircraftOrCreateNew(
                    world,
                    aircraftType,
                    positionAirport);
            final FlightMissions.Mission mission = FlightMissionHelper.scheduleDispatchedMission(
                    world,
                    aircraft,
                    world.airports().byIcao(flightplan.getDeparture()).orElseThrow(elseThrowException(flightplan.getDeparture())),
                    world.airports().byIcao(flightplan.getDestination()).orElseThrow(elseThrowException(flightplan.getDestination())),
                    world.getWorldTime() + Time.HALF_AN_HOUR);
            mission.setCharacterMode(FlightMissions.CharacterMode.PC);

            world.flightMissionControl().startOrCancel(mission);

            ShadowJetLogic.provideTransportFlightIfRequired(world, mission);

            FlightStats.event("vatsim - dispatchNewAndStart");

            return mission;
        });
    }

    private FlightMissions.Mission mission_blocksOff() {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_blocksOff <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("vatsim - erroneous case - mission_blocksOff - fm not found");
                return null;
            }
            final FlightMissions.Mission mission = mission1.get();

            if (mission.getStatus() == FlightMissions.Status.Preflight) {
                world.flightMissionControl().blocksOff(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event("vatsim - blocksOff");

            return mission;
        });
    }

    private FlightMissions.Mission mission_takeoff() {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_takeoff <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("vatsim - erroneous case - mission_takeoff - fm not found");
                return null;
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

            FlightStats.event("vatsim - takeoff");

            return mission;
        });
    }

    private void mission_updateAircraftCoords(Position position) {
        worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_updateAircraftCoords <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("vatsim - erroneous case - mission_updateAircraftCoords - fm not found");
                return null;
            }
            FlightMissions.Mission mission = mission1.get();

            if (mission.getStatus() != FlightMissions.Status.Flying) {
                log.error("erroneous case, f/m not in Flying state, in mission_updateAircraftCoords <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("vatsim - erroneous case - mission_updateAircraftCoords - fm not in Flying state");
                return null;
            }

            Optional<Aircrafts.Aircraft> aircraft1 = world.aircrafts().byId(mission.getAircraftId());
            if (aircraft1.isEmpty()) {
                log.error("erroneous case, aircraft not found, in mission_updateAircraftCoords <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("vatsim - erroneous case - mission_updateAircraftCoords - aircraft not found");
                return null;
            }

            Aircrafts.Aircraft aircraft = aircraft1.get();
            if (aircraft.getLocationStatus() != Aircrafts.LocationStatus.Flying) {
                log.error("erroneous case, aircraft not in Flying state, in mission_updateAircraftCoords <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("vatsim - erroneous case - mission_updateAircraftCoords - aircraft not in Flying state");
                return null;
            }

            aircraft.setLocationLatitude((float) position.getCoords().getLat());
            aircraft.setLocationLongitude((float) position.getCoords().getLon());
            aircraft.setLocationHeading(position.getHeading());
            aircraft.setLocationAltitude(position.getActualAltitude());

            return mission;
        });
    }

    private FlightMissions.Mission mission_landing(final String landingAirportIcao) {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_landing <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("vatsim - erroneous case - mission_landing - fm not found");
                return null;
            }
            final FlightMissions.Mission mission = mission1.get();

            final Airports.Airport landingAirport = world.airports().byIcao(landingAirportIcao).orElseThrow();

            if (mission.getStatus() == FlightMissions.Status.Flying) {
                world.flightMissionControl().landing(mission, landingAirport);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event("vatsim - landing");

            return mission;
        });
    }

    private FlightMissions.Mission mission_blocksOnAndFinish() {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_blocksOnAndFinish <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("vatsim - erroneous case - mission_blocksOnAndFinish - fm not found");
                return null;
            }
            final FlightMissions.Mission mission = mission1.get();

            if (mission.getStatus() == FlightMissions.Status.Arrival) {
                world.flightMissionControl().blocksOn(mission);
                world.flightMissionControl().finish(mission);

                ShadowJetLogic.deboardTransportFlightIfExists(world, mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event("vatsim - blocksOnAndFinish");

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
                log.error("erroneous case, f/m not found, in mission_cancelBeforeTakeoffIfExists <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("vatsim - erroneous case - mission_cancelBeforeTakeoffIfExists - fm not found");
                return null;
            }

            if (mission.get().getStatus() == FlightMissions.Status.Preflight
                    || mission.get().getStatus() == FlightMissions.Status.Departure) {
                world.flightMissionControl().cancelFlightAndReturnAircraftToDepartureAirport(mission.get());
                world.flightMissionControl().scheduleQuickRemoval(mission.get());

                ShadowJetLogic.cancelTransportFlightIfExists(world, mission.get());
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.get().getStatus());
            }

            FlightStats.event("vatsim - cancelBeforeTakeoffIfExists");

            return null;
        });
    }

    private void mission_cancelFromFlying() {
        worldAccess.modifySync(world -> {
            if (flightMissionId == 0) {
                log.error("erroneous case, f/m == 0, in mission_cancelFromFlying <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("vatsim - erroneous case - mission_cancelFromFlying - fm is 0");
                return null;
            }

            final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flightMissionId);
            if (mission.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_cancelFromFlying <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event("vatsim - erroneous case - mission_cancelFromFlying - fm not found");
                return null;
            }

            if (mission.get().getStatus() == FlightMissions.Status.Flying) {
                world.flightMissionControl().cancelFlightAndReturnAircraftToDepartureAirport(mission.get());

                ShadowJetLogic.cancelTransportFlightIfExists(world, mission.get());
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.get().getStatus());
            }

            FlightStats.event("vatsim - cancelFromFlying");

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
/* todo ak3 pilot log disabled, probably need to delete it at all

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
            log.error("unable to write pilot log", e);
        }*/
    }

    private String missionLogHead(final FlightMissions.Mission mission, final Flightplan flightplan) {
        return String.format("[%s] f/m #%s, a/c #%s : %s -> %s",
                pilotNumber,
                mission != null ? mission.getId() : "-",
                mission != null ? mission.getAircraftId() : "-",
                flightplan != null ? flightplan.getDeparture() : "????",
                flightplan != null ? flightplan.getDestination() : "????");
    }

    private String flightplanToRoute(final Flightplan flightplan) {
        return (flightplan != null ? flightplan.getDeparture() : "????") + "-" +
                (flightplan != null ? flightplan.getDestination() : "????");
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
