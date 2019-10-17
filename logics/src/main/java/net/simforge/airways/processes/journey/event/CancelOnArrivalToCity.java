/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.event;

import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;

@Subscribe(CancelOnArrivalToCity.class)
public class CancelOnArrivalToCity implements Event, Handler {
    @Inject
    private Journey journey;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("CancelOnArrivalToCity.process");
        try (Session session = sessionFactory.openSession()) {

            journey = session.load(Journey.class, journey.getId());

            HibernateUtils.transaction(session, () -> {

                JourneyOps.terminateJourney(session, journey);

            });

        } finally {
            BM.stop();
        }
    }
}
