package net.simforge.airways.processes.pilot.activity;

import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.Result;
import net.simforge.airways.processengine.activity.Activity;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.aircraft.Aircraft;
import net.simforge.airways.model.aircraft.AircraftType;
import net.simforge.airways.model.flight.AircraftAssignment;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.model.flight.PilotAssignment;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.processes.DurationConsts;
import net.simforge.airways.processes.flight.activity.FlightContext;
import net.simforge.airways.processes.flight.event.*;
import net.simforge.airways.util.FlightTimeline;
import net.simforge.airways.util.SimpleFlight;
import net.simforge.airways.processengine.TimeMachine;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Geo;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static net.simforge.airways.processengine.Result.When.NextMinute;

public class PilotOnDuty implements Activity {
    private static final Logger log = LoggerFactory.getLogger(PilotOnDuty.class);

    @Inject
    private Pilot pilot;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;
    @Inject
    private TimeMachine timeMachine;

    @Override
    public Result act() {
        BM.start("PilotTask.onDuty");
        try (Session session = sessionFactory.openSession()) {

            PilotAssignment pilotAssignment = PilotOps.findInProgressAssignment(session, pilot);
            if (pilotAssignment == null) {
                // probably we need to start the flight
                List<PilotAssignment> upcomingAssignments = PilotOps.loadUpcomingAssignments(session, pilot);
                pilotAssignment = !upcomingAssignments.isEmpty() ? upcomingAssignments.get(0) : null;

                if (pilotAssignment == null) {
                    log.error("Pilot {} - Unable to find pilot assignment suitable for flight start", pilot);
                    // todo event log, cancellation?
                    return Result.done();
                }

                Flight flight = pilotAssignment.getFlight();

                FlightContext flightCtx = FlightContext.load(session, flight);
//                flight = flightCtx.getFlight();

//                FlightTimeline timeline = FlightTimeline.byFlight(flight);
//                LocalDateTime preflightStartsAtDt = timeline.getStart().getScheduledTime();

                if (flight.getStatus() == Flight.Status.Assigned
                    /*&& preflightStartsAtDt.isBefore(timeMachine.now())*/) {
                    startFlight(flightCtx);

                    scheduling.scheduleEvent(StartBoardingCommand.class, flight, flight.getScheduledDepartureTime().minusMinutes(DurationConsts.START_OF_BOARDING_TO_DEPARTURE_MINS));

                    return Result.resume(NextMinute);
                } else {
                    log.error("Pilot {} - Unable to start the flight", pilot);
                    // todo event log, cancellation?
                    return Result.done(); // todo error?
                }

            }

            // todo logger.debug(String.format("Pilot %s is on %s assignment", pilot, pilotAssignment));

            Flight flight = pilotAssignment.getFlight();

            FlightContext flightCtx = FlightContext.load(session, flight);
            flight = flightCtx.getFlight();

            FlightTimeline timeline = FlightTimeline.byFlight(flight);
            LocalDateTime now = timeMachine.now();

            switch (flight.getStatus()) {

                case Flight.Status.Planned:
                case Flight.Status.Assigned:
                case Flight.Status.Finished:
                    // error
                    throw new IllegalArgumentException("Can't process flight status " + flight.getStatus() + " for flight " + flight + " and pilot " + pilot);

                case Flight.Status.PreFlight:
                    if (timeline.getBlocksOff().getEstimatedTime().isBefore(now)) {
                        blocksOff(flightCtx);
                    }
                    break;

                case Flight.Status.Departure:
                    if (timeline.getTakeoff().getEstimatedTime().isBefore(now)) {
                        takeoff(flightCtx);
                    }
                    break;

                case Flight.Status.Flying:
                    fly(flightCtx);
                    break;

                case Flight.Status.Arrival:
                    if (timeline.getBlocksOn().getEstimatedTime().isBefore(now)) {
                        blocksOn(flightCtx);

                        scheduling.scheduleEvent(StartDeboardingCommand.class, flight, timeMachine.now().plusMinutes(3));
                    }
                    break;

                case Flight.Status.PostFlight:
                    if (timeline.getFinish().getEstimatedTime().isBefore(now)) {
                        finishFlight(flightCtx);

                        return Result.done();
                    }
                    break;

                default:
                    throw new IllegalStateException("Can't process flight status " + flight.getStatus() + " for flight " + flight + " and pilot " + pilot);
            }

            return Result.resume(NextMinute);

        } finally {
            BM.stop();
        }
    }

    @Override
    public Result onExpiry() {
        return Result.nothing();
    }

    private void startFlight(FlightContext _ctx) {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, "PilotTask.startFlight", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx.getFlight());

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Aircraft aircraft = ctx.getAircraft();
                PilotAssignment pilotAssignment = ctx.getPilotAssignment();
                AircraftAssignment aircraftAssignment = ctx.getAircraftAssignment();

                flight.setStatus(Flight.Status.PreFlight); // todo p3 think about stages of flight, duration, etc, document it somewhere

                pilot.setStatus(Pilot.Status.OnDuty);
//                pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

                aircraft.setStatus(Aircraft.Status.PreFlight);

                pilotAssignment.setStatus(PilotAssignment.Status.InProgress);

                aircraftAssignment.setStatus(AircraftAssignment.Status.InProgress);

                session.update(flight);
                session.update(pilot);
                session.update(aircraft);
                session.update(pilotAssignment);
                session.update(aircraftAssignment);

                EventLog.info(session, log, pilot, "Pilot has started flight", flight);

                log.info("Pilot {}, flight {} - flight started", pilot, flight);

            });
        }
    }

    private void blocksOff(FlightContext _ctx) {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, "PilotOnDuty.blocksOff", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx.getFlight());

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Aircraft aircraft = ctx.getAircraft();

                flight.setStatus(Flight.Status.Departure);
                flight.setActualDepartureTime(timeMachine.now());

                scheduling.fireEvent(session, BlocksOff.class, flight);

//                pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

                aircraft.setStatus(Aircraft.Status.TaxiingOut);

                session.update(flight);
                session.update(pilot);
                session.update(aircraft);

                EventLog.info(session, log, pilot, "Aircraft departed from gate", flight, aircraft, flight.getFromAirport());

                log.info("Pilot {}, flight {} - aircraft {} departed from gate at {}", pilot, flight, aircraft, flight.getFromAirport());

            });
        }
    }

    private void takeoff(FlightContext _ctx) {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, "PilotTask.takeoff", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx.getFlight());

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Aircraft aircraft = ctx.getAircraft();

                flight.setStatus(Flight.Status.Flying);
                flight.setActualTakeoffTime(timeMachine.now());

                scheduling.fireEvent(session, Takeoff.class, flight);

//                pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

                Person person = pilot.getPerson();
                person.setLocationAirport(null);

                aircraft.setStatus(Aircraft.Status.Flying);
                Airport locationAirport = ctx.getAircraft().getLocationAirport();
                aircraft.setLocationAirport(null);
                aircraft.setLocationLatitude(locationAirport.getLatitude());
                aircraft.setLocationLongitude(locationAirport.getLongitude());

                session.update(flight);
                session.update(pilot);
                session.update(person);
                session.update(aircraft);

                EventLog.info(session, log, pilot, "Takeoff", flight, aircraft, flight.getFromAirport());

                log.info("Pilot {}, flight {} - aircraft {} took off at {}", pilot, flight, aircraft, flight.getFromAirport());

            });
        }
    }

    private void fly(FlightContext _ctx) {
        BM.start("PilotTask.fly");
        try (Session session = sessionFactory.openSession()) {

            FlightContext ctx = FlightContext.load(session, _ctx.getFlight());

            Flight flight = ctx.getFlight();

            Airport fromAirport = flight.getFromAirport();
            Airport toAirport = flight.getToAirport();

            Geo.Coords fromCoords = new Geo.Coords(fromAirport.getLatitude(), fromAirport.getLongitude());
            Geo.Coords toCoords = new Geo.Coords(toAirport.getLatitude(), toAirport.getLongitude());

            AircraftType aircraftType = flight.getAircraftType();
            SimpleFlight simpleFlight = SimpleFlight.forRoute(
                    fromCoords,
                    toCoords,
                    aircraftType);

            Duration actualTimeSinceTakeoff = Duration.between(flight.getActualTakeoffTime(), timeMachine.now());

            SimpleFlight.Position aircraftPosition = simpleFlight.getAircraftPosition(actualTimeSinceTakeoff);

            if (aircraftPosition.getStage() != SimpleFlight.Position.Stage.AfterLanding) {

                HibernateUtils.transaction(session, "PilotOnDuty.fly#inAir", () -> {

                    Aircraft aircraft = session.load(Aircraft.class, ctx.getAircraft().getId());
                    Pilot pilot = session.load(Pilot.class, ctx.getPilot().getId());

                    Geo.Coords coords = aircraftPosition.getCoords();

                    aircraft.setLocationLatitude(coords.getLat());
                    aircraft.setLocationLongitude(coords.getLon());

//                        pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

                    session.update(aircraft);
                    session.update(pilot);
                });

            } else {

                landing(ctx);

            }

        } finally {
            BM.stop();
        }
    }

    private void landing(FlightContext _ctx) {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, "PilotTask.landing", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx.getFlight());

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Person person = pilot.getPerson();
                Aircraft aircraft = ctx.getAircraft();

                flight.setStatus(Flight.Status.Arrival);
                flight.setActualLandingTime(timeMachine.now());

                scheduling.fireEvent(session, Landing.class, flight);

                person.setLocationAirport(flight.getToAirport());
//                pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

                aircraft.setLocationLatitude(null);
                aircraft.setLocationLongitude(null);
                aircraft.setLocationAirport(ctx.getFlight().getToAirport());
                aircraft.setStatus(Aircraft.Status.TaxiingIn);

                session.update(flight);
                session.update(pilot);
                session.update(person);
                session.update(aircraft);

                EventLog.info(session, log, pilot, "Landing", flight, aircraft, flight.getToAirport());

                log.info("Pilot {}, flight {} - aircraft {} landed at {}", pilot, flight, aircraft, flight.getToAirport());

            });
        }
    }

    private void blocksOn(FlightContext _ctx) {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, "PilotTask.blocksOn", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx.getFlight());

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Aircraft aircraft = ctx.getAircraft();

                flight.setStatus(Flight.Status.PostFlight);
                flight.setActualArrivalTime(timeMachine.now());

                scheduling.fireEvent(session, BlocksOn.class, flight);

//                pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

                aircraft.setStatus(Aircraft.Status.PostFlight);

                session.update(flight);
                session.update(pilot);
                session.update(aircraft);

                EventLog.info(session, log, pilot, "Aircraft arrived to gate", flight, aircraft, flight.getToAirport());

                log.info("Pilot {}, flight {} - aircraft {} arrived to gate", pilot, flight, aircraft);

            });
        }
    }

    private void finishFlight(FlightContext _ctx) {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, "PilotTask.finishFlight", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx.getFlight());

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Aircraft aircraft = ctx.getAircraft();
                PilotAssignment pilotAssignment = ctx.getPilotAssignment();
                AircraftAssignment aircraftAssignment = ctx.getAircraftAssignment();

                flight.setStatus(Flight.Status.Finished);

                pilot.setStatus(Pilot.Status.Idle);
//                pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

                aircraft.setStatus(Aircraft.Status.Idle);

                pilotAssignment.setStatus(PilotAssignment.Status.Done);

                aircraftAssignment.setStatus(AircraftAssignment.Status.Done);

                session.update(flight);
                session.update(pilot);
                session.update(aircraft);
                session.update(pilotAssignment);
                session.update(aircraftAssignment);

                EventLog.info(session, log, pilot, "Flight finished", flight, aircraft, flight.getToAirport());

                log.info("Pilot {}, flight {} - flight finished", pilot, flight);

            });
        }
    }

}
