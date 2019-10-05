/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.event;

import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.persistence.model.journey.Itinerary;
import net.simforge.airways.processes.journey.TransferLauncher;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;
import java.util.List;

@Subscribe(ArrivedOnFlight.class)
public class ArrivedOnFlight implements Event, Handler {
    @Inject
    private Journey journey;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("ArrivedOnFlight.process");
        try (Session session = sessionFactory.openSession()) {

            journey = session.load(Journey.class, journey.getId());

            List<Itinerary> itineraries = JourneyOps.getItineraries(session, journey);
            int itineraryIndex = itineraries.indexOf(journey.getItinerary());
            if (itineraryIndex == -1) {
                throw new RuntimeException(); // todo p3 replace with something else
            }
            Itinerary nextItinerary = itineraryIndex != itineraries.size()-1
                    ? itineraries.get(itineraryIndex + 1)
                    : null;

            if (nextItinerary != null) {

                HibernateUtils.transaction(session, () -> {

                    journey.setItinerary(nextItinerary);
                    journey.setStatus(Journey.Status.WaitingForCheckin);
                    session.update(journey);

                });

            } else {

                HibernateUtils.transaction(session, () -> {

                    journey.setItinerary(null);
                    session.update(journey);

                    TransferLauncher.startTransferToCity(engine, session, journey, journey.getToCity());

                });

            }

        } finally {
            BM.stop();
        }
    }
}
