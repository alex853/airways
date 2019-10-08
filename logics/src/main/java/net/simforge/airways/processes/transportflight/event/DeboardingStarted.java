/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.event;

import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.activity.Deboarding;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

@Subscribe(DeboardingStarted.class)
public class DeboardingStarted implements Event, Handler {
    private static Logger logger = LoggerFactory.getLogger(DeboardingStarted.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {

                transportFlight = session.load(TransportFlight.class, transportFlight.getId());

                transportFlight.setStatus(TransportFlight.Status.Deboarding);
                session.update(transportFlight);

                engine.startActivity(Deboarding.class, transportFlight);

                session.save(EventLog.make(transportFlight, "Deboarding started"));
                logger.info(transportFlight + " - Deboarding started");

            });
        }
    }

}
