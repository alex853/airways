package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
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
        final String fromCity = world.cities().byId(c2cFlow.getFromCityId()).orElseThrow().getName();
        final String toCity = world.cities().byId(c2cFlow.getFromCityId()).orElseThrow().getName();

        if (c2cFlow.getSuccessRate() == 0) {
            c2cFlow.setSuccessRate(CityFlowHelper.STARTING_SUCCESS_RATE);
            log.warn("City2CityFlow {}-{} - success rate was ZERO, set to default {}", fromCity, toCity, CityFlowHelper.STARTING_SUCCESS_RATE);
        }

        if (!c2cFlow.isActive()) {
            c2cFlow.setHeartbeatTime(0);
            log.warn("City2CityFlow {}-{} - inactive, heartbeat set to null", fromCity, toCity);
            return;
        }

        if (c2cFlow.getNextGroupSize() == 0) {
            c2cFlow.setNextGroupSize(CityFlowHelper.randomGroupSize());
            log.warn("City2CityFlow {}-{} - next group size was zero, set to {}", fromCity, toCity, c2cFlow.getNextGroupSize());
        }

        final int timeToAccumulateFlow = (c2cFlow.getAccumulatedFlowTime() != 0 ? c2cFlow.getAccumulatedFlowTime() : c2cFlow.getHeartbeatTime())
                + CityFlowHelper.calcTimeToAccumulateFlow(world, c2cFlow);
        if (timeToAccumulateFlow > worldTime) {
            c2cFlow.setHeartbeatTime(timeToAccumulateFlow);
            log.warn("City2CityFlow {}-{} - time to accumulate next group has not been reached world time, current accumulated time {}, time to accumulate next group {}, waiting",
                    fromCity, toCity, Time.toLdt(c2cFlow.getAccumulatedFlowTime()), Time.toLdt(timeToAccumulateFlow));
            return;
        }

        final CabinLayout.Service service = CityFlowHelper.randomCabinService();
        final int groupSizeBeingGenerated = c2cFlow.getNextGroupSize();
        world.journeyControl().create(c2cFlow, service);

        c2cFlow.setNextGroupSize(CityFlowHelper.randomGroupSize());
        c2cFlow.setAccumulatedFlow(0);
        c2cFlow.setAccumulatedFlowTime(timeToAccumulateFlow);
        c2cFlow.setHeartbeatTime(c2cFlow.getAccumulatedFlowTime() + CityFlowHelper.calcTimeToAccumulateFlow(world, c2cFlow));

        log.info("City2CityFlow {}-{} - GENERATING journey for group of {} persons, service {}, new next group size {}, new time to accumulate {}, new heartbeat time {}",
                fromCity, toCity, groupSizeBeingGenerated, service.name(), c2cFlow.getNextGroupSize(),  Time.toLdt(c2cFlow.getAccumulatedFlowTime()), Time.toLdt(c2cFlow.getHeartbeatTime()));
    }
}
