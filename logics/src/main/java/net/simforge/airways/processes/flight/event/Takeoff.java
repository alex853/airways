/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.event;

import net.simforge.airways.persistence.model.flight.Flight;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;

@Subscribe(Takeoff.class)
public class Takeoff implements Event, Handler {
    @Inject
    private Flight flight;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("Takeoff.process");
        try (Session session = sessionFactory.openSession()) {

            flight = session.load(Flight.class, flight.getId());

            TransportFlight transportFlight = flight.getTransportFlight();
            if (transportFlight == null) {
                return;
            }

            transportFlight.setStatus(TransportFlight.Status.Flying);
            HibernateUtils.updateAndCommit(session, transportFlight);

        } finally {
            BM.stop();
        }
    }
}
