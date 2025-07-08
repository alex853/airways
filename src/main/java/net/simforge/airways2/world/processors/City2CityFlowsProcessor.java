package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class City2CityFlowsProcessor {
    private static final Logger log = LoggerFactory.getLogger(City2CityFlowsProcessor.class);
    private static long lastRun;

    public static void process(final World world) {
        // todo ak0 remove this "rate limiter"
        if (System.currentTimeMillis() - lastRun < 300000) {
            return;
        } else {
            lastRun = System.currentTimeMillis();
        }

        final int worldTime = world.getWorldTime();
        final Optional<City2CityFlows.Flow> flowO = world.city2cityFlows().nextForHeartbeat(worldTime);
        if (flowO.isEmpty()) {
            return;
        }

        final City2CityFlows.Flow c2cFlow = flowO.get();
        c2cFlow.setSuccessRate(0.1f);

        log.info("City2CityFlow {}-{} - a/t {}, h/t {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), c2cFlow.getAccumulatedFlowTime(), c2cFlow.getHeartbeatTime());

        if (!c2cFlow.isActive()) {
            c2cFlow.setHeartbeatTime(0);
            log.warn("City2CityFlow {}-{} - inactive, heartbeat was set to null", c2cFlow.getFromCityId(), c2cFlow.getToCityId());
            return;
        }

        if (c2cFlow.getNextGroupSize() == 0) {
            c2cFlow.setNextGroupSize(CityFlowOps.randomGroupSize());
            log.warn("City2CityFlow {}-{} - next group size was zero, set to {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), c2cFlow.getNextGroupSize());
            return;
        }

        final int timeToAccumulateRemaining = CityFlowOps.calcTimeToAccumulateFlow(world, c2cFlow);
        final int timeToAccumulateFlow = (c2cFlow.getAccumulatedFlowTime() != 0 ? c2cFlow.getAccumulatedFlowTime() : c2cFlow.getHeartbeatTime()) + timeToAccumulateRemaining;
        log.info("City2CityFlow {}-{} - acc time remaining {}, acc time {}, acc time ldt {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), timeToAccumulateRemaining, timeToAccumulateFlow, Time.toLdt(timeToAccumulateFlow));

        if (timeToAccumulateFlow > worldTime) {
            c2cFlow.setHeartbeatTime(timeToAccumulateFlow);
            log.warn("City2CityFlow {}-{} - timeToAcc {} did not reach world time {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), Time.toLdt(timeToAccumulateFlow), Time.toLdt(worldTime));
            return;
        }

        final boolean directOrBackDirection = CityFlowOps.randomDirection();

        log.info("City2CityFlow {}-{} - GENERATING journey for group of {} persons, direct direction - {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), c2cFlow.getNextGroupSize(), directOrBackDirection);

// todo ak1            Journey journey = JourneyOps.create(session, c2cFlow, directOrBackDirection);
// todo ak1            AirwaysApp.getScheduling().startActivity(session, LookingForPersons.class, journey, JavaTime.nowUtc().plusDays(1));

        c2cFlow.setNextGroupSize(CityFlowOps.randomGroupSize());

        c2cFlow.setAccumulatedFlow(0);
        c2cFlow.setAccumulatedFlowTime(timeToAccumulateFlow);

        c2cFlow.setHeartbeatTime(timeToAccumulateFlow + CityFlowOps.calcTimeToAccumulateFlow(world, c2cFlow));
        log.info("City2CityFlow {}-{} - new next group size {}, acc time {}, next heartbeat time {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), c2cFlow.getNextGroupSize(),  Time.toLdt(c2cFlow.getAccumulatedFlowTime()), Time.toLdt(c2cFlow.getHeartbeatTime()));


//        final int dailyFlow = CityFlowOps.getDailyFlow(world, c2cFlow);
//        final float flowToDistribute = dailyFlow * (float) (worldTime - c2cFlow.getAccumulatedFlowTime()) / (float) Time.ONE_DAY;
//        final float flowIncrement = flowToDistribute * c2cFlow.getFlowFraction() * c2cFlow.getSuccessRate();
//        log.warn("City2CityFlow {}-{} - dailyFlow {}, flowToDist {}, flowIncrement {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), dailyFlow, flowToDistribute, flowIncrement);

//        c2cFlow.setAccumulatedFlow(c2cFlow.getAccumulatedFlow() + flowIncrement);
//        c2cFlow.setAccumulatedFlowTime(worldTime);

//        if (c2cFlow.getNextGroupSize() == 0) {
//            c2cFlow.setNextGroupSize(CityFlowOps.randomGroupSize());
//            log.warn("City2CityFlow {}-{} - next journey will be for group of {} persons", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), c2cFlow.getNextGroupSize());
//        }
//
//        if (c2cFlow.getAccumulatedFlow() >= c2cFlow.getNextGroupSize()) {
//            final boolean directOrBackDirection = CityFlowOps.randomDirection();
//
//            log.info("City2CityFlow {}-{} - generating journey for group of {} persons, direct direction - {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), c2cFlow.getNextGroupSize(), directOrBackDirection);

// todo ak1            Journey journey = JourneyOps.create(session, c2cFlow, directOrBackDirection);
// todo ak1            AirwaysApp.getScheduling().startActivity(session, LookingForPersons.class, journey, JavaTime.nowUtc().plusDays(1));

//            c2cFlow.setAccumulatedFlow(c2cFlow.getAccumulatedFlow() - c2cFlow.getNextGroupSize());
//            c2cFlow.setNextGroupSize(CityFlowOps.randomGroupSize());
//        }
//
//        c2cFlow.setHeartbeatTime(worldTime + CityFlowOps.calcTimeToAccumulateFlow(world, c2cFlow));
    }
}
