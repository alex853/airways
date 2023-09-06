package net.simforge.airways.processes.flight.handler;

import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.processes.flight.activity.AllocateFlight;
import net.simforge.airways.processes.flight.event.Planned;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * It initiates allocation process.
 */
@Subscribe(Planned.class)
public class OnPlanned implements Handler {
    private static final Logger log = LoggerFactory.getLogger(OnPlanned.class);

    @Inject
    private Flight flight;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;

    public void process() {
        scheduling.startActivity(AllocateFlight.class, flight, flight.getScheduledDepartureTime().minusHours(2));

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> EventLog.info(session, log, flight, "Flight Allocation initiated"));
        }
    }
}
