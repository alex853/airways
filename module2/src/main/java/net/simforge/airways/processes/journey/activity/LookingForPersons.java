/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.activity;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.ops.PersonOps;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.geo.City;
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
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;

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
                            .createQuery("from Person where type = :ordinal and status = :readyToTravel and originCity = :originCity and positionCity = :fromCity")
                            .setInteger("ordinal", Person.Type.Ordinal)
                            .setInteger("readyToTravel", Person.Status.ReadyToTravel)
                            .setEntity("originCity", originCity)
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
                    person.setStatus(Person.Status.Travelling);
                    person.setJourney(journey);
                    // todo p2 seems obsolete person.setPositionCity(null);

                    session.update(person);
                    session.save(EventLog.make(person, String.format("Decided to travel from %s to %s", fromCity.getName(), journey.getToCity().getName()), journey));
                }

                journey.setStatus(Journey.Status.LookingForTickets);
                //journey.setHeartbeatDt(JavaTime.nowUtc());
                // todo p2 mmmm journey.setExpirationDt(JavaTime.nowUtc().plusDays(7));

                session.update(journey);
                session.save(EventLog.make(journey, "Looking for tickets"));

            });

            engine.startActivity(LookingForTickets.class, journey);

            return Result.done();
        } finally {
            BM.stop();
        }
    }

    @Override
    public Result onExpiry() {
        return Result.nothing();
    }
}
