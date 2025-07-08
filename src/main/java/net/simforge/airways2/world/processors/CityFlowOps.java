package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.CityFlows;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CityFlowOps {
    private static final Logger log = LoggerFactory.getLogger(CityFlowOps.class);

    public static final int REDISTRIBUTION_PERIOD = 24 * Time.ONE_HOUR;
    public static final float FLOW_UNITS_THRESHOLD = 0.1f;
    private static final int ONE_DISTANCE_UNIT_NM = 500;
    public static final float BASE_MOBILITY_PERCENT = 0.001f; // 0.1%

    public static final float DEFAULT_ATTRACTION_FACTOR = 1.0f;
    public static final float DEFAULT_MOBILITY_FACTOR = 1.0f;

    private static final float MIN_SUCCESS_RATE = 0.00001f;
    public static final float STARTING_SUCCESS_RATE = 0.1f;
    private static final float MAX_SUCCESS_RATE = 1.0f;

    public static float getFlowUnits(final World world, final CityFlows.Flow fromCityFlow, final CityFlows.Flow toCityFlow) {
        final float attractionUnits = toCityFlow.getAttractionFactor();

        final Cities.City fromCity = world.cities().byId(fromCityFlow.getId()).orElseThrow();
        final Cities.City toCity = world.cities().byId(toCityFlow.getId()).orElseThrow();

        final float dist = (float) Geo.distance(fromCity.getCoords(), toCity.getCoords());

        final float distUnits = Math.max(dist / ONE_DISTANCE_UNIT_NM, 1);

        // higher attraction - higher flow units
        // longer distance - lower flow units
        return attractionUnits / distUnits;
    }

    public static int randomGroupSize() {
        return (int) (Math.random() * 10) + 1;
    }

    public static int calcTimeToAccumulateFlow(final World world, final City2CityFlows.Flow flow) {
        if (flow.getAccumulatedFlow() >= flow.getNextGroupSize()) {
            return 0;
        }

        final int dailyFlow = getDailyFlow(world, flow);
        final float remainingFlow = flow.getNextGroupSize() - flow.getAccumulatedFlow();
        final double requiredFlowToDistribute = remainingFlow / flow.getFlowFraction() / flow.getSuccessRate();

        final int time = (int) (requiredFlowToDistribute * Time.ONE_DAY / dailyFlow);
        log.warn("calcTimeToAccumulateFlow - dailyFlow {}, remainingFlow {}, flow fraction {}, success rate {}, requiredFlowToDistribute {}, time {}",
                dailyFlow, remainingFlow, flow.getFlowFraction(), flow.getSuccessRate(), requiredFlowToDistribute, time);

        return time;
    }

    public static boolean randomDirection() {
        return Math.random() < 0.5;
    }

    public static int getDailyFlow(final World world, City2CityFlows.Flow c2cFlow) {
        final CityFlows.Flow fromCityFlow = world.cityFlows().fromCityFlow(c2cFlow).orElseThrow();
        final Cities.City fromCity = world.cities().byId(c2cFlow.getFromCityId()).orElseThrow();
        final float mobilityFactor = fromCityFlow.getMobilityFactor();
        return (int) (fromCity.getPopulation() * BASE_MOBILITY_PERCENT * mobilityFactor);
    }

    public static float boundSuccessRate(final float successRate) {
        return Math.min(Math.max(successRate, MIN_SUCCESS_RATE), MAX_SUCCESS_RATE);
    }
}
