/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage3;

import net.simforge.airways.stage3.model.flight.Flight;
import net.simforge.airways.stage3.model.flight.PilotAssignment;
import net.simforge.airways.stage3.model.flight.TransportFlight;
import net.simforge.airways.stage3.model.flight.TransportFlightEntity;
import net.simforge.commons.hibernate.AuditInterceptor;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

public class TransportFlightOps extends BaseOpsImpl<TransportFlight> {

    private static Logger logger = LoggerFactory.getLogger(TransportFlightOps.class.getName());

    private final EntityStorage storage;
    private final SessionFactory sessionFactory;

    public TransportFlightOps(EntityStorage storage, SessionFactory sessionFactory) {
        this.storage = storage;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public List<TransportFlight> whereHeartbeatDtBelow(LocalDateTime threshold, int resultLimit) {
        BM.start("TransportFlightOps.whereHeartbeatDtBelow");
        try {
            return storage.filter(TransportFlight.class,
                    (transportFlight) -> transportFlight.getHeartbeatDt() != null && transportFlight.getHeartbeatDt().isBefore(threshold),
                    resultLimit);
        } finally {
            BM.stop();
        }
    }

    @Override
    protected Class getEntityClass() {
        return TransportFlightEntity.class;
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

    public void switchToStatus(TransportFlight transportFlight, int status, String msg) {
        try (Session session = openSession()) {
            HibernateUtils.transaction(session, "TransportFlightOps.switchToStatus", () -> {

                TransportFlightEntity _transportFlight = session.load(TransportFlightEntity.class, transportFlight.getId());

                _transportFlight.setStatus(status);
                _transportFlight.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

                session.update(_transportFlight);

                session.save(EventLog.make(_transportFlight, msg));

            });
        }
    }

    public void switchToStatusWithoutHeartbeat(TransportFlight transportFlight, int status, String msg) {
        try (Session session = openSession()) {
            HibernateUtils.transaction(session, "TransportFlightOps.switchToStatusWithoutHeartbeat", () -> {

                TransportFlightEntity _transportFlight = session.load(TransportFlightEntity.class, transportFlight.getId());

                _transportFlight.setStatus(status);
                _transportFlight.setHeartbeatDt(null);

                session.update(_transportFlight);

                session.save(EventLog.make(_transportFlight, msg));

            });
        }
    }
}
