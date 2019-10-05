/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.event;

import net.simforge.airways.engine.event.Event;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;
import java.util.List;

@Subscribe(TransferCityToAirportArrived.class)
public class TransferCityToAirportArrived implements Event, Handler {
    @Inject
    private Journey journey;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("TransferCityToAirportArrived.process");
        try (Session session = sessionFactory.openSession()) {

            journey = session.load(Journey.class, journey.getId());

            HibernateUtils.transaction(session, () -> {

                TransportFlight transportFlight = journey.getItinerary().getFlight();

                List<Person> persons = JourneyOps.getPersons(session, journey);
                persons.forEach(person -> {
                    person.setLocationCity(null);
                    person.setLocationAirport(transportFlight.getFromAirport());
                    session.update(person);
                });

                journey.setStatus(Journey.Status.WaitingForCheckin);
                session.update(journey);

            });

        } finally {
            BM.stop();
        }
    }
}
