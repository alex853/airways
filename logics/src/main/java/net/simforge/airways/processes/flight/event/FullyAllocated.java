/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.event;

import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.processes.flight.activity.FlightContext;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;

@Subscribe(FullyAllocated.class)
public class FullyAllocated implements Event, Handler {
    @Inject
    private Flight flight;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        try (Session session = sessionFactory.openSession()) {
            FlightContext flightContext = FlightContext.load(session, flight);

            HibernateUtils.transaction(session, () -> {
                Flight flight = flightContext.getFlight();
                flight.setStatus(Flight.Status.Assigned);
                session.update(flight);

                session.save(EventLog.make(this.flight, "Flight is fully allocated"));
            });
        }
    }
}
