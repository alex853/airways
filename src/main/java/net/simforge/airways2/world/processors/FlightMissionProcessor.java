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

            try {
                processMission(world, mission.get());
            } catch (final RuntimeException e) {
                log.warn("f/m #{} - flight processing error - {}", mission.get().getId(), mission.get().getStatus(), e);
                throw e;
            }
        }
    }

    private static void processMission(final World world, final FlightMissions.Mission mission) {
        final int worldTime = world.getWorldTime();
        final FlightMissionControl flightControl = world.flightMissionControl();
        final FlightTimeline timeline = FlightMissionToTimeline.byMission(mission);
        final LocalDateTime now = Time.toLdt(worldTime);
        switch (mission.getStatus()) {
            case Preflight -> {
                if (!mission.isModePc()) {
                    world.transportFlights().byFlightMissionId(mission.getId()).ifPresent(transportFlight -> {
                        final LocalDateTime boardingStartTime = timeline.getBlocksOff().getEstimatedTime().minusMinutes(15); // todo ak1 that is weird! need to redo!
                        if (boardingStartTime.isBefore(now)) {
                            final TransportFlights.Status transportFlightStatus = transportFlight.getStatus();
                            if (transportFlightStatus == TransportFlights.Status.Scheduled // todo ak1 hm? orly?
                                    || transportFlightStatus == TransportFlights.Status.Checkin // todo ak1 hm? orly?
                                    || transportFlightStatus == TransportFlights.Status.WaitingForBoarding) {
                                TransportFlightControl.instance(world).startBoarding(transportFlight);
                            }
                        }
                    });
                }

                if (!mission.isModePc() && timeline.getBlocksOff().getEstimatedTime().isBefore(now)) {
                    flightControl.blocksOff(mission);
                }
            }
            case Departure -> {
                if (!mission.isModePc() && timeline.getTakeoff().getEstimatedTime().isBefore(now)) {
                    flightControl.takeoff(mission);
                }
            }
            case Flying -> {
                fly(world, worldTime, mission);
            }
            case Arrival -> {
                if (!mission.isModePc() && timeline.getBlocksOn().getEstimatedTime().isBefore(now)) {
                    flightControl.blocksOn(mission);
                    // todo ak3 scheduling.scheduleEvent(StartDeboardingCommand.class, flight, timeMachine.now().plusMinutes(3));
                }
            }
            case Postflight -> {
                if (!mission.isModePc() && timeline.getFinish().getEstimatedTime().isBefore(now)) {
                    flightControl.finish(mission);
                }
            }
            case Finished, Cancelled -> {
                // noop
            }
            default -> {
                world.log(EventLog.EventType.FlightIsInUnexpectedStatus, EventLog.pilotId(0), mission);
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
