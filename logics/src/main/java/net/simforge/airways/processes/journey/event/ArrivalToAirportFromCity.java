package net.simforge.airways.processes.journey.event;

import net.simforge.airways.EventLog;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.model.journey.Itinerary;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.processes.transfer.journey.TransferLauncher;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

@Subscribe(ArrivalToAirportFromCity.class)
public class ArrivalToAirportFromCity implements Event, Handler {
    private static final Logger log = LoggerFactory.getLogger(ArrivalToAirportFromCity.class);

    @Inject
    private Journey journey;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("ArrivalToAirportFromCity");
        try (Session session = sessionFactory.openSession()) {

            journey = session.load(Journey.class, journey.getId());

            Itinerary itinerary = journey.getItinerary();
            TransportFlight flight = itinerary != null ? itinerary.getFlight() : null;

            if (flight != null
                    && (flight.getStatus() == TransportFlight.Status.Scheduled
                    || flight.getStatus() == TransportFlight.Status.Checkin)) {

                HibernateUtils.transaction(session, () -> {

                    journey.setStatus(Journey.Status.WaitingForCheckin);
                    session.update(journey);

                    EventLog.info(session, log, journey, "At airport, waiting for check-in");

                });

                return;
            }

            // it seems we have late or something else
            // anyway we are cancelling our journey and returning to city
            HibernateUtils.transaction(session, () -> TransferLauncher.startTransferToBiggestCityThenCancel(scheduling, session, journey));

        } finally {
            BM.stop();
        }
    }
}
