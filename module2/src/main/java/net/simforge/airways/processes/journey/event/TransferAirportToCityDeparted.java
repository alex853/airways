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
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.util.TimeMachine;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Geo;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Subscribe(TransferAirportToCityDeparted.class)
public class TransferAirportToCityDeparted implements Event, Handler {
    @Inject
    private Journey journey;
    @Inject
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;
    @Inject
    private TimeMachine timeMachine;

    @Override
    public void process() {
        BM.start("TransferAirportToCityDeparted.process");
        try (Session session = sessionFactory.openSession()) {

            journey = session.load(Journey.class, journey.getId());

            HibernateUtils.transaction(session, () -> {

                List<Person> persons = JourneyOps.getPersons(session, journey);
                Set<Airport> locationAirports = new HashSet<>();
                persons.forEach(person -> {

                    locationAirports.add(person.getLocationAirport());

                    person.setLocationCity(null);
                    person.setLocationAirport(null);
                    session.update(person);

                });

                Airport locationAirport = locationAirports.iterator().next();

                double distance = Geo.distance(locationAirport.getCoords(), journey.getToCity().getCoords());
                int transferDuration = (int) (distance / 25 * 60 + TransferCityToAirportDeparted.TRANSFER_RESERVE_BEFORE_CHECKIN);

                LocalDateTime transferWillEndAt = timeMachine.now().plusMinutes(transferDuration);

                journey.setStatus(Journey.Status.TransferToCity);
                session.update(journey);

                engine.scheduleEvent(session, TransferAirportToCityArrived.class, journey, transferWillEndAt);

            });

        } finally {
            BM.stop();
        }
    }

}
