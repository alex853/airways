/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.cityflows;

import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.AirwaysApp;
import net.simforge.airways.model.flow.City2CityFlow;
import net.simforge.airways.model.flow.CityFlow;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.processengine.ProcessEngineBuilder;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.RealTimeMachine;
import net.simforge.airways.processes.journey.activity.LookingForPersons;
import net.simforge.commons.HeartbeatTask;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class City2CityFlowTask extends HeartbeatTask<City2CityFlow> {

    private final SessionFactory sessionFactory;
    private ProcessEngine engine;

    public City2CityFlowTask() {
        this(AirwaysApp.getSessionFactory());
    }

    public City2CityFlowTask(SessionFactory sessionFactory) {
        super("City2CityFlow", sessionFactory);
        this.sessionFactory = sessionFactory;
    }

    @Override
    protected void startup() {
        super.startup();

        engine = ProcessEngineBuilder.create()
                .withTimeMachine(new RealTimeMachine())
                .withSessionFactory(sessionFactory)
                .build();

        BM.setLoggingPeriod(ChronoUnit.HOURS.getDuration().toMillis());
    }

    @Override
    protected City2CityFlow heartbeat(City2CityFlow _flow) {
        BM.start("City2CityFlowTask.heartbeat");
        try (Session session = sessionFactory.openSession()) {
            City2CityFlow flow = session.get(City2CityFlow.class, _flow.getId());

            HibernateUtils.transaction(session, () -> {
                if (!flow.getActive()) {
                    logger.warn("City2CityFlow {}-{} - inactive, heartbeat was set to null", flow.getFromFlow().getCity().getName(), flow.getToFlow().getCity().getName());
                    session.update(flow);
                    return;
                }

                LocalDateTime now = JavaTime.nowUtc();

                CityFlow fromCityFlow = flow.getFromFlow();
                int dailyFlow = CityFlowOps.getDailyFlow(fromCityFlow);

                double flowToDistribute = dailyFlow * (Duration.between(flow.getAccumulatedFlowDt(), now).toMillis() / (double) CityFlowOps.DAY);

                double flowIncrement = flowToDistribute * flow.getPercentage() * CityFlowOps.boundAvailability(flow.getAvailability());
                flow.setAccumulatedFlow(flow.getAccumulatedFlow() + flowIncrement);
                flow.setAccumulatedFlowDt(now);

                if (flow.getNextGroupSize() == 0) {
                    flow.setNextGroupSize(CityFlowOps.randomGroupSize());
                    logger.debug("City2CityFlow {}-{} - next journey will be for group of {} persons", flow.getFromFlow().getCity().getName(), flow.getToFlow().getCity().getName(), flow.getNextGroupSize());
                }

                if (flow.getAccumulatedFlow() >= flow.getNextGroupSize()) {
                    boolean directOrBackDirection = CityFlowOps.randomDirection();

                    logger.info("City2CityFlow {}-{} - generating journey for group of {} persons, direct direction - {}", flow.getFromFlow().getCity().getName(), flow.getToFlow().getCity().getName(), flow.getNextGroupSize(), directOrBackDirection);

                    Journey journey = JourneyOps.create(session, flow, directOrBackDirection);
                    engine.startActivity(session, LookingForPersons.class, journey, JavaTime.nowUtc().plusDays(1));

                    flow.setAccumulatedFlow(flow.getAccumulatedFlow() - flow.getNextGroupSize());
                    flow.setNextGroupSize(CityFlowOps.randomGroupSize());
                }

                flow.setHeartbeatDt(CityFlowOps.getNextC2CFlowHeartbeatDt(flow));

                session.update(flow);
            });

            return flow;
        } finally {
            BM.stop();
        }
    }
}
