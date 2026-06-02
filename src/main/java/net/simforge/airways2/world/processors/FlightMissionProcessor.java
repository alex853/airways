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


            aircraft.setLocationLatitude((float) coords.getLat());
            aircraft.setLocationLongitude((float) coords.getLon());
            aircraft.setLocationHeading((int) Geo.bearing(coords, toAirport.getCoords()));

            aircraft.setLastUpdated(worldTime);

            // todo ak3 not implemented in #old                       pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        } else {

            if (mission.getCharacterMode() == FlightMissions.CharacterMode.NPC) {
                world.flightMissionControl().landing(mission, toAirport);
            }
        }
    }

    private static void flyHeadingMode(final World world, final int worldTime, final FlightMissions.Mission mission) {
        Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        AircraftTypes.AircraftType aircraftType = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow();
        AircraftPerformanceData performanceData = AircraftPerformanceData.getData(aircraftType.getIcao());

        double timeHrs = Duration.between(Time.toLdt(aircraft.getLastUpdated()), Time.toLdt(worldTime)).getSeconds() / 3600.0;

        double seaLevelTas = performanceData.getTakeoffSpeed() * 1.3;
        double cruiseLevelTas = performanceData.getTypicalCruiseSpeed();

        double altitudeFactor = (double) aircraft.getLocationAltitude() / (double) performanceData.getTypicalCruiseAltitude();

        double currentTasKts = seaLevelTas + altitudeFactor * (cruiseLevelTas - seaLevelTas);

        double distanceNm = currentTasKts * timeHrs;

        Geo.Coords currentPosition = Geo.destination(aircraft.getLocationCoords(), aircraft.getLocationHeading(), distanceNm);

        aircraft.setLocationLatitude((float) currentPosition.getLat());
        aircraft.setLocationLongitude((float) currentPosition.getLon());
        aircraft.setLastUpdated(worldTime);
    }

    private static void processPilotOnDutyEvent(World world, EventsToProcess.Event event) {
        final int flightId = event.getObjectId();
        final FlightMissions.Mission mission = world.flightMissions().byId(flightId).orElseThrow();
        if (mission.getCharacterMode() == FlightMissions.CharacterMode.PC) {
            return;
        }
        world.flightMissionControl().startOrCancel(mission);
    }
}
