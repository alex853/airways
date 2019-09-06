/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage3;

import net.simforge.airways.stage2.status.Status;
import net.simforge.airways.stage2.status.StatusHandler;
import net.simforge.airways.stage3.model.aircraft.Aircraft;
import net.simforge.airways.stage3.model.aircraft.AircraftEntity;
import net.simforge.airways.stage3.model.aircraft.AircraftType;
import net.simforge.airways.stage3.model.flight.AircraftAssignment;
import net.simforge.airways.stage3.model.flight.Flight;
import net.simforge.airways.stage3.model.flight.PilotAssignment;
import net.simforge.airways.stage3.model.geo.Airport;
import net.simforge.airways.stage3.model.person.Person;
import net.simforge.airways.stage3.model.person.Pilot;
import net.simforge.airways.stage3.model.person.PilotEntity;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PilotTask extends StorageHeartbeatTask<Pilot> {

    private final StatusHandler statusHandler;
    private final PilotOps pilotOps;

    public PilotTask() {
        super("PilotTask");
        this.statusHandler = StatusHandler.create(this);
        this.pilotOps = new PilotOps(storage, Airways3App.getSessionFactory());
    }

    @Override
    protected void startup() {
        super.startup();

        setBaseSleepTime(5000);
        BM.setLoggingPeriod(600000);
    }

    @Override
    protected Pilot heartbeat(Pilot pilot) {
        BM.start("PilotTask.heartbeat");
        try {
            statusHandler.perform(StatusHandler.context(pilot));
            return pilot;
        } finally {
            BM.stop();
        }
    }

    @Override
    protected BaseOps<Pilot> getBaseOps() {
        return pilotOps;
    }

    @Status(code = Pilot.Status.Idle)
    private void idle(StatusHandler.Context<Pilot> ctx) {
        BM.start("PilotTask.idle");
        try {

            Pilot pilot = ctx.getSubject();

            List<PilotAssignment> pilotAssignments = pilotOps.getCachedPilotAssignments_assigned(pilot);

            if (pilotAssignments.isEmpty()) {
                logger.debug(String.format("Pilot %s does not have any assignment", pilot));
                pilotOps.arrangeHeartbeatIn(pilot, TimeUnit.DAYS.toMillis(1));
                return;
            }

            logger.debug(String.format("Pilot %s has %s assignment(s)", pilot, pilotAssignments.size()));

            PilotAssignment pilotAssignment = pilotAssignments.get(0);
            Flight flight = pilotAssignment.getFlight();

            FlightContext flightCtx = FlightContext.fromCache(flight);
            flight = flightCtx.getFlight();

            FlightTimeline timeline = FlightTimeline.byFlight(flight);
            LocalDateTime preflightStartsAtDt = timeline.getStart().getScheduledTime();

            if (flight.getStatus() == Flight.Status.Assigned
                    && preflightStartsAtDt.isBefore(JavaTime.nowUtc())) {
                startFlight(flightCtx);
            } else {
                pilotOps.arrangeHeartbeatAt(pilot, preflightStartsAtDt);
            }

        } finally {
            BM.stop();
        }
    }

    @Status(code = Pilot.Status.IdlePlanned)
    private void idlePlanned(StatusHandler.Context<Pilot> ctx) {
        idle(ctx);
    }

    @Status(code = Pilot.Status.OnDuty)
    private void onDuty(StatusHandler.Context<Pilot> ctx) {
        BM.start("PilotTask.onDuty");
        try {

            Pilot pilot = ctx.getSubject();

            //noinspection JpaQlInspection,unchecked
            PilotAssignment pilotAssignment = pilotOps.getCachedInProgressAssignment(pilot);

            logger.debug(String.format("Pilot %s is on %s assignment", pilot, pilotAssignment));

            Flight flight = pilotAssignment.getFlight();

            FlightContext flightCtx = FlightContext.fromCache(flight);
            flight = flightCtx.getFlight();

            FlightTimeline timeline = FlightTimeline.byFlight(flight);
            LocalDateTime now = JavaTime.nowUtc();

            switch (flight.getStatus()) {

                case Flight.Status.Planned:
                case Flight.Status.Assigned:
                case Flight.Status.Finished:
                    // error
                    throw new IllegalArgumentException("Can't process flight status " + flight.getStatus() + " for flight " + flight + " and pilot " + pilot);

                case Flight.Status.PreFlight:
                    if (timeline.getBlocksOff().getEstimatedTime().isBefore(now)) {
                        blocksOff(flightCtx);
                    } else {
                        pilotOps.arrangeHeartbeatIn(pilot, TimeUnit.MINUTES.toMillis(1));
                    }
                    break;

                case Flight.Status.Departure:
                    if (timeline.getTakeoff().getEstimatedTime().isBefore(now)) {
                        takeoff(flightCtx);
                    } else {
                        pilotOps.arrangeHeartbeatIn(pilot, TimeUnit.MINUTES.toMillis(1));
                    }
                    break;

                case Flight.Status.Flying:
                    fly(flightCtx);
                    break;

                case Flight.Status.Arrival:
                    if (timeline.getBlocksOn().getEstimatedTime().isBefore(now)) {
                        blocksOn(flightCtx);
                    } else {
                        pilotOps.arrangeHeartbeatIn(pilot, TimeUnit.MINUTES.toMillis(1));
                    }
                    break;

                case Flight.Status.PostFlight:
                    if (timeline.getFinish().getEstimatedTime().isBefore(now)) {
                        finishFlight(flightCtx);
                    } else {
                        pilotOps.arrangeHeartbeatIn(pilot, TimeUnit.MINUTES.toMillis(1));
                    }
                    break;

                default:
                    throw new IllegalStateException("Can't process flight status " + flight.getStatus() + " for flight " + flight + " and pilot " + pilot);
            }

        } finally {
            BM.stop();
        }
    }

    private void startFlight(FlightContext _ctx) {
        try (Session session = pilotOps.openSession()) {
            HibernateUtils.transaction(session, "PilotTask.startFlight", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx);

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Aircraft aircraft = ctx.getAircraft();
                PilotAssignment pilotAssignment = ctx.getPilotAssignment();
                AircraftAssignment aircraftAssignment = ctx.getAircraftAssignment();

                flight.setStatus(Flight.Status.PreFlight);

                pilot.setStatus(Pilot.Status.OnDuty);
                pilot.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

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
        try (Session session = pilotOps.openSession()) {
            HibernateUtils.transaction(session, "PilotTask.blocksOff", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx);

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Aircraft aircraft = ctx.getAircraft();

                flight.setStatus(Flight.Status.Departure);
                flight.setActualDepartureTime(JavaTime.nowUtc());

                pilot.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

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
        try (Session session = pilotOps.openSession()) {
            HibernateUtils.transaction(session, "PilotTask.takeoff", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx);

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Aircraft aircraft = ctx.getAircraft();

                flight.setStatus(Flight.Status.Flying);
                flight.setActualTakeoffTime(JavaTime.nowUtc());

                pilot.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));
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
        try {

            Flight flight = _ctx.getFlight();

            Airport fromAirport = storage.get(Airport.class, flight.getFromAirport().getId());
            Airport toAirport = storage.get(Airport.class, flight.getToAirport().getId());

            Geo.Coords fromCoords = new Geo.Coords(fromAirport.getLatitude(), fromAirport.getLongitude());
            Geo.Coords toCoords = new Geo.Coords(toAirport.getLatitude(), toAirport.getLongitude());

            AircraftType aircraftType = storage.get(AircraftType.class, _ctx.getAircraft().getType().getId());
            SimpleFlight simpleFlight = SimpleFlight.forRoute(
                    fromCoords,
                    toCoords,
                    aircraftType);

            Duration actualTimeSinceTakeoff = Duration.between(flight.getActualTakeoffTime(), JavaTime.nowUtc());

            SimpleFlight.Position aircraftPosition = simpleFlight.getAircraftPosition(actualTimeSinceTakeoff);

            if (aircraftPosition.getStage() != SimpleFlight.Position.Stage.AfterLanding) {

                try (Session session = pilotOps.openSession()) {
                    HibernateUtils.transaction(session, "PilotTask.fly#inAir", () -> {

                        Aircraft aircraft = session.load(AircraftEntity.class, _ctx.getAircraft().getId());
                        Pilot pilot = session.load(PilotEntity.class, _ctx.getPilot().getId());

                        Geo.Coords coords = aircraftPosition.getCoords();

                        aircraft.setPositionLatitude(coords.getLat());
                        aircraft.setPositionLongitude(coords.getLon());

                        pilot.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

                        session.update(aircraft);
                        session.update(pilot);
                    });
                }

            } else {

                landing(_ctx);

            }

        } finally {
            BM.stop();
        }
    }

    private void landing(FlightContext _ctx) {
        try (Session session = pilotOps.openSession()) {
            HibernateUtils.transaction(session, "PilotTask.landing", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx);

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Person person = pilot.getPerson();
                Aircraft aircraft = ctx.getAircraft();

                flight.setStatus(Flight.Status.Arrival);
                flight.setActualLandingTime(JavaTime.nowUtc());

                person.setPositionAirport(flight.getToAirport());
                pilot.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

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
        try (Session session = pilotOps.openSession()) {
            HibernateUtils.transaction(session, "PilotTask.blocksOn", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx);

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Aircraft aircraft = ctx.getAircraft();

                flight.setStatus(Flight.Status.PostFlight);
                flight.setActualArrivalTime(JavaTime.nowUtc());

                pilot.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

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
        try (Session session = pilotOps.openSession()) {
            HibernateUtils.transaction(session, "PilotTask.finishFlight", () -> {

                FlightContext ctx = FlightContext.load(session, _ctx);

                Flight flight = ctx.getFlight();
                Pilot pilot = ctx.getPilot();
                Aircraft aircraft = ctx.getAircraft();
                PilotAssignment pilotAssignment = ctx.getPilotAssignment();
                AircraftAssignment aircraftAssignment = ctx.getAircraftAssignment();

                flight.setStatus(Flight.Status.Finished);

                pilot.setStatus(Pilot.Status.Idle);
                pilot.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

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
