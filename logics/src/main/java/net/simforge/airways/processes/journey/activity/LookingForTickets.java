package net.simforge.airways.processes.journey.activity;

import net.simforge.airways.cityflows.CityFlowOps;
import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.Result;
import net.simforge.airways.processengine.activity.Activity;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.journey.Itinerary;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.model.flow.City2CityFlowStats;
import net.simforge.airways.processes.journey.event.TicketsBought;
import net.simforge.airways.ticketing.DirectConnectionsTicketing;
import net.simforge.airways.ticketing.TicketingRequest;
import net.simforge.airways.processengine.TimeMachine;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

public class LookingForTickets implements Activity {
    private static final Logger log = LoggerFactory.getLogger(LookingForTickets.class);

    @Inject
    private Journey journey;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;
    @Inject
    private TimeMachine timeMachine;

    @Override
    public Result act() {
        BM.start("LookingForTickets.act");
        try (Session session = sessionFactory.openSession()) {

            journey = session.load(Journey.class, journey.getId());

            TicketingRequest ticketingRequest = TicketingRequest.get(journey, session, timeMachine);
            List<TransportFlight> foundFlights = DirectConnectionsTicketing.search(ticketingRequest);

            if (foundFlights == null || foundFlights.size() == 0) {
                return Result.resume(Result.When.FewTimesPerDay);
            }

            journey = session.load(Journey.class, journey.getId());

            HibernateUtils.transaction(session, () -> {
                Itinerary firstItinerary = null;

                for (int i = 0; i < foundFlights.size(); i++) {
                    TransportFlight foundFlight = foundFlights.get(i);

                    Itinerary itinerary = new Itinerary();
                    itinerary.setJourney(journey);
                    itinerary.setFlight(foundFlight);
                    itinerary.setItemOrder(i + 1);
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

                EventLog.info(session, log, journey, "Tickets bought");
                List<Person> persons = JourneyOps.getPersons(session, journey);
                persons.forEach(person -> EventLog.info(session, log, person, "Tickets bought", journey));

                City2CityFlowStats stats = CityFlowOps.getCurrentStats(session, journey.getC2cFlow());
                stats.setTicketsBought(stats.getTicketsBought() + journey.getGroupSize());
                session.update(stats);

                scheduling.fireEvent(session, TicketsBought.class, journey);
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

                EventLog.info(session, log, journey, "No tickets found - CANCELLED");

                City2CityFlowStats stats = CityFlowOps.getCurrentStats(session, journey.getC2cFlow());
                stats.setNoTickets(stats.getNoTickets() + journey.getGroupSize());
                session.update(stats);

                List<Person> persons = JourneyOps.getPersons(session, journey);
                for (Person person : persons) {
                    person.setStatus(Person.Status.Idle);
                    person.setJourney(null);
                    session.update(person);

                    EventLog.info(session, log, person, "No tickets found - CANCELLED", journey);
                }
            });

            return Result.nothing();

        } finally {
            BM.stop();
        }
    }
}
