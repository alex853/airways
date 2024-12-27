package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.*;
import net.simforge.airways2.world.storage.Aircrafts;
import net.simforge.airways2.world.storage.Airports;
import net.simforge.airways2.world.storage.Events;
import net.simforge.airways2.world.storage.FlightMissions;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static net.simforge.airways2.world.storage.Events.Type.PilotOnDuty;

public class FlightMissionProcessor {
    private static final Logger log = LoggerFactory.getLogger(FlightMissionProcessor.class);

    public static void process(final World world, final int worldTime) {
        final Events events = world.events();

        while (true) {
            final Optional<Events.Event> pilotOnDutyEvent = events.findFirstActiveEvent(PilotOnDuty, worldTime);
            if (pilotOnDutyEvent.isEmpty()) {
                break;
            }

            final FlightMissions.Mission mission = world.flightMissions().byId(pilotOnDutyEvent.get().getObjectId()).orElseThrow();
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

            mission.setStatus(FlightMissions.Status.Preflight);
            mission.setHeartbeatTime(worldTime + Time.TICK);
            aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Active);
            // todo ak1 pilot/pilots/cabin crew - set status
            pilotOnDutyEvent.get().setProcessedStatus();
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
                default -> throw new IllegalStateException("what to do here???");
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

        // todo ak2 scheduling.fireEvent(session, BlocksOff.class, flight);

        // todo ak2   (was not implemented in #old) pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        // todo ak2 aircraft.setStatus(Aircraft.Status.TaxiingOut);

        // todo ak1 EventLog.info(session, log, pilot, "Aircraft departed from gate", flight, aircraft, flight.getFromAirport());

        // todo ak1 log.info("Pilot {}, flight {} - aircraft {} departed from gate at {}", pilot, flight, aircraft, flight.getFromAirport());

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

        // todo ak1 EventLog.info(session, log, pilot, "Takeoff", flight, aircraft, flight.getFromAirport());

        // todo ak1 log.info("Pilot {}, flight {} - aircraft {} took off at {}", pilot, flight, aircraft, flight.getFromAirport());
    }

    private static void fly(final World world, final int worldTime, final FlightMissions.Mission mission) {
        final Airports.Airport fromAirport = world.airports().byId(mission.getDepartureAirportId()).orElseThrow();
        final Airports.Airport toAirport = world.airports().byId(mission.getDestinationAirportId()).orElseThrow();

        // todo ak1 AircraftType aircraftType = flight.getAircraftType();
        final AircraftPerformanceData performanceData = AircraftPerformanceDataHelper.getData(); // todo ak1
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

        // todo ak1 EventLog.info(session, log, pilot, "Landing", flight, aircraft, flight.getToAirport());

        // todo ak1 log.info("Pilot {}, flight {} - aircraft {} landed at {}", pilot, flight, aircraft, flight.getToAirport());
    }

    private static void blocksOn(final World world, final int worldTime, final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Postflight);
        mission.setActualArrivalTime(worldTime);

        // todo ak2 scheduling.fireEvent(session, BlocksOn.class, flight);

// todo ak2               pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        // todo ak1 EventLog.info(session, log, pilot, "Aircraft arrived to gate", flight, aircraft, flight.getToAirport());

        // todo ak1 log.info("Pilot {}, flight {} - aircraft {} arrived to gate", pilot, flight, aircraft);
    }

    private static void finishFlight(final World world, final int worldTime, final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Finished);

        // todo ak2 pilot.setStatus(Pilot.Status.Idle);
// todo ak2               pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);

        // todo ak2 pilotAssignment.setStatus(PilotAssignment.Status.Done);

        // todo ak2 aircraftAssignment.setStatus(AircraftAssignment.Status.Done);

        // todo ak1 EventLog.info(session, log, pilot, "Flight finished", flight, aircraft, flight.getToAirport());

        // todo ak1 log.info("Pilot {}, flight {} - flight finished", pilot, flight);
    }
}
