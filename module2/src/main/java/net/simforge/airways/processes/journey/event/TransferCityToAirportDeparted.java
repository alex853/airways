/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.event;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.event.Event;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.DurationConsts;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

@Subscribe(TransferCityToAirportDeparted.class)
public class TransferCityToAirportDeparted implements Event, Handler {
    public static final int TRANSFER_RESERVE_BEFORE_CHECKIN = 15;

    @Inject
    private Journey journey;
    @Inject
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("TransferCityToAirportDeparted.process");
        try (Session session = sessionFactory.openSession()) {

            journey = session.load(Journey.class, journey.getId());

            HibernateUtils.transaction(session, () -> {

                List<Person> persons = JourneyOps.getPersons(session, journey);
                persons.forEach(person -> {
                    person.setLocationCity(null);
                    person.setLocationAirport(null);
                    session.update(person);
                });

                TransportFlight transportFlight = journey.getItinerary().getFlight();
                LocalDateTime checkinStartsAt = transportFlight.getDepartureDt().minusMinutes(DurationConsts.START_OF_CHECKIN_TO_DEPARTURE_MINS);

                LocalDateTime transferWillEndAt = checkinStartsAt.minusMinutes(TransferCityToAirportDeparted.TRANSFER_RESERVE_BEFORE_CHECKIN);

                journey.setStatus(Journey.Status.TransferToAirport);
                session.update(journey);

                engine.scheduleEvent(session, TransferCityToAirportArrived.class, journey, transferWillEndAt);

            });

        } finally {
            BM.stop();
        }
    }
}
