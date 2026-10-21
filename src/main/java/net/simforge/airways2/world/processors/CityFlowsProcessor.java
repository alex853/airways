package net.simforge.airways2.world.processors;

import static net.simforge.airways2.tools.Formatting.df3;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.CityFlows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class CityFlowsProcessor {
    private static final Logger log = LoggerFactory.getLogger(CityFlowsProcessor.class);

    public static void process(final World world) {
        final Optional<CityFlows.Flow> thisCity = world.cityFlows().nextForRedistribution(world.getWorldTime());
        if (thisCity.isEmpty()) {
            return;
        }

        final Cities.City city = world.cities().byId(thisCity.get().getId()).orElseThrow();
        log.info("c/f #{} {} - lets redistribute", thisCity.get().getId(), city.getName());

        final Collection<CityFlows.Flow> reachableCities = world.cityFlows().all()
                .filter(f -> f.getId() != thisCity.get().getId())
                .filter(f -> CityFlowHelper.getFlowUnits(world, f, thisCity.get()) >= CityFlowHelper.FLOW_UNITS_THRESHOLD)
                .toList();
        final float totalFlowUnits = reachableCities.stream()
                .map(f -> CityFlowHelper.getFlowUnits(world, f, thisCity.get()))
                .reduce(0.0f, Float::sum);
        final Map<Integer, City2CityFlows.Flow> existingC2CFlows = world.city2cityFlows()
                .allFromCityId(thisCity.get().getId())
                .collect(Collectors.toMap(City2CityFlows.Flow::getToCityId, f -> f));
        final Set<Integer> c2cFlowsToBeDeactivated = new TreeSet<>(existingC2CFlows.keySet());
        log.info("c/f #{} {} - there are {} reachable cities, total flow units {}, there are {} existing c2cflows", thisCity.get().getId(), city.getName(), reachableCities.size(), totalFlowUnits, existingC2CFlows.size());

        reachableCities.forEach(toCity -> {
            c2cFlowsToBeDeactivated.remove(toCity.getId());

            final float flowUnits = CityFlowHelper.getFlowUnits(world, toCity, thisCity.get());
            final float flowFraction = flowUnits / Math.max(totalFlowUnits, 0.000001f);

            final City2CityFlows.Flow c2cFlow = existingC2CFlows.computeIfAbsent(toCity.getId(),
                    (a) -> world.city2cityFlows().createInactive(thisCity.get().getId(), toCity.getId()));
            final boolean wasInactive = !c2cFlow.isActive();
            if (wasInactive) {
                c2cFlow.setActive(true);

                c2cFlow.setNextGroupSize(CityFlowHelper.randomGroupSize());
                c2cFlow.setAccumulatedFlow(0.0f);
                c2cFlow.setAccumulatedFlowTime(world.getWorldTime());
            }

            c2cFlow.setFlowFraction(flowFraction);
            if (wasInactive) {
                c2cFlow.setHeartbeatTime(world.getWorldTime() + CityFlowHelper.calcTimeToAccumulateFlow(world, c2cFlow));
            }
            // The heartbeat of a flow which is active already is not touched: it is "the last generation + the time to accumulate the next group". Moving
            // it to "now + the time to accumulate" at every redistribution (once per 24 hours) pushed the flows with a long wait further and further, they
            // never fired, and when they finally did, they generated the journeys of all the missed time in a burst. The new fraction is taken into account
            // when the heartbeat comes: City2CityFlowsProcessor calculates the time to accumulate again and waits more, if it is needed.
            final String toCityName = world.cities().byId(toCity.getId()).orElseThrow().getName();
            log.info("c2c #{}/{} {}-{} - active, flow units {}, percentage {}, next group size {}, acc flow {}",
                    thisCity.get().getId(), toCity.getId(), city.getName(), toCityName, flowUnits, df3.format(flowFraction*100), c2cFlow.getNextGroupSize(), c2cFlow.getAccumulatedFlow());
        });

        c2cFlowsToBeDeactivated.forEach(toCityId -> {
            final City2CityFlows.Flow c2cFlow = existingC2CFlows.get(toCityId);
            c2cFlow.setActive(false);
            c2cFlow.setHeartbeatTime(0);
            final String toCityName = world.cities().byId(toCityId).orElseThrow().getName();
            log.info("c2c #{}/{} {}-{} - inactive", thisCity.get().getId(), toCityId, city.getName(), toCityName);
        });

        thisCity.get().setLastRedistributionTime(world.getWorldTime());
        log.info("c/f #{} {} - completed", thisCity.get().getId(), city.getName());
    }
}
