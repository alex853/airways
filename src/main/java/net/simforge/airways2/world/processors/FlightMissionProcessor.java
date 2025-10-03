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

        EventProcessing.process(world, PilotOnDuty, event -> {
            final int flightId = event.getObjectId();
            final FlightMissions.Mission mission = world.flightMissions().byId(flightId).orElseThrow();
            if (mission.isModePc()) {
                return;
            }
            world.flightMissionControl().startOrCancel(mission);
        });

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
        final Optional<TransportFlights.Flight> transportFlight = world.transportFlights().byFlightMissionId(mission.getId());
        final FlightTimeline timeline = FlightMissionToTimeline.byMission(mission);
        final LocalDateTime now = Time.toLdt(worldTime);
        switch (mission.getStatus()) {
            case Preflight -> {
                if (!mission.isModePc()) {
                    transportFlight.ifPresent(tf -> {
                        if (TransportFlightHelper.calcBoardingStartTime(mission) <= worldTime) {
                            if (TransportFlightHelper.flightStatusAllowsToStartBoarding(tf.getStatus())) {
                                TransportFlightControl.instance(world).startBoarding(tf);
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
                    transportFlight.ifPresent(tf -> TransportFlightControl.instance(world).scheduleAutomaticDeboarding(tf));
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
