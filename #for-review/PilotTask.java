/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage2;

import net.simforge.airways.stage2.model.flight.AircraftAssignment;
import net.simforge.airways.stage2.model.flight.Flight;
import net.simforge.airways.stage2.model.Pilot;
import net.simforge.airways.stage2.model.flight.PilotAssignment;
import net.simforge.airways.stage2.model.aircraft.Aircraft;
import net.simforge.airways.stage2.model.aircraft.AircraftType;
import net.simforge.airways.stage2.model.geo.Airport;
import net.simforge.commons.HeartbeatTask;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PilotTask extends HeartbeatTask<Pilot> {

    private final SessionFactory sessionFactory;
    private Session session;

    @SuppressWarnings("unused")
    public PilotTask() {
        this(AirwaysApp.getSessionFactory());
    }

    private PilotTask(SessionFactory sessionFactory) {
        super("Pilot", sessionFactory);
        this.sessionFactory = sessionFactory;
    }

    @Override
    protected void startup() {
        super.startup();

        BM.setLoggingPeriod(ChronoUnit.HOURS.getDuration().toMillis());
    }

    @Override
    protected Pilot heartbeat(Pilot pilot) {
        BM.start("PilotTask.heartbeat");
        try (Session session = sessionFactory.openSession()) {
            this.session = session;

            pilot = session.get(Pilot.class, pilot.getId());

            logger.debug(String.format("Pilot %s heartbeat - status %s", pilot, pilot.getStatus()));

            switch (pilot.getStatus()) {
                case Pilot.Status.Idle:
                    idle(pilot);
                    break;
                case Pilot.Status.OnDuty:
                    onDuty(pilot);
                    break;
                default:
                    throw new IllegalStateException("Unsupported pilot status " + pilot.getStatus());
            }

            return pilot;
        } finally {
            this.session = null;
            BM.stop();
        }
    }

    private void idle(Pilot pilot) {
        BM.start("PilotTask.idle");
        try {

            List<PilotAssignment> pilotAssignments = PilotOps.loadPilotAssignments(session, pilot);

            if (pilotAssignments.isEmpty()) {
                logger.debug(String.format("Pilot %s does not have any assignment", pilot));
                setNextHeartbeatDtInMillis(session, pilot, TimeUnit.DAYS.toMillis(1));
                return;
            }

            logger.debug(String.format("Pilot %s has %s assignment(s)", pilot, pilotAssignments.size()));

            PilotAssignment pilotAssignment = pilotAssignments.get(0);
            Flight flight = pilotAssignment.getFlight();
            FlightContext ctx = FlightContext.load(session, flight);

            FlightTimeline timeline = FlightTimeline.byFlight(flight);
            LocalDateTime preflightStartsAtDt = timeline.getStart().getScheduledTime();

            if (flight.getStatus() == Flight.Status.Assigned
                    && preflightStartsAtDt.isBefore(JavaTime.nowUtc())) {
                startFlight(ctx);
            } else {
                setNextHeartbeatDt(session, pilot, preflightStartsAtDt);
            }

        } finally {
            BM.stop();
        }
    }

    private void onDuty(Pilot pilot) {
        BM.start("PilotTask.onDuty");
        try {

            //noinspection JpaQlInspection,unchecked
            PilotAssignment pilotAssignment = PilotOps.loadInProgressPilotAssignment(session, pilot);

            logger.debug(String.format("Pilot %s is on %s assignment", pilot, pilotAssignment));

            Flight flight = pilotAssignment.getFlight();
            FlightContext ctx = FlightContext.load(session, flight);

            FlightTimeline timeline = FlightTimeline.byFlight(flight);
            LocalDateTime now = JavaTime.nowUtc();

            if (flight.getStatus() == Flight.Status.Planned) {

                // error
                throw new IllegalArgumentException(); // todo AK

            } else if (flight.getStatus() == Flight.Status.Assigned) {

                // error
                throw new IllegalArgumentException(); // todo AK

            } else if (flight.getStatus() == Flight.Status.PreFlight) {

                if (timeline.getBlocksOff().getEstimatedTime().isBefore(now)) {
                    blocksOff(ctx);
                } else {
                    planHeartbeat(pilot);
                }

            } else if (flight.getStatus() == Flight.Status.Departure) {

                if (timeline.getTakeoff().getEstimatedTime().isBefore(now)) {
                    takeoff(ctx);
                } else {
                    planHeartbeat(pilot);
                }

            } else if (flight.getStatus() == Flight.Status.Flying) {

                fly(ctx);

            } else if (flight.getStatus() == Flight.Status.Arrival) {

                if (timeline.getBlocksOn().getEstimatedTime().isBefore(now)) {
                    blocksOn(ctx);
                } else {
                    planHeartbeat(pilot);
                }

            } else if (flight.getStatus() == Flight.Status.PostFlight) {

                if (timeline.getFinish().getEstimatedTime().isBefore(now)) {
                    finishFlight(ctx);
                } else {
                    planHeartbeat(pilot);
                }

            } else if (flight.getStatus() == Flight.Status.Finished) {

                // todo AK search for next assignment or go back to Idle status
                // error
                throw new IllegalArgumentException(); // todo AK

            }

        } finally {
            BM.stop();
        }

    }

    private void startFlight(FlightContext ctx) {
        BM.start("PilotTask.startFlight");
        try {

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

            HibernateUtils.updateAndCommit(session,
                    "PilotTask.startFlight#update",
                    pilot,
                    pilotAssignment,
                    flight,
                    aircraft,
                    aircraftAssignment);

            logger.info(String.format("Pilot %s has started flight %s", pilot, flight));

        } finally {
            BM.stop();
        }
    }

    private void blocksOff(FlightContext ctx) {
        BM.start("PilotTask.blocksOff");
        try {

            ctx.getFlight().setStatus(Flight.Status.Departure);
            ctx.getFlight().setActualDepartureTime(JavaTime.nowUtc());

            ctx.getAircraft().setStatus(Aircraft.Status.TaxiingOut);

            ctx.getPilot().setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

            HibernateUtils.updateAndCommit(session, ctx.getFlight(), ctx.getAircraft(), ctx.getPilot());

            logger.info(String.format("Pilot %s has started taxiing out", ctx.getPilot()));

        } finally {
            BM.stop();
        }

    }

    private void takeoff(FlightContext ctx) {
        BM.start("PilotTask.takeoff");
        try {

            ctx.getFlight().setStatus(Flight.Status.Flying);
            ctx.getFlight().setActualTakeoffTime(JavaTime.nowUtc());

            ctx.getAircraft().setStatus(Aircraft.Status.Flying);

            Airport positionAirport = ctx.getAircraft().getPositionAirport();
            ctx.getAircraft().setPositionAirport(null);
            ctx.getAircraft().setPositionLatitude(positionAirport.getLatitude());
            ctx.getAircraft().setPositionLongitude(positionAirport.getLongitude());

            ctx.getPilot().getPerson().setPositionAirport(null);
            ctx.getPilot().setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

            HibernateUtils.updateAndCommit(session, ctx.getFlight(), ctx.getAircraft(), ctx.getPilot(), ctx.getPilot().getPerson());

            logger.info(String.format("Pilot %s has took aircraft %s off at %s", ctx.getPilot(), ctx.getAircraft(), ctx.getFlight().getFromAirport()));

        } finally {
            BM.stop();
        }
    }

    private void fly(FlightContext ctx) {
        BM.start("PilotTask.fly");
        try {

            Flight flight = ctx.getFlight();

            Airport fromAirport = flight.getFromAirport();
            Airport toAirport = flight.getToAirport();

            Geo.Coords fromCoords = new Geo.Coords(fromAirport.getLatitude(), fromAirport.getLongitude());
            Geo.Coords toCoords = new Geo.Coords(toAirport.getLatitude(), toAirport.getLongitude());

            AircraftType aircraftType = ctx.getAircraft().getType();
            SimpleFlight simpleFlight = SimpleFlight.forRoute(
                    fromCoords,
                    toCoords,
                    aircraftType);

            Duration actualTimeSinceTakeoff = Duration.between(flight.getActualTakeoffTime(), JavaTime.nowUtc());

            SimpleFlight.Position aircraftPosition = simpleFlight.getAircraftPosition(actualTimeSinceTakeoff);

            if (aircraftPosition.getStage() != SimpleFlight.Position.Stage.AfterLanding) {
                Geo.Coords coords = aircraftPosition.getCoords();

                ctx.getAircraft().setPositionLatitude(coords.getLat());
                ctx.getAircraft().setPositionLongitude(coords.getLon());

                ctx.getPilot().setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

                HibernateUtils.updateAndCommit(session, ctx.getAircraft(), ctx.getPilot());
            } else {
                landing(ctx);
            }

        } finally {
            BM.stop();
        }
    }

    private void landing(FlightContext ctx) {
        BM.start("PilotTask.landing");
        try {

            ctx.getAircraft().setPositionLatitude(null);
            ctx.getAircraft().setPositionLongitude(null);
            ctx.getAircraft().setPositionAirport(ctx.getFlight().getToAirport());
            ctx.getAircraft().setStatus(Aircraft.Status.TaxiingOut);

            ctx.getFlight().setActualLandingTime(JavaTime.nowUtc());
            ctx.getFlight().setStatus(Flight.Status.Arrival);

            ctx.getPilot().getPerson().setPositionAirport(ctx.getFlight().getToAirport());
            ctx.getPilot().setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

            HibernateUtils.updateAndCommit(session, ctx.getFlight(), ctx.getAircraft(), ctx.getPilot(), ctx.getPilot().getPerson());

            logger.info(String.format("Pilot %s has landed aircraft %s off at %s", ctx.getPilot(), ctx.getAircraft(), ctx.getFlight().getFromAirport()));

        } finally {
            BM.stop();
        }
    }

    private void blocksOn(FlightContext ctx) {
        BM.start("PilotTask.blocksOn");
        try {

            ctx.getFlight().setStatus(Flight.Status.PostFlight);
            ctx.getFlight().setActualArrivalTime(JavaTime.nowUtc());

            ctx.getAircraft().setStatus(Aircraft.Status.PostFlight);

            ctx.getPilot().setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

            HibernateUtils.updateAndCommit(session, ctx.getFlight(), ctx.getAircraft(), ctx.getPilot());

        } finally {
            BM.stop();
        }
    }

    private void finishFlight(FlightContext ctx) {
        BM.start("PilotTask.finishFlight");
        try {

            ctx.getFlight().setStatus(Flight.Status.Finished);

            ctx.getAircraft().setStatus(Aircraft.Status.Idle);

            ctx.getPilotAssignment().setStatus(PilotAssignment.Status.Done);
            ctx.getAircraftAssignment().setStatus(AircraftAssignment.Status.Done);

            ctx.getPilot().setStatus(Pilot.Status.Idle);
            ctx.getPilot().setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

            HibernateUtils.updateAndCommit(session, ctx.getFlight(), ctx.getAircraft(), ctx.getPilotAssignment(), ctx.getAircraftAssignment(), ctx.getPilot());

        } finally {
            BM.stop();
        }
    }

    @Deprecated
    private void planHeartbeat(Pilot pilot) {
        pilot.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));
        HibernateUtils.updateAndCommit(session, "PilotTask.planHeartbeat", pilot);
    }
}
