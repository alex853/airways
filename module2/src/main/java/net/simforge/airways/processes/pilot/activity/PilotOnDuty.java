/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.pilot.activity;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.Pilot;
import net.simforge.airways.persistence.model.aircraft.Aircraft;
import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.airways.persistence.model.flight.AircraftAssignment;
import net.simforge.airways.persistence.model.flight.Flight;
import net.simforge.airways.persistence.model.flight.PilotAssignment;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.processes.flight.activity.FlightContext;
import net.simforge.airways.util.FlightTimeline;
import net.simforge.airways.util.SimpleFlight;
import net.simforge.airways.util.TimeMachine;
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

import static net.simforge.airways.engine.Result.When.NextMinute;

public class PilotOnDuty implements Activity {
    private static Logger logger = LoggerFactory.getLogger(PilotOnDuty.class);

    @Inject
    private Pilot pilot;
    @Inject
    private Engine engine;
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
                    logger.error("Pilot {} - Unable to find pilot assignment suitable for flight start");
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
                    return Result.resume(NextMinute);
                } else {
                    logger.error("Pilot {} - Unable to find start the flight");
                    // todo event log, cancellation?
                    return Result.done();
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

                case Flight.Status.PreFlight: // todo p2 start boarding at some moment
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
                    }
                    break;

                case Flight.Status.PostFlight: // todo p2 start deboarding at some moment
                    if (timeline.getFinish().getEstimatedTime().isBefore(now)) {
                        finishFlight(flightCtx);
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

                session.save(EventLog.make(pilot, "Pilot has started flight", flight));

                logger.info("Pilot {}, flight {} - flight started", pilot, flight);

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

//                pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

                aircraft.setStatus(Aircraft.Status.TaxiingOut);

                session.update(flight);
                session.update(pilot);
                session.update(aircraft);

                session.save(EventLog.make(pilot, "Aircraft departed from gate", flight, aircraft, flight.getFromAirport()));

                logger.info("Pilot {}, flight {} - aircraft {} departed from gate at {}", pilot, flight, aircraft, flight.getFromAirport());

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

//                pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

                Person person = pilot.getPerson();
                person.setPositionAirport(null);

                aircraft.setStatus(Aircraft.Status.Flying);
                Airport positionAirport = ctx.getAircraft().getPositionAirport();
                aircraft.setPositionAirport(null);
                aircraft.setPositionLatitude(positionAirport.getLatitude());
                aircraft.setPositionLongitude(positionAirport.getLongitude());

                session.update(flight);
                session.update(pilot);
                session.update(person);
                session.update(aircraft);

                session.save(EventLog.make(pilot, "Takeoff", flight, aircraft, flight.getFromAirport()));

                logger.info("Pilot {}, flight {} - aircraft {} took off at {}", pilot, flight, aircraft, flight.getFromAirport());

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

                    aircraft.setPositionLatitude(coords.getLat());
                    aircraft.setPositionLongitude(coords.getLon());

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

                person.setPositionAirport(flight.getToAirport());
//                pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

                aircraft.setPositionLatitude(null);
                aircraft.setPositionLongitude(null);
                aircraft.setPositionAirport(ctx.getFlight().getToAirport());
                aircraft.setStatus(Aircraft.Status.TaxiingIn);

                session.update(flight);
                session.update(pilot);
                session.update(person);
                session.update(aircraft);

                session.save(EventLog.make(pilot, "Landing", flight, aircraft, flight.getToAirport()));

                logger.info("Pilot {}, flight {} - aircraft {} landed at {}", pilot, flight, aircraft, flight.getToAirport());

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

//                pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

                aircraft.setStatus(Aircraft.Status.PostFlight);

                session.update(flight);
                session.update(pilot);
                session.update(aircraft);

                session.save(EventLog.make(pilot, "Aircraft arrived to gate", flight, aircraft, flight.getToAirport()));

                logger.info("Pilot {}, flight {} - aircraft {} arrived to gate", pilot, flight, aircraft);

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

                session.save(EventLog.make(pilot, "Flight finished", flight, aircraft, flight.getToAirport()));

                logger.info("Pilot {}, flight {} - flight finished", pilot, flight);

            });
        }
    }

}
