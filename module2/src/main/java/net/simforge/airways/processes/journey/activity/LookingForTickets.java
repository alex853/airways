/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.activity;

import net.simforge.airways.cityflows.CityFlowOps;
import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.JourneyItinerary;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.persistence.model.flow.City2CityFlowStats;
import net.simforge.airways.ticketing.DirectConnectionsTicketing;
import net.simforge.airways.ticketing.TicketingRequest;
import net.simforge.airways.util.TimeMachine;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;
import java.util.List;

public class LookingForTickets implements Activity {
    @Inject
    private Journey journey;
    @Inject
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;
    @Inject
    private TimeMachine timeMachine;

    @Override
    public Result act() {
        BM.start("LookingForTickets.act");
        try (Session session = sessionFactory.openSession()) {

            TicketingRequest ticketingRequest = TicketingRequest.get(journey, session, timeMachine);
            List<TransportFlight> foundFlights = DirectConnectionsTicketing.search(ticketingRequest);

            if (foundFlights == null || foundFlights.size() == 0) {
                return Result.resume(Result.When.FewTimesPerDay);
            }

            journey = session.load(Journey.class, journey.getId());

            HibernateUtils.transaction(session, () -> {
                JourneyItinerary firstItinerary = null;

                for (int i = 0; i < foundFlights.size(); i++) {
                    TransportFlight foundFlight = foundFlights.get(i);

                    JourneyItinerary itinerary = new JourneyItinerary();
                    itinerary.setJourney(journey);
                    itinerary.setFlight(foundFlight);
                    itinerary.setItemOrder(i);
                    session.save(itinerary);

                    if (firstItinerary == null) {
                        firstItinerary = itinerary;
                    }

                    foundFlight.setFreeTickets(foundFlight.getFreeTickets() - journey.getGroupSize());
                    session.update(foundFlight);
                }

                journey.setStatus(Journey.Status.WaitingForFlight);
                journey.setItinerary(firstItinerary);
                session.update(journey);

                City2CityFlowStats stats = CityFlowOps.getCurrentStats(session, journey.getC2cFlow());
                stats.setTicketsBought(stats.getTicketsBought() + journey.getGroupSize());
                session.update(stats);

                // todo p1 we need to fire event or start some activity which starts transfer of passengers to airport
                // ? schedule event StartTransferToAirport at some calculated time
                // ? it changes location of persons and updates status of journey and schedule event FinishTransferToAirport
                // ? FinishTransferToAirport updates persons' location to airport location and prepares for check-in
                engine.startActivity(session, WaitingForFlight.class, journey, null);
            });

            return Result.done();
        } finally {
            BM.stop();
        }
    }

    @Override
    public Result onExpiry() {
        BM.start("LookingForTickets.onExpiry");
        try (Session session = sessionFactory.openSession()) {
            journey = session.load(Journey.class, journey.getId());

            HibernateUtils.transaction(session, () -> {
                journey.setStatus(Journey.Status.CouldNotFindTickets);
                session.update(journey);

                session.save(EventLog.make(journey, "Journey could not find tickets in appropriate time"));

                City2CityFlowStats stats = CityFlowOps.getCurrentStats(session, journey.getC2cFlow());
                stats.setNoTickets(stats.getNoTickets() + journey.getGroupSize());
                session.update(stats);

                List<Person> persons = JourneyOps.getPersons(session, journey);
                for (Person person : persons) {
                    person.setStatus(Person.Status.Idle);
                    person.setJourney(null);
                    session.update(person);

                    session.save(EventLog.make(person, "Journey expired during ticketing", journey));
                }
            });

            return Result.nothing();
        } finally {
            BM.stop();
        }
    }
}
