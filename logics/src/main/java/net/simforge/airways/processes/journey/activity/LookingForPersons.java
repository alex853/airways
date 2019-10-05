/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.activity;

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
            HibernateUtils.transaction(session, () -> {
                // Note: no expiration check here!

                // journey is loaded via another session and it can cause issues later when journey will be updated
                journey = session.load(Journey.class, journey.getId());

                City originCity = journey.getC2cFlow().getFromFlow().getCity();
                City fromCity = journey.getFromCity();

                List<Person> persons;

                BM.start("LookingForPersons.act#query");
                try {
                    //noinspection unchecked
                    persons = session
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

                if (persons.size() < journey.getGroupSize()) {
                    logger.debug("Journey {}-{} - found {} persons, while journey needs {} persons, creating insufficient persons", fromCity.getName(), journey.getToCity().getName(), persons.size(), journey.getGroupSize());

                    while (persons.size() < journey.getGroupSize()) {
                        Person person = PersonOps.createOrdinalPerson(session, originCity);
                        persons.add(person);
                    }
                } else {
                    logger.debug("Journey {}-{} - all persons found", fromCity.getName(), journey.getToCity().getName(), persons.size());
                }

                for (Person person : persons) {
                    person.setStatus(Person.Status.OnJourney);
                    person.setJourney(journey);
                    session.update(person);
                    session.save(EventLog.make(person, String.format("Decided to travel from %s to %s", fromCity.getName(), journey.getToCity().getName()), journey));
                }

                journey.setStatus(Journey.Status.LookingForTickets);
                session.update(journey);
                session.save(EventLog.make(journey, "Looking for tickets"));

                engine.startActivity(session, LookingForTickets.class, journey, timeMachine.now().plusDays(7));
            });

            return Result.done();
        } finally {
            BM.stop();
        }
    }

    @Override
    public Result onExpiry() {
        return Result.nothing(); // no op because no expiration expected
    }
}
