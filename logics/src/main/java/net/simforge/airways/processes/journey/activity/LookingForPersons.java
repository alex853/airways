/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.activity;

import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.Result;
import net.simforge.airways.processengine.activity.Activity;
import net.simforge.airways.ops.PersonOps;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.airways.processengine.TimeMachine;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

public class LookingForPersons implements Activity {
    private static Logger logger = LoggerFactory.getLogger(LookingForPersons.class);

    @Inject
    private Journey journey;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;
    @Inject
    private TimeMachine timeMachine;

    @Override
    public Result act() {
        BM.start("LookingForPersons.act");
        try (Session session = sessionFactory.openSession()) {

            journey = session.load(Journey.class, journey.getId());

            List<Person> journeyPersons = JourneyOps.getPersons(session, journey);
            int currentGroupSize = journeyPersons.size();

            City originCity = journey.getC2cFlow().getFromFlow().getCity();
            City fromCity = journey.getFromCity();

            List<Person> freePersons;

            BM.start("LookingForPersons.act#query");
            try {
                //noinspection unchecked
                freePersons = session
                        .createQuery("from Person " +
                                "where type = :ordinal " +
                                "  and status = :readyToTravel " +
                                "  and locationCity = :fromCity")
                        .setInteger("ordinal", Person.Type.Ordinal)
                        .setInteger("readyToTravel", Person.Status.Idle)
                        .setEntity("fromCity", fromCity)
                        .setMaxResults(journey.getGroupSize())
                        .list();
            } finally {
                BM.stop();
            }

            for (Person freePerson : freePersons) {
                if (currentGroupSize == journey.getGroupSize()) {
                    break;
                }

                HibernateUtils.transaction(session, () -> {
                    freePerson.setStatus(Person.Status.OnJourney);
                    freePerson.setJourney(journey);
                    session.update(freePerson);
                    session.save(EventLog.make(freePerson, String.format("Decided to travel from %s to %s", fromCity.getName(), journey.getToCity().getName()), journey));
                });

                currentGroupSize++;
            }

            boolean returningJourney = originCity.getId().intValue() != fromCity.getId().intValue();
            if (!returningJourney) {
                while (currentGroupSize < journey.getGroupSize()) {
                    HibernateUtils.transaction(session, () -> {
                        Person person = PersonOps.createOrdinalPerson(session, originCity);

                        person.setStatus(Person.Status.OnJourney);
                        person.setJourney(journey);
                        session.update(person);
                        session.save(EventLog.make(person, String.format("Decided to travel from %s to %s", fromCity.getName(), journey.getToCity().getName()), journey));
                    });
                    currentGroupSize++;
                }
            } // else - we do not create persons for returning journeys as persons should be created only in their city of origin

            if (currentGroupSize == journey.getGroupSize()) {
                logger.debug("Journey {}-{} - all persons found", fromCity.getName(), journey.getToCity().getName(), currentGroupSize);

                HibernateUtils.transaction(session, () -> {
                    journey.setStatus(Journey.Status.LookingForTickets);
                    session.update(journey);
                    session.save(EventLog.make(journey, "Looking for tickets"));

                    engine.startActivity(session, LookingForTickets.class, journey, timeMachine.now().plusDays(7));
                });
                return Result.done();
            } else {
                return Result.resume(Result.When.NextDay);
            }

        } finally {
            BM.stop();
        }
    }

    @Override
    public Result onExpiry() {
        BM.start("LookingForPersons.onExpiry");
        try (Session session = sessionFactory.openSession()) {
            journey = session.load(Journey.class, journey.getId());

            HibernateUtils.transaction(session, () -> {
                journey.setStatus(Journey.Status.CouldNotFindPersons);
                session.update(journey);

                session.save(EventLog.make(journey, "Journey could not be populated in appropriate time"));

                List<Person> persons = JourneyOps.getPersons(session, journey);
                for (Person person : persons) {
                    person.setStatus(Person.Status.Idle);
                    person.setJourney(null);
                    session.update(person);

                    session.save(EventLog.make(person, "Journey expired during populating", journey));
                }
            });

            return Result.nothing();
        } finally {
            BM.stop();
        }
    }
}
