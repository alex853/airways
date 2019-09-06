/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage3;

import net.simforge.airways.stage3.model.flight.Flight;
import net.simforge.airways.stage3.model.flight.PilotAssignment;
import net.simforge.airways.stage3.model.person.Pilot;
import net.simforge.airways.stage3.model.person.PilotEntity;
import net.simforge.commons.hibernate.AuditInterceptor;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

public class PilotOps extends BaseOpsImpl<Pilot> {

    private static Logger logger = LoggerFactory.getLogger(PilotOps.class.getName());

    private final EntityStorage storage;
    private final SessionFactory sessionFactory;

    public PilotOps(EntityStorage storage, SessionFactory sessionFactory) {
        this.storage = storage;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public List<Pilot> whereHeartbeatDtBelow(LocalDateTime threshold, int resultLimit) {
        BM.start("PilotOps.whereHeartbeatDtBelow");
        try {
            return storage.filter(Pilot.class,
                    (pilot) -> pilot.getHeartbeatDt() != null && pilot.getHeartbeatDt().isBefore(threshold),
                    resultLimit);
        } finally {
            BM.stop();
        }
    }

    @Override
    protected Class getEntityClass() {
        return PilotEntity.class;
    }

    @Override
    protected Session openSession() {
        return sessionFactory
                .withOptions()
                .interceptor(
                        new CacheInvalidationInterceptor(
                                storage,
                                new AuditInterceptor()))
                .openSession();
    }

    public List<PilotAssignment> getCachedPilotAssignments_assigned(Pilot pilot) {
        List<PilotAssignment> data = storage.filter(PilotAssignment.class,
                (assignment) -> assignment.getPilot().getId().equals(pilot.getId())
                        && assignment.getStatus() == PilotAssignment.Status.Assigned);
        data.sort((a1, a2) -> {
            Flight f1 = storage.get(Flight.class, a1.getFlight().getId());
            Flight f2 = storage.get(Flight.class, a2.getFlight().getId());
            return f1.getScheduledDepartureTime().compareTo(f2.getScheduledDepartureTime());
        });
        return data;
    }

    public PilotAssignment getCachedInProgressAssignment(Pilot pilot) {
        List<PilotAssignment> data = storage.filter(PilotAssignment.class,
                (assignment) -> assignment.getPilot().getId().equals(pilot.getId())
                        && assignment.getStatus() == PilotAssignment.Status.InProgress,
                1);
        return !data.isEmpty() ? data.get(0) : null;
    }
}
