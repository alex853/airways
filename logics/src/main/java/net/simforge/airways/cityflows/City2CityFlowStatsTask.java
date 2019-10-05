/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.cityflows;

import net.simforge.airways.persistence.AirwaysApp;
import net.simforge.airways.persistence.model.flow.City2CityFlow;
import net.simforge.airways.persistence.model.flow.City2CityFlowStats;
import net.simforge.commons.HeartbeatTask;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.temporal.ChronoUnit;

public class City2CityFlowStatsTask extends HeartbeatTask<City2CityFlowStats> {

    private final SessionFactory sessionFactory;

    public City2CityFlowStatsTask() {
        this(AirwaysApp.getSessionFactory());
    }

    public City2CityFlowStatsTask(SessionFactory sessionFactory) {
        super("City2CityFlowStats", sessionFactory);
        this.sessionFactory = sessionFactory;
    }

    @Override
    protected void startup() {
        super.startup();

        BM.setLoggingPeriod(ChronoUnit.HOURS.getDuration().toMillis());
    }

    @Override
    protected City2CityFlowStats heartbeat(City2CityFlowStats _stats) {
        BM.start("City2CityFlowStatsTask.heartbeat");
        try (Session session = sessionFactory.openSession()) {
            City2CityFlowStats stats = session.get(City2CityFlowStats.class, _stats.getId());

            HibernateUtils.transaction(session, () -> {
                City2CityFlow c2cFlow = stats.getC2cFlow();

                double availabilityBefore = c2cFlow.getAvailability();
                double availabilityDelta = CityFlowOps.calcAvailabilityDelta(c2cFlow, stats);
                double availabilityAfter = CityFlowOps.boundAvailability(availabilityBefore + availabilityDelta);

                stats.setAvailabilityBefore(availabilityBefore);
                stats.setAvailabilityDelta(availabilityDelta);
                stats.setAvailabilityAfter(availabilityAfter);
                stats.setHeartbeatDt(null);

                c2cFlow.setAvailability(availabilityAfter);
                c2cFlow.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));

                session.update(stats);
                session.update(c2cFlow);
            });

            return stats;
        } finally {
            BM.stop();
        }
    }
}
