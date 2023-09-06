package net.simforge.airways.processes.transportflight.event;

import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.processes.DurationConsts;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.LocalDateTime;

/**
 * 'Scheduled' event is fired at the moment when TimetableRow creates new TransportFlight instance.
 */
@Subscribe(Scheduled.class)
public class Scheduled implements Event, Handler {
    private static final Logger log = LoggerFactory.getLogger(Scheduled.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("Scheduled.process");
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {

                LocalDateTime checkinOpensAt = transportFlight.getDepartureDt().minusMinutes(DurationConsts.START_OF_CHECKIN_TO_DEPARTURE_MINS);

                scheduling.scheduleEvent(session, CheckinOpens.class, transportFlight, checkinOpensAt);

                EventLog.info(session, log, transportFlight, "Check-in will open at " + checkinOpensAt);

            });
        } finally {
            BM.stop();
        }
    }
}
