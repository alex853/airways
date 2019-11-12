/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.timetablerow;

import net.simforge.airways.AirwaysApp;
import net.simforge.airways.model.flight.TimetableRow;
import net.simforge.airways.ops.TimetableOps;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.ProcessEngineBuilder;
import net.simforge.airways.processengine.RealTimeMachine;
import net.simforge.airways.processengine.TimeMachine;
import net.simforge.commons.HeartbeatTask;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.temporal.ChronoUnit;

public class TimetableRowTask extends HeartbeatTask<TimetableRow> {
    private final SessionFactory sessionFactory;
    private final TimeMachine timeMachine;
    private final ProcessEngine engine;

    public TimetableRowTask() {
        this(AirwaysApp.getSessionFactory());
    }

    public TimetableRowTask(SessionFactory sessionFactory) {
        super("TimetableRow", sessionFactory);
        this.sessionFactory = sessionFactory;
        this.timeMachine = new RealTimeMachine();
        this.engine = ProcessEngineBuilder.create().withSessionFactory(sessionFactory).withTimeMachine(timeMachine).build();
    }

    @Override
    protected void startup() {
        super.startup();

        BM.setLoggingPeriod(ChronoUnit.HOURS.getDuration().toMillis());
    }

    @Override
    protected TimetableRow heartbeat(TimetableRow _timetableRow) {
        BM.start("TimetableRowTask.heartbeat");
        try (Session session = sessionFactory.openSession()) {
            TimetableRow timetableRow = session.get(TimetableRow.class, _timetableRow.getId());

            // todo think about time machine for HeartbeatTask

            boolean someFlightFailed = TimetableOps.scheduleFlights(timetableRow, session, engine, timeMachine);

            if (someFlightFailed) {
                timetableRow.setHeartbeatDt(timeMachine.now().plusHours(1));
            } else {
                timetableRow.setHeartbeatDt(timeMachine.now().plusDays(1));
            }

            HibernateUtils.updateAndCommit(session, timetableRow);

            return timetableRow;
        } finally {
            BM.stop();
        }
    }
}
