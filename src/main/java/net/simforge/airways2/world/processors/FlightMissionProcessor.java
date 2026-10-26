package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.*;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static net.simforge.airways2.world.datamodel.EventsToProcess.Type.PilotOnDuty;

public class FlightMissionProcessor {
    private static final Logger log = LoggerFactory.getLogger(FlightMissionProcessor.class);

    public static void process(final World world) {
        final int worldTime = world.getWorldTime();

        Processing.event(world, PilotOnDuty, event -> processPilotOnDutyEvent(world, event));

        Processing.heartbeat(() -> world.flightMissions().nextForHeartbeat(worldTime),
                mission -> processMission(world, mission));
    }

    private static void processMission(final World world, final FlightMissions.Mission mission) {
        final int worldTime = world.getWorldTime();
        final LocalDateTime now = Time.toLdt(worldTime);

        final FlightMissionControl flightControl = world.flightMissionControl();
        final TransportFlightControl tfControl = world.transportFlightControl();

        final Optional<TransportFlights.Flight> transportFlight = world.transportFlights().byFlightMissionId(mission.getId());
        final FlightTimeline timeline = FlightMissionToTimeline.byMission(mission);

        countNightTimeOnTheGround(world, mission);

        switch (mission.getStatus()) {
            case Preflight -> {
                if (mission.getCharacterMode() == FlightMissions.CharacterMode.NPC) {
                    transportFlight.ifPresent(tf -> {
                        if (TransportFlightHelper.calcBoardingStartTime(mission) <= worldTime) {
                            if (TransportFlightHelper.flightStatusAllowsToStartBoarding(tf.getStatus())) {
                                tfControl.startBoarding(tf);
                            }
                        }
                    });
                }

                if (mission.getCharacterMode() == FlightMissions.CharacterMode.NPC && timeline.getBlocksOff().getEstimatedTime().isBefore(now)) {
                    flightControl.blocksOff(mission);
                }
            }
            case Departure -> {
                if (mission.getCharacterMode() == FlightMissions.CharacterMode.NPC && timeline.getTakeoff().getEstimatedTime().isBefore(now)) {
                    flightControl.takeoff(mission);
                }
            }
            case Flying -> {
                if (mission.getCoordinatesSource() == FlightMissions.CoordinatesSource.AutomaticSimpleFlight) {
                    flySimpleFlight(world, worldTime, mission);
                } else if (mission.getCoordinatesSource() == FlightMissions.CoordinatesSource.FlyHeadingMode) {
                    flyHeadingMode(world, worldTime, mission);
                }
            }
            case Arrival -> {
                if (mission.getCharacterMode() == FlightMissions.CharacterMode.NPC  && timeline.getBlocksOn().getEstimatedTime().isBefore(now)) {
                    flightControl.blocksOn(mission);
                    transportFlight.ifPresent(tfControl::scheduleAutomaticDeboarding);
                }
            }
            case Postflight -> {
                if (mission.getCharacterMode() == FlightMissions.CharacterMode.NPC  && timeline.getFinish().getEstimatedTime().isBefore(now)) {
                    flightControl.finish(mission);
                } else if (mission.getCharacterMode() == FlightMissions.CharacterMode.PC && mission.isAutoFinish()
                        && transportFlight.map(tf -> tf.getStatus() == TransportFlights.Status.Finished).orElse(true)) {
                    // The flight of a pilot who has the auto finish is at its end: the passengers have got off (or there are none), so the pilot does not have to press
                    // the button. Not earlier than the deboarding is over, the same condition as of the button (see FlightDashboardController.buildManualActions).
                    log.info("f/m #{} - the flight is finished by itself (auto finish)", mission.getId());
                    flightControl.finish(mission);
                }
            }
            case Finished, Cancelled -> {
                // noop
            }
            default -> {
                world.log(EventLog.EventType.FlightIsInUnexpectedStatus, EventLog.userId(mission.getUserId()), mission);
                log.warn("f/m #{} - flight is in unexpected status - {}", mission.getId(), mission.getStatus());
            }
        }

        if (mission.getStatus() == FlightMissions.Status.Finished
                || mission.getStatus() == FlightMissions.Status.Cancelled) {
            mission.setHeartbeatTime(0);
        } else {
            mission.setHeartbeatTime(worldTime + Time.TICK);
        }
    }

    // Between blocks off and takeoff, and between landing and blocks on, the aircraft of a flight of a user stays at the airport and nobody sets its position,
    // but the night time goes on. A flight which the sim tracker follows gets its positions from the sim.
    private static void countNightTimeOnTheGround(final World world, final FlightMissions.Mission mission) {
        final FlightMissions.Status status = mission.getStatus();
        if (mission.getUserId() <= 0
                || (status != FlightMissions.Status.Departure && status != FlightMissions.Status.Arrival)
                || mission.getCoordinatesSource() == FlightMissions.CoordinatesSource.TrackedViaTracker) {
            return;
        }
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        world.flightMissionControl().countNightTime(mission, aircraft.getLocationLatitude(), aircraft.getLocationLongitude());
    }

    private static void flySimpleFlight(final World world, final int worldTime, final FlightMissions.Mission mission) {
        final Airports.Airport fromAirport = world.airports().byId(mission.getDepartureAirportId()).orElseThrow();
        final Airports.Airport toAirport = world.airports().byId(mission.getDestinationAirportId()).orElseThrow();

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow();
        final AircraftPerformanceData performanceData = AircraftPerformanceData.getData(aircraftType.getIcao());
        final SimpleFlight simpleFlight = SimpleFlight.forRoute(
                fromAirport.getCoords(),
                toAirport.getCoords(),
                performanceData);

        final Duration actualTimeSinceTakeoff = Duration.between(Time.toLdt(mission.getActualTakeoffWorldTime()), Time.toLdt(worldTime));

        final SimpleFlight.Position aircraftPosition = simpleFlight.getAircraftPosition(actualTimeSinceTakeoff);

        if (aircraftPosition.getStage() != SimpleFlight.Position.Stage.AfterLanding) {

            // todo ak3 Pilot pilot = session.load(Pilot.class, ctx.getPilot().getId());

            final Geo.Coords coords = aircraftPosition.getCoords();


            world.flightMissionControl().moveAircraft(mission, coords.getLat(), coords.getLon(),
                    (int) Geo.bearing(coords, toAirport.getCoords()), aircraft.getLocationAltitude());

            // todo ak3 not implemented in #old                       pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        } else {

            if (mission.getCharacterMode() == FlightMissions.CharacterMode.NPC) {
                world.flightMissionControl().landing(mission, toAirport);
            } else {
                // the simple flight is over, the pilot has not pressed Landing yet: the aircraft is over the destination airport and the night time goes on
                world.flightMissionControl().countNightTime(mission, toAirport.getLatitude(), toAirport.getLongitude());
            }
        }
    }

    private static void flyHeadingMode(final World world, final int worldTime, final FlightMissions.Mission mission) {
        Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        AircraftTypes.AircraftType aircraftType = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow();
        AircraftPerformanceData performanceData = AircraftPerformanceData.getData(aircraftType.getIcao());

        double timeHrs = Duration.between(Time.toLdt(aircraft.getLastUpdated()), Time.toLdt(worldTime)).getSeconds() / 3600.0;

        double seaLevelTas = performanceData.getTakeoffSpeed();
        double cruiseLevelTas = performanceData.getTypicalCruiseSpeed();

        double altitudeFactor = (double) aircraft.getLocationAltitude() / (double) performanceData.getTypicalCruiseAltitude();

        double currentTasKts = seaLevelTas + altitudeFactor * (cruiseLevelTas - seaLevelTas);

        double distanceNm = currentTasKts * timeHrs;

        Geo.Coords currentPosition = Geo.destination(aircraft.getLocationCoords(), aircraft.getLocationHeading(), distanceNm);

        world.flightMissionControl().moveAircraft(mission, currentPosition.getLat(), currentPosition.getLon(),
                aircraft.getLocationHeading(), aircraft.getLocationAltitude());
    }

    private static void processPilotOnDutyEvent(World world, EventsToProcess.Event event) {
        final int flightId = event.getObjectId();
        final FlightMissions.Mission mission = world.flightMissions().byId(flightId).orElseThrow();
        if (mission.getCharacterMode() == FlightMissions.CharacterMode.PC) {
            return;
        }

        final FlightMissionControl.StartResult startResult = world.flightMissionControl().start(mission);
        if (startResult.started()) {
            return;
        }

        // Something has changed since the mission was dispatched. A dispatched mission is cancelled with its t/f and journeys,
        // a mission which has moved on (started, cancelled, finished) is left as it is.
        if (mission.getStatus() == FlightMissions.Status.Dispatched) {
            world.flightMissionControl().cancelDispatched(mission, startResult.reason());
        } else {
            log.warn("f/m #{} - pilot on duty event is ignored - {}", mission.getId(), startResult.reason());
        }
    }
}
