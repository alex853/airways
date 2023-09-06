package net.simforge.airways.processes.transportflight.event;

import net.simforge.airways.EventLog;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.ops.TransportFlightOps;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

@Subscribe(DeboardingCompleted.class)
public class DeboardingCompleted implements Event, Handler {
    private static final Logger log = LoggerFactory.getLogger(DeboardingCompleted.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {

                transportFlight = session.load(TransportFlight.class, transportFlight.getId());

                TransportFlightOps.checkAndSetStatus(transportFlight, TransportFlight.Status.Finished);
                session.update(transportFlight);

                EventLog.info(session, log, transportFlight, "Deboarding completed, FLIGHT FINISHED");

            });
        }
    }
}
