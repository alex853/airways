/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage2;

import net.simforge.airways.stage2.flightallocation.Activity;
import net.simforge.airways.stage2.flightallocation.FlyingState;
import net.simforge.airways.stage2.flightallocation.InAirportState;
import net.simforge.airways.stage2.flightallocation.Schedule;
import net.simforge.airways.stage2.model.Pilot;
import net.simforge.airways.stage2.model.aircraft.Aircraft;
import net.simforge.airways.stage2.model.flight.AircraftAssignment;
import net.simforge.airways.stage2.model.flight.Flight;
import net.simforge.airways.stage2.model.flight.PilotAssignment;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FlightOps {

    private static Logger logger = LoggerFactory.getLogger(FlightOps.class.getName());

    private static List<Pilot> cachedPilots;
    private static final Map<Integer, Schedule> pilotSchedules = new ConcurrentHashMap<>();

    public static void allocateFlight(Session session, Flight flight) {
        HibernateUtils.transaction(session, "FlightOps.allocateFlight", ()-> {

            logger.debug("Flight " + flight + " - allocating...");

            FlightContext ctx = FlightContext.load(session, flight);

            AircraftAssignment aircraftAssignment = ctx.getAircraftAssignment();
            if (aircraftAssignment == null) {
                if (allocateAircraft(session, ctx)) {
                    ctx = FlightContext.load(session, flight);
                }
            }

            PilotAssignment pilotAssignment = ctx.getPilotAssignment();
            if (pilotAssignment == null) {
                if (allocatePilot(session, ctx)) {
                    ctx = FlightContext.load(session, flight);
                }
            }

            if (ctx.isFullyAssigned()) {
                flight.setStatus(Flight.Status.Assigned);
                flight.setHeartbeatDt(null);
                session.update(flight);

                EventLog.saveLog(session, flight, "Flight is fully assigned");

                logger.info("Flight " + ctx.getFlight() + " - ASSIGNED");

                // reset pilot heartbeat when pilot assignment changed
                Pilot pilot = ctx.getPilot();
                pilot.setHeartbeatDt(JavaTime.nowUtc());
            }
        });
    }

    private static boolean allocateAircraft(Session session, FlightContext ctx) {
        BM.start("FlightOps.allocateAircraft");
        try {

            //noinspection JpaQlInspection,unchecked
            List<Aircraft> aircrafts = session
                    .createQuery("select a " +
                            "from Aircraft a " +
                            "where a.type = :aircraftType")
                    .setEntity("aircraftType", ctx.getFlight().getAircraftType())
                    .list();

            FlightTimeline timeline = FlightTimeline.byFlight(ctx.getFlight());
            LocalDateTime estimatedStartTime = timeline.getStart().getEstimatedTime();
            LocalDateTime estimatedFinishTime = timeline.getFinish().getEstimatedTime();

            Activity flightActivity = Activity.forFlight(ctx.getFlight().getFromAirport(), estimatedStartTime, ctx.getFlight().getToAirport(), estimatedFinishTime);

            for (Aircraft aircraft : aircrafts) {
                Schedule schedule = loadAircraftSchedule(session, aircraft);

                if (schedule.isActivitySuitable(flightActivity)) {
                    AircraftAssignment aircraftAssignment = new AircraftAssignment();
                    aircraftAssignment.setFlight(ctx.getFlight());
                    aircraftAssignment.setAircraft(aircraft);
                    aircraftAssignment.setStatus(AircraftAssignment.Status.Assigned);

                    session.save(aircraftAssignment);

                    EventLog.saveLog(session, ctx.getFlight(), "Aircraft is assigned to flight", aircraft);
                    EventLog.saveLog(session, aircraft, "Aircraft is assigned to flight", ctx.getFlight());

                    logger.info("Flight " + ctx.getFlight() + " - aicraft " + aircraft.getRegNo() + " allocated");

                    return true;
                }
            }

            return false;

        } finally {
            BM.stop();
        }
    }

    private static Schedule loadAircraftSchedule(Session session, Aircraft aircraft) {
        BM.start("FlightOps.loadAircraftSchedule");
        try {

            Schedule schedule = new Schedule();

            //noinspection JpaQlInspection,unchecked
            List<AircraftAssignment> assignments = session
                    .createQuery("select aa " +
                            "from AircraftAssignment aa " +
                            "where aa.aircraft = :aircraft " +
                            "  and aa.status != :cancelled " +
                            "order by aa.flight.scheduledDepartureTime asc")
                    .setEntity("aircraft", aircraft)
                    .setInteger("cancelled", AircraftAssignment.Status.Cancelled)
                    .list();

            for (AircraftAssignment assignment : assignments) {
                Flight flight = assignment.getFlight();
                FlightTimeline timeline = FlightTimeline.byFlight(flight);

                LocalDateTime startTime = timeline.getStart().getEstimatedTime(); // 1todo AK estimated / actual time in relation with flight status
                LocalDateTime finishTime = timeline.getFinish().getEstimatedTime();

                Activity activity = Activity.forFlight(flight.getFromAirport(), startTime, flight.getToAirport(), finishTime);

                schedule.add(activity);
            }

            schedule.setCurrentState(aircraft.getStatus() == Aircraft.Status.Idle
                    ? new InAirportState(aircraft.getPositionAirport())
                    : new FlyingState());

            return schedule;

        } finally {
            BM.stop();
        }
    }

    private static boolean allocatePilot(Session session, FlightContext ctx) {
        BM.start("FlightOps.allocatePilot");
        try {

            List<Pilot> pilots;
            if (cachedPilots != null) {
                pilots = cachedPilots;
            } else {
                pilots = PilotOps.loadAllPilots(session);
                cachedPilots = pilots;
            }

            FlightTimeline timeline = FlightTimeline.byFlight(ctx.getFlight());
            LocalDateTime estimatedStartTime = timeline.getStart().getEstimatedTime();
            LocalDateTime estimatedFinishTime = timeline.getFinish().getEstimatedTime();

            Activity flightActivity = Activity.forFlight(ctx.getFlight().getFromAirport(), estimatedStartTime, ctx.getFlight().getToAirport(), estimatedFinishTime);

            for (Pilot pilot : pilots) {
                Schedule schedule;

                Schedule cachedSchedule = pilotSchedules.get(pilot.getId());
                if (cachedSchedule != null) {
                    if (cachedSchedule.isActivitySuitable(flightActivity)) {
                        schedule = loadPilotSchedule(session, pilot);
                        pilotSchedules.put(pilot.getId(), schedule);
                    } else {
                        schedule = cachedSchedule;
                    }
                } else {
                    schedule = loadPilotSchedule(session, pilot);
                    pilotSchedules.put(pilot.getId(), schedule);
                }

                if (schedule.isActivitySuitable(flightActivity)) {
                    PilotAssignment pilotAssignment = new PilotAssignment();
                    pilotAssignment.setFlight(ctx.getFlight());
                    pilotAssignment.setPilot(pilot);
                    pilotAssignment.setRole("Captain");
                    pilotAssignment.setStatus(PilotAssignment.Status.Assigned);

                    session.save(pilotAssignment);

                    EventLog.saveLog(session, ctx.getFlight(), "Pilot is assigned to flight", pilot);
                    EventLog.saveLog(session, pilot, "Pilot is assigned to flight", ctx.getFlight());

                    logger.info("Flight " + ctx.getFlight() + " - pilot " + pilot.getId() + " allocated");

                    pilotSchedules.remove(pilot.getId());

                    return true;
                }
            }

            return false;

        } finally {
            BM.stop();
        }
    }

    private static Schedule loadPilotSchedule(Session session, Pilot pilot) {
        BM.start("FlightOps.loadPilotSchedule");
        try {

            Schedule schedule = new Schedule();

            pilot = session.load(Pilot.class, pilot.getId());

            //noinspection JpaQlInspection,unchecked
            List<PilotAssignment> assignments = session
                    .createQuery("select pa " +
                            "from PilotAssignment pa " +
                            "where pa.pilot = :pilot " +
                            "  and pa.status != :cancelled " +
                            "order by pa.flight.scheduledDepartureTime asc")
                    .setEntity("pilot", pilot)
                    .setInteger("cancelled", PilotAssignment.Status.Cancelled)
                    .list();

            for (PilotAssignment assignment : assignments) {
                Flight flight = assignment.getFlight();
                FlightTimeline timeline = FlightTimeline.byFlight(flight);

                LocalDateTime startTime = timeline.getStart().getEstimatedTime(); // 1todo AK estimated / actual time in relation with flight status
                LocalDateTime finishTime = timeline.getFinish().getEstimatedTime();

                Activity activity = Activity.forFlight(flight.getFromAirport(), startTime, flight.getToAirport(), finishTime);

                schedule.add(activity);
            }

            schedule.setCurrentState(pilot.getStatus() == Aircraft.Status.Idle
                    ? new InAirportState(pilot.getPerson().getPositionAirport())
                    : new FlyingState());

            return schedule;

        } finally {
            BM.stop();
        }
    }

    public static void cancelFlight(Session session, final Flight _flight, String reason) {
        HibernateUtils.transaction(session, "FlightOps.cancel", () -> {
            FlightContext ctx = FlightContext.load(session, _flight);

            Flight flight = ctx.getFlight();
            AircraftAssignment aircraftAssignment = ctx.getAircraftAssignment();
            PilotAssignment pilotAssignment = ctx.getPilotAssignment();

            flight.setStatus(Flight.Status.Cancelled);
            flight.setHeartbeatDt(null);
            session.update(flight);

            String msg = "Flight cancelled, reason is '" + reason + "'";
            EventLog.saveLog(session, flight, msg);

            if (aircraftAssignment != null) {
                aircraftAssignment.setStatus(AircraftAssignment.Status.Cancelled);
                session.update(aircraftAssignment);

                EventLog.saveLog(session, aircraftAssignment.getAircraft(), msg, flight);
            }

            if (pilotAssignment != null) {
                pilotAssignment.setStatus(PilotAssignment.Status.Cancelled);
                session.update(pilotAssignment);

                // reset pilot heartbeat when pilot assignment changed
                Pilot pilot = pilotAssignment.getPilot();
                pilot.setHeartbeatDt(JavaTime.nowUtc());

                EventLog.saveLog(session, pilot, msg, flight);

                pilotSchedules.remove(pilot.getId());
            }

            logger.info("Flight " + ctx.getFlight() + " - CANCELLED");
        });
    }
}
