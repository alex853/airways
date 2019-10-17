/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.event;

import net.simforge.airways.cityflows.CityFlowOps;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.flow.City2CityFlowStats;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;
import java.util.List;

@Subscribe(FinishOnArrivalToCity.class)
public class FinishOnArrivalToCity implements Event, Handler {
    @Inject
    private Journey journey;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("FinishOnArrivalToCity.process");
        try (Session session = sessionFactory.openSession()) {

            journey = session.load(Journey.class, journey.getId());

            HibernateUtils.transaction(session, () -> {

                journey.setStatus(Journey.Status.Finished);
                session.update(journey);

                List<Person> persons = JourneyOps.getPersons(session, journey);
                persons.forEach(person -> {
                    person.setStatus(Person.Status.Idle);
                    person.setJourney(null);
                    session.update(person);

                    session.save(EventLog.make(person, "Journey FINISHED", journey));
                });

                session.save(EventLog.make(journey, "Journey FINISHED"));

                City2CityFlowStats stats = CityFlowOps.getCurrentStats(session, journey.getC2cFlow());
                stats.setTravelled(stats.getTravelled() + journey.getGroupSize());
                session.update(stats);

            });

        } finally {
            BM.stop();
        }
    }
}
