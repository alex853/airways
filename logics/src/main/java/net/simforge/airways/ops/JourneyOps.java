package net.simforge.airways.ops;

import net.simforge.airways.EventLog;
import net.simforge.airways.model.journey.Itinerary;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.model.flow.City2CityFlow;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

public class JourneyOps {
    private static final Logger log = LoggerFactory.getLogger(JourneyOps.class);

    public static Journey create(Session session, City2CityFlow flow, boolean directDirection) {
        BM.start("JourneyOps.create");
        try {
            Journey journey = new Journey();
            journey.setGroupSize(flow.getNextGroupSize());
            journey.setFromCity(directDirection ? flow.getFromFlow().getCity() : flow.getToFlow().getCity());
            journey.setToCity(directDirection ? flow.getToFlow().getCity() : flow.getFromFlow().getCity());
            journey.setC2cFlow(flow);
            journey.setStatus(Journey.Status.LookingForPersons);

            session.save(journey);
            EventLog.info(session, log, journey, String.format("New journey is created, group contains %s person(s)", journey.getGroupSize()), flow.getFromFlow().getCity(), flow);

            return journey;
        } finally {
            BM.stop();
        }
    }

    public static List<Person> getPersons(Session session, Journey journey) {
        BM.start("JourneyOps.getPersons");
        try {
            //noinspection unchecked
            return session
                    .createQuery("from Person where journey = :journey")
                    .setEntity("journey", journey)
                    .list();
        } finally {
            BM.stop();
        }
    }

    public static List<Itinerary> getItineraries(Session session, Journey journey) {
        BM.start("JourneyOps.getItineraries");
        try {
            //noinspection unchecked
            return session
                    .createQuery("from JourneyItinerary where journey = :journey")
                    .setEntity("journey", journey)
                    .list();
        } finally {
            BM.stop();
        }
    }

    public static Collection<Journey> loadJourneysForFlight(Session session, TransportFlight transportFlight) {
        BM.start("JourneyOps.loadJourneysForFlight");
        try {
            //noinspection unchecked
            return session
                    .createQuery("from Journey j " +
                            "where j.itinerary.flight = :flight")
                    .setEntity("flight", transportFlight)
                    .list();
        } finally {
            BM.stop();
        }
    }

    public static void terminateJourney(Session session, Journey journey) {
        BM.start("JourneyOps.terminateJourney");
        try {

            journey.setStatus(Journey.Status.Terminated);
            session.update(journey);

            List<Person> persons = getPersons(session, journey);
            persons.forEach(person -> {
                person.setStatus(Person.Status.Idle);
                person.setJourney(null);
                session.update(person);

                EventLog.info(session, log, person, "Journey dissolved & TERMINATED", journey);
            });

            EventLog.info(session, log, journey, "Journey dissolved & TERMINATED");

        } finally {
            BM.stop();
        }
    }
}
