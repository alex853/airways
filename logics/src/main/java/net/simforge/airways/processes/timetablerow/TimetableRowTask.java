package net.simforge.airways.processes.timetablerow;

import net.simforge.airways.AirwaysApp;
import net.simforge.airways.model.flight.TimetableRow;
import net.simforge.airways.ops.TimetableOps;
import net.simforge.airways.processengine.*;
import net.simforge.commons.HeartbeatTask;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.temporal.ChronoUnit;

public class TimetableRowTask extends HeartbeatTask<TimetableRow> {
    private final SessionFactory sessionFactory;
    private final TimeMachine timeMachine;
    private final ProcessEngineScheduling scheduling;

    @SuppressWarnings("unused") // used via reflection
    public TimetableRowTask() {
        super("TimetableRow", AirwaysApp.getSessionFactory());
        this.sessionFactory = AirwaysApp.getSessionFactory();
        this.timeMachine = AirwaysApp.getTimeMachine();
        this.scheduling = AirwaysApp.getScheduling();
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

            boolean allFlightsOk = TimetableOps.scheduleFlights(timetableRow, session, scheduling, timeMachine);

            if (allFlightsOk) {
                timetableRow.setHeartbeatDt(timeMachine.now().plusDays(1));
            } else {
                timetableRow.setHeartbeatDt(timeMachine.now().plusHours(1));
            }

            HibernateUtils.updateAndCommit(session, timetableRow);

            return timetableRow;
        } finally {
            BM.stop();
        }
    }
}
