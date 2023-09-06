package net.simforge.airways.processes.transportflight.event;

import net.simforge.airways.EventLog;
import net.simforge.airways.ops.TransportFlightOps;
import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.activity.Deboarding;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

@Subscribe(DeboardingStarted.class)
public class DeboardingStarted implements Event, Handler {
    private static final Logger log = LoggerFactory.getLogger(DeboardingStarted.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {

                transportFlight = session.load(TransportFlight.class, transportFlight.getId());

                TransportFlightOps.checkAndSetStatus(transportFlight, TransportFlight.Status.Deboarding);
                session.update(transportFlight);

                scheduling.startActivity(Deboarding.class, transportFlight);

                EventLog.info(session, log, transportFlight, "Deboarding started");

            });
        }
    }

}
