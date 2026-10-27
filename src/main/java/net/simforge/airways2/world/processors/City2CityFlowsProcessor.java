package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Formatting;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.CityFlows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class City2CityFlowsProcessor {
    private static final Logger log = LoggerFactory.getLogger(City2CityFlowsProcessor.class);

    /**
     * How many flows are served in one world step. There are tens of thousands of flows and every served flow gives one group of passengers, so one flow per
     * step (8640 a day) was less than the flows asked for: the flows of the cities lagged behind the world by days and weeks. The earliest flows go first.
     */
    static final int FLOWS_PER_STEP = 10;

    public static void process(final World world) {
        Processing.heartbeat(() -> world.city2cityFlows().nextForHeartbeat(world.getWorldTime()),
                c2cFlow -> processFlow(world, c2cFlow),
                FLOWS_PER_STEP);
    }

    private static void processFlow(final World world, final City2CityFlows.Flow c2cFlow) {
        final int worldTime = world.getWorldTime();
        final String fromCity = Formatting.shortCity(world.cities().byId(c2cFlow.getFromCityId()).orElseThrow().getName());
        final String toCity = Formatting.shortCity(world.cities().byId(c2cFlow.getToCityId()).orElseThrow().getName());

        if (c2cFlow.getSuccessRate() == 0) {
            c2cFlow.setSuccessRate(CityFlowHelper.STARTING_SUCCESS_RATE);
            log.warn("c2c #{}/{} {}-{} - s/r was 0, set default {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), fromCity, toCity, CityFlowHelper.STARTING_SUCCESS_RATE);
        }

        if (!c2cFlow.isActive()) {
            c2cFlow.setHeartbeatTime(0);
            log.warn("c2c #{}/{} {}-{} - inactive, h/b set 0", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), fromCity, toCity);
            return;
        }

        final Optional<CityFlows.Flow> fromCityFlowO = world.cityFlows().byCityId(c2cFlow.getFromCityId());
        if (fromCityFlowO.isEmpty()) {
            log.error("c2c #{}/{} {}-{} - unable to find flow for 'from city'",
                    c2cFlow.getFromCityId(), c2cFlow.getToCityId(), fromCity, toCity);
            // the flow is the first of the due ones until its heartbeat moves, so it would block all the other flows
            c2cFlow.setHeartbeatTime(worldTime + Time.ONE_HOUR);
            return;
        }

        if (c2cFlow.getNextGroupSize() == 0) {
            c2cFlow.setNextGroupSize(CityFlowHelper.randomGroupSize());
            log.warn("c2c #{}/{} {}-{} - next g/s was 0, set {}", c2cFlow.getFromCityId(), c2cFlow.getToCityId(), fromCity, toCity, c2cFlow.getNextGroupSize());
        }

        final int timeToAccumulateFlow = (c2cFlow.getAccumulatedFlowTime() != 0 ? c2cFlow.getAccumulatedFlowTime() : c2cFlow.getHeartbeatTime())
                + CityFlowHelper.calcTimeToAccumulateFlow(world, c2cFlow);
        if (timeToAccumulateFlow > worldTime) {
            c2cFlow.setHeartbeatTime(timeToAccumulateFlow);
            log.warn("c2c #{}/{} {}-{} - acc/t not reached world time, curr acc/t {}, acc/t for next g/s {}, waiting",
                    c2cFlow.getFromCityId(), c2cFlow.getToCityId(), fromCity, toCity, Time.toLdt(c2cFlow.getAccumulatedFlowTime()), Time.toLdt(timeToAccumulateFlow));
            return;
        }

        float attractionFactor = fromCityFlowO.get().getAttractionFactor();

        CabinLayout.Service service = CityFlowHelper.randomCabinService(attractionFactor);
        int groupSizeBeingGenerated = c2cFlow.getNextGroupSize();
        final int journeyId = world.journeyControl().create(c2cFlow, service).getId();

        c2cFlow.setNextGroupSize(CityFlowHelper.randomGroupSize());
        c2cFlow.setAccumulatedFlow(0);
        c2cFlow.setAccumulatedFlowTime(timeToAccumulateFlow);
        c2cFlow.setHeartbeatTime(c2cFlow.getAccumulatedFlowTime() + CityFlowHelper.calcTimeToAccumulateFlow(world, c2cFlow));

        log.info("#{}/{} {}-{}: NEW j/y #{} {}{}, nxt {}, a/t {}, h/b {}",
                c2cFlow.getFromCityId(), c2cFlow.getToCityId(), fromCity, toCity, journeyId, service.name(), groupSizeBeingGenerated, c2cFlow.getNextGroupSize(),
                minutes(c2cFlow.getAccumulatedFlowTime()), minutes(c2cFlow.getHeartbeatTime()));
    }

    private static LocalDateTime minutes(final int time) {
        return Time.toLdt(time).truncatedTo(ChronoUnit.MINUTES);
    }
}
