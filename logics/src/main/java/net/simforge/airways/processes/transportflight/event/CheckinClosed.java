package net.simforge.airways.processes.transportflight.event;

import net.simforge.airways.ops.TransportFlightOps;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.flight.TransportFlight;
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
    private static final Logger log = LoggerFactory.getLogger(CheckinClosed.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private SessionFactory sessionFactory;

    public void process() {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {

                transportFlight = session.load(TransportFlight.class, transportFlight.getId());

                TransportFlightOps.checkAndSetStatus(transportFlight, TransportFlight.Status.WaitingForBoarding);
                session.update(transportFlight);

                EventLog.info(session, log, transportFlight, "Check-in closed");

            });
        }
    }

    // todo p3 non-checked-in journeys go to "too late for checkin"
}
