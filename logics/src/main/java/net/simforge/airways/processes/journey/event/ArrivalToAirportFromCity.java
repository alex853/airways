/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.event;

import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.persistence.model.journey.Itinerary;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.processes.journey.TransferLauncher;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;

@Subscribe(ArrivalToAirportFromCity.class)
public class ArrivalToAirportFromCity implements Event, Handler {
    @Inject
    private Journey journey;
    @Inject
    private ProcessEngine engine;
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

                    session.save(EventLog.make(journey, "At airport, waiting for check-in"));

                });

                return;
            }

            // it seems we have late or something else
            // anyway we are cancelling our journey and returning to city
            HibernateUtils.transaction(session, () -> {

                TransferLauncher.startTransferToBiggestCityThenCancel(engine, session, journey);

            });

        } finally {
            BM.stop();
        }
    }
}
