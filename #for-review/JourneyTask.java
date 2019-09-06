/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage2;

import net.simforge.airways.stage1.Util;
import net.simforge.airways.stage2.model.Journey;
import net.simforge.airways.stage2.model.Person;
import net.simforge.airways.stage2.model.flow.City2CityFlowStats;
import net.simforge.airways.stage2.model.geo.City;
import net.simforge.airways.stage2.status.Status;
import net.simforge.airways.stage2.status.StatusHandler;
import net.simforge.commons.HeartbeatTask;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.temporal.ChronoUnit;
import java.util.List;

public class JourneyTask extends HeartbeatTask<Journey> {

    private final SessionFactory sessionFactory;
    private final StatusHandler statusHandler;

    public JourneyTask() {
        this(AirwaysApp.getSessionFactory());
    }

    public JourneyTask(SessionFactory sessionFactory) {
        super("Journey", sessionFactory);
        this.sessionFactory = sessionFactory;
        this.statusHandler = StatusHandler.create(this);
    }

    @Override
    protected void startup() {
        super.startup();

        BM.setLoggingPeriod(ChronoUnit.HOURS.getDuration().toMillis());

        setBaseSleepTime(10000);
        setBatchSize(1000);
    }

    @Override
    protected Journey heartbeat(Journey _journey) {
        BM.start("Journey.heartbeat");
        try (Session session = sessionFactory.openSession()) {
            Journey journey = session.get(Journey.class, _journey.getId());

            statusHandler.perform(StatusHandler.context(journey, session));

            return journey;
        } finally {
            BM.stop();
        }
    }

    @Status(code = Journey.Status.LookingForPersons)
    private void lookingForPersons(StatusHandler.Context<Journey> ctx) {
        BM.start("JourneyTask.lookingForPersons");
        try {
            Journey journey = ctx.getSubject();
            Session session = ctx.get(Session.class);

            HibernateUtils.transaction(session, () -> {
                // Note: no expiration check here!

                City originCity = journey.getC2cFlow().getFromFlow().getCity();
                City fromCity = journey.getFromCity();

                List<Person> persons;

                BM.start("JourneyTask.lookingForPersons#query");
                try {
                    //noinspection unchecked,JpaQlInspection
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
                        Person person = PersonOps.create(session, originCity);
                        persons.add(person);
                    }
                } else {
                    logger.debug("Journey {}-{} - all persons found", fromCity.getName(), journey.getToCity().getName(), persons.size());
                }

                for (Person person : persons) {
                    person.setStatus(Person.Status.Travelling);
                    person.setJourney(journey);
                    person.setPositionCity(null);

                    Util.update(session, person, "updatePerson");
                    EventLog.saveLog(session, person, String.format("Decided to travel from %s to %s", fromCity.getName(), journey.getToCity().getName()), journey);
                }

                journey.setStatus(Journey.Status.LookingForTickets);
                journey.setHeartbeatDt(JavaTime.nowUtc());
                journey.setExpirationDt(JavaTime.nowUtc().plusDays(7));

                Util.update(session, journey, "updateJourney");
                EventLog.saveLog(session, journey, "Looking for tickets");
            });
        } finally {
            BM.stop();
        }
    }

    @Status(code = Journey.Status.LookingForTickets)
    private void lookingForTickets(StatusHandler.Context<Journey> ctx) {
        BM.start("JourneyTask.lookingForTickets");
        try {
            Journey journey = ctx.getSubject();
            Session session = ctx.get(Session.class);

            Util.transaction(session, () -> {
                if (JourneyOps.isExpired(journey)) {
                    journey.setStatus(Journey.Status.CouldNotFindTickets);
                    journey.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

                    Util.update(session, journey, "updateJourney");
                    EventLog.saveLog(session, journey, "Journey could not find tickets in appropriate time");

                    return;
                }

                journey.setHeartbeatDt(JavaTime.nowUtc().plusHours(6));
                Util.update(session, journey, "updateJourney");
            });
        } finally {
            BM.stop();
        }
    }

    @Status(code = Journey.Status.CouldNotFindTickets)
    private void couldNotFindTickets(StatusHandler.Context<Journey> ctx) {
        BM.start("JourneyTask.couldNotFindTickets");
        try {
            Journey journey = ctx.getSubject();
            Session session = ctx.get(Session.class);

            Util.transaction(session, () -> {
                City2CityFlowStats stats = CityFlowOps.getCurrentStats(session, journey.getC2cFlow());
                stats.setNoTickets(stats.getNoTickets() + journey.getGroupSize());

                Util.update(session, stats, "updateStats");

                //noinspection JpaQlInspection,unchecked
                List<Person> persons = session
                        .createQuery("from Person where journey = :journey")
                        .setEntity("journey", journey)
                        .list();
                for (Person person : persons) {
                    PersonOps.releaseFromJourney(session, person);
                }

                die(session, journey);
            });
        } finally {
            BM.stop();
        }
    }

    private void die(Session session, Journey journey) {
        BM.start("JourneyTask.die");
        try {
            journey.setStatus(Journey.Status.Died);
            journey.setHeartbeatDt(null);

            Util.update(session, journey, "dieJourney");
            EventLog.saveLog(session, journey, "Journey processing finished");
        } finally {
            BM.stop();
        }
    }
}
