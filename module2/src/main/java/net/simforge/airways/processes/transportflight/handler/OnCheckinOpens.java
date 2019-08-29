/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.handler;

import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.engine.*;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.activity.Checkin;
import net.simforge.airways.processes.transportflight.event.CheckinClosed;
import net.simforge.airways.processes.transportflight.event.CheckinOpens;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.LocalDateTime;

/**
 * It makes check-in for passengers which are already in the airport.
 */
@Subscribe(CheckinOpens.class)
public class OnCheckinOpens implements Handler {
    private static Logger logger = LoggerFactory.getLogger(OnCheckinOpens.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;

    public void process() {
        engine.startActivity(Checkin.class, transportFlight);

        LocalDateTime checkinClosesAt = transportFlight.getDepartureDt().minusMinutes(40); // checkin ends 40 mins before departure
        engine.scheduleEvent(CheckinClosed.class, transportFlight, checkinClosesAt);

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, EventLog.make(transportFlight, "Check-in open, it will close at " + checkinClosesAt));
        }
        logger.info(transportFlight + " - Check-in open, it will close at  " + checkinClosesAt);
    }
}
