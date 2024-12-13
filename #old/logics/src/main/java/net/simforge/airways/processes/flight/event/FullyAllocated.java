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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

@Subscribe(FullyAllocated.class)
public class FullyAllocated implements Event, Handler {
    private static final Logger log = LoggerFactory.getLogger(FullyAllocated.class);

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

                EventLog.info(session, log, this.flight, "Flight is fully allocated");
            });
        }
    }
}
