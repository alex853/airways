package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.Journeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class City2CityFlowsProcessor {
    private static final Logger log = LoggerFactory.getLogger(City2CityFlowsProcessor.class);

    public static void process(final World world) {
        final int worldTime = world.getWorldTime();
        final Optional<City2CityFlows.Flow> flowO = world.city2cityFlows().nextForHeartbeat(worldTime);
        if (flowO.isEmpty()) {
            return;
        }

        final City2CityFlows.Flow c2cFlow = flowO.get();
        if (c2cFlow.getSuccessRate() == 0) {
            c2cFlow.setSuccessRate(CityFlowHelper.STARTING_SUCCESS_RATE);
            log.warn("City2CityFlow {}-{} - success rate was ZERO, set to default {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), CityFlowHelper.STARTING_SUCCESS_RATE);
        }

        if (!c2cFlow.isActive()) {
            c2cFlow.setHeartbeatTime(0);
            log.warn("City2CityFlow {}-{} - inactive, heartbeat was set to null", c2cFlow.getFromCityId(), c2cFlow.getToCityId());
            return;
        }

        if (c2cFlow.getNextGroupSize() == 0) {
            c2cFlow.setNextGroupSize(CityFlowHelper.randomGroupSize());
            log.warn("City2CityFlow {}-{} - next group size was zero, set to {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), c2cFlow.getNextGroupSize());
        }

        final int timeToAccumulateRemaining = CityFlowHelper.calcTimeToAccumulateFlow(world, c2cFlow);
        final int timeToAccumulateFlow = (c2cFlow.getAccumulatedFlowTime() != 0 ? c2cFlow.getAccumulatedFlowTime() : c2cFlow.getHeartbeatTime()) + timeToAccumulateRemaining;
        log.info("City2CityFlow {}-{} - raw acc time {}, raw heartbeat time {}, acc time remaining {}, acc time {}, acc time ldt {}",
                c2cFlow.getFromCityId(), c2cFlow.getToCityId(), c2cFlow.getAccumulatedFlowTime(), c2cFlow.getHeartbeatTime(), timeToAccumulateRemaining, timeToAccumulateFlow, Time.toLdt(timeToAccumulateFlow));

        if (timeToAccumulateFlow > worldTime) {
            c2cFlow.setHeartbeatTime(timeToAccumulateFlow);
            log.warn("City2CityFlow {}-{} - acc time {} has not been reached world time {}, waiting", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), Time.toLdt(timeToAccumulateFlow), Time.toLdt(worldTime));
            return;
        }

        final boolean directOrBackDirection = CityFlowHelper.randomDirection(); // todo ak0 'roundtrip support' - remove this
        final CabinLayout.Service service = CityFlowHelper.randomCabinService();

        log.info("City2CityFlow {}-{} - GENERATING journey for group of {} persons, service {}, direct direction - {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), c2cFlow.getNextGroupSize(), service.name(), directOrBackDirection);

        JourneyControl.instance(world).create(c2cFlow, service, directOrBackDirection);
        // todo ak2 AirwaysApp.getScheduling().startActivity(session, LookingForPersons.class, journey, JavaTime.nowUtc().plusDays(1));

        c2cFlow.setNextGroupSize(CityFlowHelper.randomGroupSize());

        c2cFlow.setAccumulatedFlow(0);
        c2cFlow.setAccumulatedFlowTime(timeToAccumulateFlow);

        c2cFlow.setHeartbeatTime(timeToAccumulateFlow + CityFlowHelper.calcTimeToAccumulateFlow(world, c2cFlow));
        log.info("City2CityFlow {}-{} - new next group size {}, new acc time {}, new heartbeat time {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), c2cFlow.getNextGroupSize(),  Time.toLdt(c2cFlow.getAccumulatedFlowTime()), Time.toLdt(c2cFlow.getHeartbeatTime()));
    }
}
