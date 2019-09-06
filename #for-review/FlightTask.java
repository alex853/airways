/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage2;

import net.simforge.airways.stage2.model.flight.Flight;
import net.simforge.airways.stage2.status.Otherwise;
import net.simforge.airways.stage2.status.Status;
import net.simforge.airways.stage2.status.StatusHandler;
import net.simforge.commons.HeartbeatTask;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class FlightTask extends HeartbeatTask<Flight> {

    private final SessionFactory sessionFactory;
    private final StatusHandler statusHandler;

    @SuppressWarnings("unused")
    public FlightTask() {
        this(AirwaysApp.getSessionFactory());
    }

    private FlightTask(SessionFactory sessionFactory) {
        super("Flight", sessionFactory);
        this.sessionFactory = sessionFactory;
        this.statusHandler = StatusHandler.create(this);
    }

    @Override
    protected void startup() {
        super.startup();

        BM.setLoggingPeriod(ChronoUnit.HOURS.getDuration().toMillis());
    }

    @Override
    protected Flight heartbeat(Flight _flight) {
        BM.start("FlightTask.heartbeat");
        try (Session session = sessionFactory.openSession()) {
            Flight flight = session.get(Flight.class, _flight.getId());

            statusHandler.perform(StatusHandler.context(flight, session));

            return flight;
        } finally {
            BM.stop();
        }
    }

    @SuppressWarnings("unused")
    @Status(code = Flight.Status.Planned)
    private void planned(StatusHandler.Context<Flight> ctx) {
        BM.start("FlightTask.planned");
        try {

            Flight flight = ctx.getSubject();
            Session session = ctx.get(Session.class);

            logger.debug("Flight " + flight + " - status: planned");

            LocalDateTime allocationDeadline = flight.getScheduledDepartureTime().minusHours(1);
            LocalDateTime now = JavaTime.nowUtc();

            if (allocationDeadline.isBefore(now)) {
                FlightOps.cancelFlight(session, flight, "Allocation deadline");
                return;
            }

            int allocationWindowDays = 3;
            LocalDateTime startOfAllocationWindow = allocationDeadline.minusDays(allocationWindowDays);

            if (now.isBefore(startOfAllocationWindow)) {
                setNextHeartbeatDt(session, flight, startOfAllocationWindow);
                return;
            }

            FlightOps.allocateFlight(session, flight);

            FlightContext flightCtx = FlightContext.load(session, flight);
            flight = flightCtx.getFlight();
            if (!flightCtx.isFullyAssigned()) {
                setNextHeartbeatDtInMillis(session, flight, TimeUnit.HOURS.toMillis(1));
            }

        } finally {
            BM.stop();
        }
    }

    @SuppressWarnings("unused")
    @Otherwise
    private void otherwise(StatusHandler.Context<Flight> ctx) {
        BM.start("FlightTask.otherwise");
        try {

            Flight flight = ctx.getSubject();
            Session session = ctx.get(Session.class);

            logger.warn("Flight " + flight + " - OTHERWISE handler - removing heartbeat");

            HibernateUtils.transaction(session, () -> {
                flight.setHeartbeatDt(null);
                session.update(flight);

                EventLog.saveLog(session, flight, "WARN - Otherwise handler");
            });

        } finally {
            BM.stop();
        }
    }
}
