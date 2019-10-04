/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.event;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.event.Event;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.DurationConsts;
import net.simforge.airways.processes.transportflight.activity.Checkin;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.LocalDateTime;

/**
 * 'CheckinOpens' event means that check-in opens O_o It fact it means that passengers may check-in to the flight.
 */
@Subscribe(CheckinOpens.class)
public class CheckinOpens implements Event, Handler {
    private static Logger logger = LoggerFactory.getLogger(CheckinOpens.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;

    public void process() {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {

                engine.startActivity(session, Checkin.class, transportFlight);

                LocalDateTime checkinClosesAt = transportFlight.getDepartureDt().minusMinutes(DurationConsts.END_OF_CHECKIN_TO_DEPARTURE_MINS);
                engine.scheduleEvent(session, CheckinClosed.class, transportFlight, checkinClosesAt);

                session.save(EventLog.make(transportFlight, "Check-in open, it will close at " + checkinClosesAt));
                logger.info(transportFlight + " - Check-in open, it will close at  " + checkinClosesAt);

            });
        }
    }
}
