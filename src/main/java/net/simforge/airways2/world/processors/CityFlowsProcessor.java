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
        // todo ak3 city flow status?
        log.info("city flow #{}, '{}' - lets redistribute", thisCity.get().getId(), city.getName());

        final Collection<CityFlows.Flow> reachableCities = world.cityFlows().all().stream()
                .filter(f -> f.getId() != thisCity.get().getId())
                .filter(f -> CityFlowOps.getFlowUnits(world, f, thisCity.get()) >= CityFlowOps.FLOW_UNITS_THRESHOLD)
                .toList();
        final float totalFlowUnits = reachableCities.stream()
                .map(f -> CityFlowOps.getFlowUnits(world, f, thisCity.get()))
                .reduce(0.0f, Float::sum);
        final Map<Integer, City2CityFlows.Flow> existingC2CFlows = world.city2cityFlows()
                .allFromCityId(thisCity.get().getId()).stream()
                .collect(Collectors.toMap(City2CityFlows.Flow::getToCityId, f -> f));
        final Set<Integer> c2cFlowsToBeDeactivated = new TreeSet<>(existingC2CFlows.keySet());
        log.info("city flow #{}, '{}' - there are {} reachable cities, total flow units {}, there are {} existing c2cflows", thisCity.get().getId(), city.getName(), reachableCities.size(), totalFlowUnits, existingC2CFlows.size());

        reachableCities.forEach(toCity -> {
            c2cFlowsToBeDeactivated.remove(toCity.getId());

            final float flowUnits = CityFlowOps.getFlowUnits(world, toCity, thisCity.get());
            final float flowFraction = flowUnits / Math.max(totalFlowUnits, 0.000001f);

            final City2CityFlows.Flow c2cFlow = existingC2CFlows.computeIfAbsent(toCity.getId(),
                    (a) -> world.city2cityFlows().createInactive(thisCity.get().getId(), toCity.getId()));
            if (!c2cFlow.isActive()) {
                c2cFlow.setActive(true);

                c2cFlow.setNextGroupSize(CityFlowOps.randomGroupSize());
                c2cFlow.setAccumulatedFlow(0.0f);
                c2cFlow.setAccumulatedFlowTime(world.getWorldTime());
            }

            c2cFlow.setFlowFraction(flowFraction);
            c2cFlow.setHeartbeatTime(world.getWorldTime() + CityFlowOps.calcTimeToAccumulateFlow(world, c2cFlow));
            final String toCityName = world.cities().byId(toCity.getId()).orElseThrow().getName();
            log.info("city flow #{}, '{}' - flow to city #{}, '{}' is active, flow units {}, percentage {}, next group size {}, acc flow {}",
                    thisCity.get().getId(), city.getName(), toCity.getId(), toCityName, flowUnits, df3.format(flowFraction*100), c2cFlow.getNextGroupSize(), c2cFlow.getAccumulatedFlow());
        });

        c2cFlowsToBeDeactivated.forEach(toCityId -> {
            final City2CityFlows.Flow c2cFlow = existingC2CFlows.get(toCityId);
            c2cFlow.setActive(false);
            c2cFlow.setHeartbeatTime(0);
            final String toCityName = world.cities().byId(toCityId).orElseThrow().getName();
            log.info("city flow #{}, '{}' - flow to city #{}, '{}' is inactive", thisCity.get().getId(), city.getName(), toCityId, toCityName);
        });

        thisCity.get().setLastRedistributionTime(world.getWorldTime());
        log.info("city flow #{}, '{}' - completed", thisCity.get().getId(), city.getName());
    }
}
