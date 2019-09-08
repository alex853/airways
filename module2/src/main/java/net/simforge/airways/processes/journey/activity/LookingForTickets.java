/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.activity;

import net.simforge.airways.cityflows.CityFlowOps;
import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.ops.PersonOps;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.flow.City2CityFlowStats;
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

    @Override
    public Result act() {
        BM.start("LookingForTickets.act");
        try (Session session = sessionFactory.openSession()) {
            // todo p1


            return Result.done();
        } finally {
            BM.stop();
        }
    }

    @Override
    public Result onExpiry() {
        BM.start("LookingForTickets.onExpiry");
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {
                journey.setStatus(Journey.Status.CouldNotFindTickets);
//                journey.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

                session.update(journey);
                session.save(EventLog.make(journey, "Journey could not find tickets in appropriate time"));

                City2CityFlowStats stats = CityFlowOps.getCurrentStats(session, journey.getC2cFlow());
                stats.setNoTickets(stats.getNoTickets() + journey.getGroupSize());

                session.update(stats);

                List<Person> persons = JourneyOps.getPersons(session, journey);
                for (Person person : persons) {
// todo                   PersonOps.releaseFromJourney(session, person);
                }

                die(session, journey);
            });
        } finally {
            BM.stop();
        }
        return null;
    }

    private void die(Session session, Journey journey) {
        BM.start("JourneyTask.die");
        try {
//            journey.setStatus(Journey.Status.Died);
//            journey.setHeartbeatDt(null);
//
//            Util.update(session, journey, "dieJourney");
//            EventLog.saveLog(session, journey, "Journey processing finished");
        } finally {
            BM.stop();
        }
    }

}
