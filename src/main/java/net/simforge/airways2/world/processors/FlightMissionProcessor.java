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
        final EventsToProcess eventsToProcess = world.eventsToProcess();

        while (true) {
            final Optional<EventsToProcess.Event> pilotOnDutyEvent = eventsToProcess.findFirstActiveEvent(PilotOnDuty, worldTime);
            if (pilotOnDutyEvent.isEmpty()) {
                break;
            }

            final FlightMissions.Mission mission = world.flightMissions().byId(pilotOnDutyEvent.get().getObjectId()).orElseThrow();
            if (!mission.isModePc()) {
                world.flightMissionControl().startOrCancel(mission);
            }

            pilotOnDutyEvent.get().setProcessedStatus();
        }

        while (true) {
            final Optional<FlightMissions.Mission> mission = world.flightMissions().nextForHeartbeat(worldTime);
            if (mission.isEmpty()) {
                break;
            }

            final FlightMissionControl flightControl = world.flightMissionControl();
            final FlightTimeline timeline = FlightMissionToTimeline.byMission(mission.get());
            final LocalDateTime now = Time.toLdt(worldTime);
            switch (mission.get().getStatus()) {
                case Preflight -> {
                    if (!mission.get().isModePc() && timeline.getBlocksOff().getEstimatedTime().isBefore(now)) {
                        flightControl.blocksOff(mission.get());
                    }
                }
                case Departure -> {
                    if (!mission.get().isModePc() && timeline.getTakeoff().getEstimatedTime().isBefore(now)) {
                        flightControl.takeoff(mission.get());
                    }
                }
                case Flying -> {
                    fly(world, worldTime, mission.get());
                }
                case Arrival -> {
                    if (!mission.get().isModePc() && timeline.getBlocksOn().getEstimatedTime().isBefore(now)) {
                        flightControl.blocksOn(mission.get());
                        // todo ak3 scheduling.scheduleEvent(StartDeboardingCommand.class, flight, timeMachine.now().plusMinutes(3));
                    }
                }
                case Postflight -> {
                    if (!mission.get().isModePc() && timeline.getFinish().getEstimatedTime().isBefore(now)) {
                        flightControl.finish(mission.get());
                    }
                }
                case Finished, Cancelled -> {
                    // noop
                }
                default -> {
                    world.log(EventLog.EventType.FlightIsInUnexpectedStatus, EventLog.pilotId(0), mission.get());
                    log.warn("f/m #{} - flight is in unexpected status - {}", mission.get().getId(), mission.get().getStatus());
                }
            }

            if (mission.get().getStatus() == FlightMissions.Status.Finished
                    || mission.get().getStatus() == FlightMissions.Status.Cancelled) {
                mission.get().setHeartbeatTime(0);
            } else {
                mission.get().setHeartbeatTime(worldTime + Time.TICK);
            }
        }
    }

    private static void fly(final World world, final int worldTime, final FlightMissions.Mission mission) {
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

            // todo ak3 not implemented in #old                       pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        } else {

            if (!mission.isModePc()) {
                world.flightMissionControl().landing(mission, toAirport);
            }
        }
    }
}
