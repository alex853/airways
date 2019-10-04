/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.event;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.event.Event;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.JourneyItinerary;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.DurationConsts;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Geo;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

/**
 * It calculates moment when passengers have to start their way to airport.
 * DepartAtAirport event will be fired at that calculated moment.
 * It will change location of persons and update status of journey.
 * Also it will schedule event ArriveToAirport.
 * ArriveToAirport updates persons' location to airport location and prepares journey for check-in.
 */
@Subscribe(TicketsBought.class)
public class TicketsBought implements Event, Handler {
    @Inject
    private Journey journey;
    @Inject
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("TicketsBought.process");
        try (Session session = sessionFactory.openSession()) {

            JourneyItinerary itinerary = journey.getItinerary();
            TransportFlight transportFlight = itinerary.getFlight();
            LocalDateTime checkinStartsAt = transportFlight.getDepartureDt().minusMinutes(DurationConsts.START_OF_CHECKIN_TO_DEPARTURE_MINS);

            List<Person> persons = JourneyOps.getPersons(session, journey);
            double maxDistance = persons.stream().mapToDouble(person -> {
                if (person.getLocationCity() == null)
                    return 0.0;
                return Geo.distance(person.getLocationCity().getCoords(), transportFlight.getFromAirport().getCoords());
            }).max().orElse(0.0);

            int transferToAirportMinutes = (int) (maxDistance / 25 * 60 + DepartToAirport.TRANSFER_RESERVE_BEFORE_CHECKIN);
            LocalDateTime transferWillStartAt = checkinStartsAt.minusMinutes(transferToAirportMinutes);

            engine.scheduleEvent(DepartToAirport.class, journey, transferWillStartAt);

        } finally {
            BM.stop();
        }
    }
}
