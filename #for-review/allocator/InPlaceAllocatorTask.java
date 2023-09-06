/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage1.allocator;

import net.simforge.airways.stage1.FlightContext;
import net.simforge.airways.stage1.FlightTimeline;
import net.simforge.airways.stage1.Util;
import net.simforge.airways.stage1.model.AircraftAssignment;
import net.simforge.airways.stage1.model.Flight;
import net.simforge.airways.stage1.model.Pilot;
import net.simforge.airways.stage1.model.PilotAssignment;
import net.simforge.airways.stage1.model.aircraft.Aircraft;
import net.simforge.commons.runtime.BaseTask;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.LocalDateTime;
import java.util.List;

public class InPlaceAllocatorTask extends BaseTask {
    private SessionFactory sessionFactory;

    public InPlaceAllocatorTask(SessionFactory sessionFactory) {
        super("InPlaceAllocator");
        this.sessionFactory = sessionFactory;
    }

    @Override
    protected void process() {
        List<Flight> flights;

        try (Session session = sessionFactory.openSession()) {
            //noinspection JpaQlInspection,unchecked
            flights = session
                    .createQuery("select f " +
                            "from Flight f " +
                            "where f.scheduledDepartureTime <= :threshold " +
                            "  and f.status = :planned " +
                            "order by f.scheduledDepartureTime asc")
                    .setParameter("threshold", JavaTime.nowUtc().plusDays(1))
                    .setInteger("planned", Flight.Status.Planned)
                    .setMaxResults(100)
                    .list();
        }

        logger.debug("Flights to allocate: " + flights.size());

        for (Flight flight : flights) {
            process(flight);
        }
    }

    private void process(Flight flight) {
        logger.debug("Flight " + flight + " - processing...");

        try (Session session = sessionFactory.openSession()) {
            FlightContext ctx = FlightContext.load(session, flight);

            LocalDateTime scheduledDepartureTime = flight.getScheduledDepartureTime();
            LocalDateTime cancellationThreshold = scheduledDepartureTime.plusHours(1);

            if (cancellationThreshold.isBefore(JavaTime.nowUtc())) {
                logger.info("Flight " + flight + " - CANCELLING: cancellation time is " + cancellationThreshold + ", scheduled departure time is " + flight.getScheduledDepartureTime());
                cancel(session, ctx);
                return;
            }

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
                ctx.getFlight().setStatus(Flight.Status.Assigned);
                Util.update(session, ctx.getFlight());
            }
        }
    }

    private boolean allocateAircraft(Session session, FlightContext ctx) {
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

                Util.save(session, aircraftAssignment);

                return true;
            }
        }

        return false;
    }

    private Schedule loadAircraftSchedule(Session session, Aircraft aircraft) {
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
    }

    private boolean allocatePilot(Session session, FlightContext ctx) {
        //noinspection JpaQlInspection,unchecked
        List<Pilot> pilots = session
                .createQuery("select p " +
                        "from Pilot p")
                .list();

        FlightTimeline timeline = FlightTimeline.byFlight(ctx.getFlight());
        LocalDateTime estimatedStartTime = timeline.getStart().getEstimatedTime();
        LocalDateTime estimatedFinishTime = timeline.getFinish().getEstimatedTime();

        Activity flightActivity = Activity.forFlight(ctx.getFlight().getFromAirport(), estimatedStartTime, ctx.getFlight().getToAirport(), estimatedFinishTime);

        for (Pilot pilot : pilots) {
            Schedule schedule = loadPilotSchedule(session, pilot);

            if (schedule.isActivitySuitable(flightActivity)) {
                PilotAssignment pilotAssignment = new PilotAssignment();
                pilotAssignment.setFlight(ctx.getFlight());
                pilotAssignment.setPilot(pilot);
                pilotAssignment.setRole("Captain");
                pilotAssignment.setStatus(PilotAssignment.Status.Assigned);

                Util.save(session, pilotAssignment);

                return true;
            }
        }

        return false;
    }

    private Schedule loadPilotSchedule(Session session, Pilot pilot) {
        Schedule schedule = new Schedule();

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
                ? new InAirportState(pilot.getPositionAirport())
                : new FlyingState());

        return schedule;
    }

    private void cancel(Session session, FlightContext ctx) {
        ctx.getFlight().setStatus(Flight.Status.Cancelled);

        if (ctx.getAircraftAssignment() != null) {
            ctx.getAircraftAssignment().setStatus(AircraftAssignment.Status.Cancelled);
        }

        if (ctx.getPilotAssignment() != null) {
            ctx.getPilotAssignment().setStatus(PilotAssignment.Status.Cancelled);
        }

        Util.update(session, ctx.getFlight(), ctx.getAircraftAssignment(), ctx.getPilotAssignment());
    }
}
