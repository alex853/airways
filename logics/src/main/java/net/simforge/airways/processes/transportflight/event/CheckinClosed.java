/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.event;

import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.activity.ActivityInfo;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.activity.Checkin;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * 'CheckinClosed' event means that check-in finished. No more passengers can check-in to the flight.
 */
@Subscribe(CheckinClosed.class)
public class CheckinClosed implements Event, Handler {
    private static Logger logger = LoggerFactory.getLogger(CheckinClosed.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;

    public void process() {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {

                transportFlight = session.load(TransportFlight.class, transportFlight.getId());

                transportFlight.setStatus(TransportFlight.Status.WaitingForBoarding);
                session.update(transportFlight);

                ActivityInfo checkinActivity = engine.findActivity(Checkin.class, transportFlight);
                if (!checkinActivity.isFinished()) {
                    engine.stopActivity(checkinActivity);
                }

                session.save(EventLog.make(transportFlight, "Check-in closed"));
                logger.info(transportFlight + " - Check-in closed");

            });
        }
    }

    // todo p3 non-checked-in journeys go to "too late for checkin"
}
