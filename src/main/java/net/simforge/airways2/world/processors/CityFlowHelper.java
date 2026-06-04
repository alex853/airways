package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.CityFlows;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CityFlowHelper {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(CityFlowHelper.class);

    public static final int REDISTRIBUTION_PERIOD = 24 * Time.ONE_HOUR;
    public static final float FLOW_UNITS_THRESHOLD = 0.1f;
    private static final int ONE_DISTANCE_UNIT_NM = 500;
    private static final int FULL_UNITS_MINIMAL_DISTANCE_NM = 250;
    private static final int ZERO_UNITS_DISTANCE_NM = 50;

    public static final float BASE_MOBILITY_PERCENT = 0.001f; // 0.1%

    public static final float DEFAULT_ATTRACTION_FACTOR = 1.0f;
    public static final float DEFAULT_MOBILITY_FACTOR = 1.0f;

    private static final float MIN_SUCCESS_RATE = 0.00001f;
    public static final float STARTING_SUCCESS_RATE = 0.33f;
    private static final float MAX_SUCCESS_RATE = 1.0f;

    public static float getFlowUnits(final World world, final CityFlows.Flow fromCityFlow, final CityFlows.Flow toCityFlow) {
        final Cities.City fromCity = world.cities().byId(fromCityFlow.getId()).orElseThrow();
        final Cities.City toCity = world.cities().byId(toCityFlow.getId()).orElseThrow();

        final float dist = (float) Geo.distance(fromCity.getCoords(), toCity.getCoords());
        float distanceUnits = calcDistanceUnits(dist);

        final float fromCityPopulationUnit = fromCity.getPopulation() / 1000000.0f;
        final float toCityPopulationUnit = toCity.getPopulation() / 1000000.0f;

        final float attractionUnits = toCityFlow.getAttractionFactor();

        return attractionUnits * distanceUnits * fromCityPopulationUnit * toCityPopulationUnit;
    }

    // higher attraction - higher flow units
    // longer distance - lower flow units
    public static float calcDistanceUnits(final float dist) {
        if (dist <= ZERO_UNITS_DISTANCE_NM) {
            return 0;
        } else if (dist <= FULL_UNITS_MINIMAL_DISTANCE_NM) {
            return (dist - ZERO_UNITS_DISTANCE_NM) / (FULL_UNITS_MINIMAL_DISTANCE_NM - ZERO_UNITS_DISTANCE_NM);
        } else {
            final float distUnits = Math.max(dist / ONE_DISTANCE_UNIT_NM, 1);
            return 1 / distUnits;
        }
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

        return (int) (requiredFlowToDistribute * Time.ONE_DAY / dailyFlow);
    }

    public static int getDailyFlow(final World world, City2CityFlows.Flow c2cFlow) {
        final CityFlows.Flow fromCityFlow = world.cityFlows().byCityFlow(c2cFlow).orElseThrow();
        final Cities.City fromCity = world.cities().byId(c2cFlow.getFromCityId()).orElseThrow();
        final float mobilityFactor = fromCityFlow.getMobilityFactor();
        return (int) (fromCity.getPopulation() * BASE_MOBILITY_PERCENT * mobilityFactor);
    }

    public static float boundSuccessRate(final float successRate) {
        return Math.min(Math.max(successRate, MIN_SUCCESS_RATE), MAX_SUCCESS_RATE);
    }

    private static final int[] ultraAttractionThresholds = new int[] {20, 50, 70};
    private static final int[] highAttractionThresholds = new int[] {10, 30, 60};
    private static final int[] mediumAttractionThresholds = new int[] {5, 15, 30};
    private static final int[] basicThresholds = new int[] {0, 7, 20};

    public static CabinLayout.Service randomCabinService(float attractionFactor) {
        int[] thresholds = attractionFactor < 1.5 ? basicThresholds
                : attractionFactor < 10 ? mediumAttractionThresholds
                  : attractionFactor < 30 ? highAttractionThresholds
                    : ultraAttractionThresholds;

        final int rnd = (int) (Math.random() * 100);
        if (rnd <= thresholds[0]) {
            return CabinLayout.Service.F;
        } else if (rnd <= thresholds[1]) {
            return CabinLayout.Service.J;
        } else if (rnd <= thresholds[2]) {
            return CabinLayout.Service.W;
        } else {
            return CabinLayout.Service.Y;
        }
    }
}
