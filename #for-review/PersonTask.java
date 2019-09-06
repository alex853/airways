/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage2;

import net.simforge.airways.stage1.Util;
import net.simforge.airways.stage2.model.Person;
import net.simforge.airways.stage2.status.Status;
import net.simforge.airways.stage2.status.StatusHandler;
import net.simforge.commons.HeartbeatTask;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.temporal.ChronoUnit;

public class PersonTask extends HeartbeatTask<Person> {

    private final SessionFactory sessionFactory;
    private final StatusHandler statusHandler;

    public PersonTask() {
        this(AirwaysApp.getSessionFactory());
    }

    public PersonTask(SessionFactory sessionFactory) {
        super("Person", sessionFactory);
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
    protected Person heartbeat(Person _person) {
        BM.start("PersonTask.heartbeat");
        try (Session session = sessionFactory.openSession()) {
            Person person = session.get(Person.class, _person.getId());

            Util.transaction(session, () -> {
                statusHandler.perform(StatusHandler.context(person, session));
            });

            return person;
        } finally {
            BM.stop();
        }
    }

    @Status(code = Person.Status.NoTravel)
    private void noTravel(StatusHandler.Context<Person> ctx) {
        BM.start("PersonTask.noTravel");
        try {
            Person person = ctx.getSubject();
            Session session = ctx.get(Session.class);

            person.setStatus(Person.Status.ReadyToTravel);
            person.setHeartbeatDt(null);

            Util.update(session, person, "updatePerson");
            EventLog.saveLog(session, person, "Person is ready to travel");
        } finally {
            BM.stop();
        }
    }
}
