package net.simforge.airways.processes.transportflight.event;

import net.simforge.airways.ops.TransportFlightOps;
import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.flight.TransportFlight;
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
    private static final Logger log = LoggerFactory.getLogger(CheckinOpens.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;

    public void process() {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {

                transportFlight = session.load(TransportFlight.class, transportFlight.getId());

                if (transportFlight.getStatus().code() > TransportFlight.Status.Checkin.code()) {
                    EventLog.warn(session, log, transportFlight,
                            String.format("Check-in terminated as Transport Flight is in '%s' status", transportFlight.getStatus()));
                    return;
                } // todo AK cancellation needs to cancel and stop all related events and activities

                TransportFlightOps.checkAndSetStatus(transportFlight, TransportFlight.Status.Checkin);
                session.update(transportFlight);

                scheduling.startActivity(session, Checkin.class, transportFlight);

                LocalDateTime checkinClosesAt = transportFlight.getDepartureDt().minusMinutes(DurationConsts.END_OF_CHECKIN_TO_DEPARTURE_MINS);
                scheduling.scheduleEvent(session, CheckinClosed.class, transportFlight, checkinClosesAt);

                EventLog.info(session, log, transportFlight,
                        String.format("Check-in open, it will close at %s", checkinClosesAt));

            });
        }
    }
}
