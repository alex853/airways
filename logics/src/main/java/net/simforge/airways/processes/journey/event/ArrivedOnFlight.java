package net.simforge.airways.processes.journey.event;

import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.journey.Itinerary;
import net.simforge.airways.processes.transfer.journey.TransferLauncher;
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
    private ProcessEngineScheduling scheduling;
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

                    TransferLauncher.startTransferToCityThenEvent(scheduling, session, journey, journey.getToCity(), FinishOnArrivalToCity.class);

                });

            }

        } finally {
            BM.stop();
        }
    }
}
