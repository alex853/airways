/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.handler;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.activity.ActivityInfo;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.activity.Checkin;
import net.simforge.airways.processes.transportflight.event.CheckinClosed;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * It stops check-in for the flight. Boarding will be started by pilot's command.
 */
@Subscribe(CheckinClosed.class)
public class OnCheckinClosed implements Handler {
    private static Logger logger = LoggerFactory.getLogger(OnCheckinClosed.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;

    public void process() {
        ActivityInfo checkinActivity = engine.findActivity(Checkin.class, transportFlight);
        if (!checkinActivity.isDone()) {
            engine.stopActivity(checkinActivity);
        }

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, EventLog.make(transportFlight, "Check-in closed"));
        }
        logger.info(transportFlight + " - Check-in closed");
    }
}
