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

    public static void process(final World world, final int worldTime) {
        final EventsToProcess eventsToProcess = world.eventsToProcess();

        while (true) {
            final Optional<EventsToProcess.Event> pilotOnDutyEvent = eventsToProcess.findFirstActiveEvent(PilotOnDuty, worldTime);
            if (pilotOnDutyEvent.isEmpty()) {
                break;
            }

            final FlightMissions.Mission mission = world.flightMissions().byId(pilotOnDutyEvent.get().getObjectId()).orElseThrow();
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

            mission.setStatus(FlightMissions.Status.Preflight);
            mission.setHeartbeatTime(worldTime + Time.TICK);
            aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Active);
            aircraft.setFlightMissionId(mission.getId());
            // todo ak1 pilot/pilots/cabin crew - set status
            pilotOnDutyEvent.get().setProcessedStatus();

            int pilot = 0; // todo ak2 remove it when pilot is introduced
            world.log(EventLog.EventType.FlightStarted, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
            log.info("Pilot {}, flight {} - flight started and in Preflight status, aircraft {} is activated", pilot, mission.getId(), aircraft.getRegNo());
        }

        while (true) {
            final Optional<FlightMissions.Mission> mission = world.flightMissions().nextForHeartbeat(worldTime);
            if (mission.isEmpty()) {
                break;
            }

            final FlightTimeline timeline = FlightMissionToTimeline.byMission(mission.get());
            final LocalDateTime now = Time.toLdt(worldTime);
            switch (mission.get().getStatus()) {
                case Preflight -> {
                    if (timeline.getBlocksOff().getEstimatedTime().isBefore(now)) {
                        blocksOff(world, worldTime, mission.get());
                    }
                }
                case Departure ->  {
                    if (timeline.getTakeoff().getEstimatedTime().isBefore(now)) {
                        takeoff(world, worldTime, mission.get());
                    }
                }
                case Flying -> {
                    fly(world, worldTime, mission.get());
                }
                case Arrival -> {
                    if (timeline.getBlocksOn().getEstimatedTime().isBefore(now)) {
                        blocksOn(world, worldTime, mission.get());

                        // todo ak2 scheduling.scheduleEvent(StartDeboardingCommand.class, flight, timeMachine.now().plusMinutes(3));
                    }
                }
                case Postflight -> {
                    if (timeline.getFinish().getEstimatedTime().isBefore(now)) {
                        finishFlight(world, worldTime, mission.get());
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

    private static void blocksOff(final World world, final int worldTime, final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Departure);
        mission.setActualDepartureTime(worldTime);

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        // todo ak2 scheduling.fireEvent(session, BlocksOff.class, flight);

        // todo ak2   (was not implemented in #old) pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        // todo ak2 aircraft.setStatus(Aircraft.Status.TaxiingOut);

        int pilot = 0; // todo ak2 remove it when pilot is introduced
        world.log(EventLog.EventType.AircraftDepartedFromGate, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
        log.info("Pilot {}, flight {} - aircraft {} departed from gate at {}", pilot, mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDepartureAirportId()));
    }

    private static void takeoff(final World world, final int worldTime, final FlightMissions.Mission mission) {
        // todo ak2 Pilot pilot = ctx.getPilot();

        mission.setStatus(FlightMissions.Status.Flying);
        mission.setActualTakeoffTime(worldTime);

        // todo ak2 scheduling.fireEvent(session, Takeoff.class, flight);

        // todo ak2 (not implemented in #old) pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        // todo ak2 Person person = pilot.getPerson();
        // todo ak2 person.setLocationAirport(null);

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setLocationStatus(Aircrafts.LocationStatus.Flying);

        final Airports.Airport locationAirport = world.airports().byId(aircraft.getLocationAirportId()).orElseThrow();
        aircraft.setLocationAirportId(0);
        aircraft.setLocationLatitude(locationAirport.getLatitude());
        aircraft.setLocationLongitude(locationAirport.getLongitude());

        int pilot = 0; // todo ak2 remove it when pilot is introduced
        world.log(EventLog.EventType.AircraftTakeoff, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
        log.info("Pilot {}, flight {} - aircraft {} took off at {}", pilot, mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDepartureAirportId()));
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

            landing(world, worldTime, mission);

        }
    }

    private static void landing(final World world, final int worldTime, final FlightMissions.Mission mission) {
        // todo ak2 Pilot pilot = ctx.getPilot();
        // todo ak2 Person person = pilot.getPerson();
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        mission.setStatus(FlightMissions.Status.Arrival);
        mission.setActualLandingTime(worldTime);

        // todo ak1 scheduling.fireEvent(session, Landing.class, flight);

// todo ak2        person.setLocationAirport(flight.getToAirport());
// todo ak2               pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        final Airports.Airport locationAirport = world.airports().byId(mission.getDestinationAirportId()).orElseThrow();

        aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
        aircraft.setLocationAirportId(locationAirport.getId());
        aircraft.setLocationLatitude(locationAirport.getLatitude());
        aircraft.setLocationLongitude(locationAirport.getLongitude());

        int pilot = 0; // todo ak2 remove it when pilot is introduced
        world.log(EventLog.EventType.AircraftLanding, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDestinationAirportId()));
        log.info("Pilot {}, flight {} - aircraft {} landed at {}", pilot, mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDestinationAirportId()));
    }

    private static void blocksOn(final World world, final int worldTime, final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Postflight);
        mission.setActualArrivalTime(worldTime);

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        // todo ak2 scheduling.fireEvent(session, BlocksOn.class, flight);

// todo ak2               pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        int pilot = 0; // todo ak2 remove it when pilot is introduced
        world.log(EventLog.EventType.AircraftArrivedToGate, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDestinationAirportId()));
        log.info("Pilot {}, flight {} - aircraft {} arrived to gate at {}", pilot, mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDestinationAirportId()));
    }

    private static void finishFlight(final World world, final int worldTime, final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Finished);

        // todo ak2 pilot.setStatus(Pilot.Status.Idle);
// todo ak2               pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
        aircraft.setFlightMissionId(0);

        // todo ak2 pilotAssignment.setStatus(PilotAssignment.Status.Done);

        // todo ak2 aircraftAssignment.setStatus(AircraftAssignment.Status.Done);

        int pilot = 0; // todo ak2 remove it when pilot is introduced
        world.log(EventLog.EventType.FlightFinished, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDestinationAirportId()));
        log.info("Pilot {}, flight {} - flight finished", pilot, mission.getId());
    }
}
