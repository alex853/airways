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

            // todo ak0 pilot npc/pc check

            final FlightMissions.Mission mission = world.flightMissions().byId(pilotOnDutyEvent.get().getObjectId()).orElseThrow();
            world.flightMissionControl().startOrCancel(mission);

            pilotOnDutyEvent.get().setProcessedStatus();
        }

        while (true) {
            final Optional<FlightMissions.Mission> mission = world.flightMissions().nextForHeartbeat(worldTime);
            if (mission.isEmpty()) {
                break;
            }

            // todo ak0 pilot npc/pc check

            final FlightMissionControl flightControl = world.flightMissionControl();
            final FlightTimeline timeline = FlightMissionToTimeline.byMission(mission.get());
            final LocalDateTime now = Time.toLdt(worldTime);
            switch (mission.get().getStatus()) {
                case Preflight -> {
                    if (timeline.getBlocksOff().getEstimatedTime().isBefore(now)) {
                        flightControl.blocksOff(mission.get());
                    }
                }
                case Departure -> {
                    if (timeline.getTakeoff().getEstimatedTime().isBefore(now)) {
                        flightControl.takeoff(mission.get());
                    }
                }
                case Flying -> {
                    fly(world, worldTime, mission.get());
                }
                case Arrival -> {
                    if (timeline.getBlocksOn().getEstimatedTime().isBefore(now)) {
                        flightControl.blocksOn(mission.get());

                        // todo ak2 scheduling.scheduleEvent(StartDeboardingCommand.class, flight, timeMachine.now().plusMinutes(3));
                    }
                }
                case Postflight -> {
                    if (timeline.getFinish().getEstimatedTime().isBefore(now)) {
                        flightControl.finish(mission.get());
                    }
                }
                default -> throw new IllegalStateException("what to do here???"); // todo ak2 ???
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

        // todo ak2 AircraftType aircraftType = flight.getAircraftType();
        final AircraftPerformanceData performanceData = AircraftPerformanceDataHelper.getData(); // todo ak2
        final SimpleFlight simpleFlight = SimpleFlight.forRoute(
                fromAirport.getCoords(),
                toAirport.getCoords(),
                performanceData);

        final Duration actualTimeSinceTakeoff = Duration.between(Time.toLdt(mission.getActualTakeoffTime()), Time.toLdt(worldTime));

        final SimpleFlight.Position aircraftPosition = simpleFlight.getAircraftPosition(actualTimeSinceTakeoff);

        if (aircraftPosition.getStage() != SimpleFlight.Position.Stage.AfterLanding) {

            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
            // todo ak2 Pilot pilot = session.load(Pilot.class, ctx.getPilot().getId());

            final Geo.Coords coords = aircraftPosition.getCoords();

            aircraft.setLocationLatitude((float) coords.getLat());
            aircraft.setLocationLongitude((float) coords.getLon());

// todo ak2 not implemented in #old                       pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        } else {

            world.flightMissionControl().landing(mission);

        }
    }
}
