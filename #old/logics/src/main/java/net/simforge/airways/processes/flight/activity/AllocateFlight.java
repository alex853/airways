package net.simforge.airways.processes.flight.activity;

import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.Result;
import net.simforge.airways.processengine.activity.Activity;
import net.simforge.airways.processengine.activity.ActivityInfo;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.processes.flight.event.CancelDueToNoAllocation;
import net.simforge.airways.processes.flight.event.FullyAllocated;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * This activity allocates or configures allocation process for the flight.
 */
public class AllocateFlight implements Activity {
    private static final Logger log = LoggerFactory.getLogger(AllocateFlight.class);

    @Inject
    private Flight flight;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Result act() {
        // Currently, for simplicity, it starts trivial allocation with quite narrow time frame

        try (Session session = sessionFactory.openSession()) {
            FlightContext flightContext = FlightContext.load(session, flight);
            if (flightContext.isFullyAllocated()) {
                scheduling.fireEvent(FullyAllocated.class, flight);
                return Result.done();
            }

            ActivityInfo allocationActivity = scheduling.findActivity(TrivialAllocation.class, flight);
            if (allocationActivity == null) {
                scheduling.scheduleActivity(TrivialAllocation.class, flight, flight.getScheduledDepartureTime().minusHours(6), flight.getScheduledDepartureTime().minusHours(3));
            }

            return Result.resume(Result.When.FewTimesPerHour);
        }
    }

    public Result onExpiry() {
        FlightContext flightContext;
        try (Session session = sessionFactory.openSession()) {
            flightContext = FlightContext.load(session, flight);

            boolean isFullyAllocated = flightContext.isFullyAllocated();
            if (isFullyAllocated) {
                scheduling.fireEvent(FullyAllocated.class, flight);
            } else {
                scheduling.fireEvent(CancelDueToNoAllocation.class, flight); // todo AK add some explanation why it is not fully allocated
                HibernateUtils.transaction(session, () -> EventLog.warn(session, log, flight, "Unable to fully allocate the flight"));
            }
        }
        return Result.nothing();
    }
}
