/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.activity;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.flight.Flight;
import net.simforge.airways.processes.flight.event.FullyAllocated;
import net.simforge.airways.processes.flight.event.Cancelled;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;

/**
 * This activity allocates or configures allocation process for the flight.
 */
public class AllocateFlight implements Activity {
    @Inject
    private Flight flight;
    @Inject
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Result act() {
        // Currently, for simplicity, it starts trivial allocation with quite narrow time frame
        engine.scheduleActivity(TrivialAllocation.class, flight, flight.getScheduledDepartureTime().minusHours(6), flight.getScheduledDepartureTime().minusHours(3));
        return Result.ok(Result.NextRun.DoNotRun);
    }

    public Result onExpiry() {
        FlightContext flightContext;
        try (Session session = sessionFactory.openSession()) {
            flightContext = FlightContext.load(session, flight);

            boolean isFullyAllocated = flightContext.isFullyAllocated();
            if (isFullyAllocated) {
                engine.fireEvent(FullyAllocated.class, flight);
                HibernateUtils.saveAndCommit(session, EventLog.make(flight, "Flight is fully allocated"));
            } else {
                engine.fireEvent(Cancelled.class, flight); // todo p3 СancelDueToNoAllocation instead of Cancelled?
                HibernateUtils.saveAndCommit(session, EventLog.make(flight, "Unable to fully allocate the flight"));
            }
        }
        return null;
    }
}
