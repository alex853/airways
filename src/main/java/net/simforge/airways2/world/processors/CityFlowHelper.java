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

import java.util.concurrent.ThreadLocalRandom;

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

    public static final int MIN_GROUP_SIZE = 1;
    public static final int MAX_GROUP_SIZE = 10;
    // a group of the typical size of a flow is accumulated in about this time
    public static final double TARGET_GROUP_INTERVAL_DAYS = 1.0;

    // a flow whose clock is behind the world by more than this is catching up: it gets the big groups (bigger than the usual maximum) to create fewer journeys
    public static final int CATCH_UP_LAG = 7 * Time.ONE_DAY;
    public static final int CATCH_UP_MIN_GROUP_SIZE = 10;
    public static final int CATCH_UP_MAX_GROUP_SIZE = 20;

    /**
     * The size of the next group. A flow which is far behind the world (its accumulated flow time is more than CATCH_UP_LAG in the past) is catching up:
     * it gets a random size from CATCH_UP_MIN_GROUP_SIZE to CATCH_UP_MAX_GROUP_SIZE (see catchUpGroupSize). Otherwise the size depends on the thickness
     * of the flow: a thick flow (10 passengers per day and more) gets the groups of 9-10, a thin one (a passenger per day and less) gets 1-2, the ones
     * between get the window of three sizes around passengers per day. The passengers per day stay the same, as the time to accumulate a group is
     * proportional to its size, so a thick flow has fewer and bigger groups, a thin flow has small groups, which are accumulated in a reasonable time
     * and so are served from time to time.
     *
     * @param accumulatedFlowTime the clock of the flow the next group starts from
     */
    public static int randomGroupSize(final World world, final City2CityFlows.Flow flow, final int accumulatedFlowTime) {
        final long lag = (long) world.getWorldTime() - accumulatedFlowTime;
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        if (lag > CATCH_UP_LAG) {
            final int candidate = random.nextInt(CATCH_UP_MIN_GROUP_SIZE, CATCH_UP_MAX_GROUP_SIZE + 1);
            return catchUpGroupSize(lag, timeToAccumulateGroup(world, flow, 1), candidate);
        }
        final int[] range = groupSizeRange(getDailyPassengers(world, flow));
        return random.nextInt(range[0], range[1] + 1);
    }

    /** Seconds the flow needs to accumulate a whole group of the size from zero. */
    public static double timeToAccumulateGroup(final World world, final City2CityFlows.Flow flow, final int groupSize) {
        final int dailyFlow = getDailyFlow(world, world.cityFlows().byCityFlow(flow).orElseThrow());
        return groupSize / (double) flow.getFlowFraction() / flow.getSuccessRate() * Time.ONE_DAY / dailyFlow;
    }

    /**
     * The size for a flow which is catching up: the candidate size if the time to accumulate it is not later than now (the lag covers it), otherwise
     * the candidate cut proportionally to the lag (the lag of 200 days and 300 days for the candidate of 10 give 10 * 200 / 300, that is 7), not less
     * than the smallest size.
     */
    static int catchUpGroupSize(final long lag, final double timeToAccumulateOne, final int candidate) {
        final double timeToAccumulateCandidate = timeToAccumulateOne * candidate;
        if (timeToAccumulateCandidate <= lag) {
            return candidate;
        }
        return (int) Math.max(Math.round(candidate * lag / timeToAccumulateCandidate), MIN_GROUP_SIZE);
    }

    /** The passengers per day the flow generates, all factors taken into account. */
    public static double getDailyPassengers(final World world, final City2CityFlows.Flow flow) {
        final int dailyFlow = getDailyFlow(world, world.cityFlows().byCityFlow(flow).orElseThrow());
        return dailyFlow * (double) flow.getFlowFraction() * flow.getSuccessRate();
    }

    /** The bounds [min, max] (inclusive) of the next group size for the flow of the passengers per day. */
    static int[] groupSizeRange(final double dailyPassengers) {
        final double center = Math.min(Math.max(dailyPassengers * TARGET_GROUP_INTERVAL_DAYS, MIN_GROUP_SIZE), MAX_GROUP_SIZE);
        final int rounded = (int) Math.round(center);
        return new int[] {Math.max(MIN_GROUP_SIZE, rounded - 1), Math.min(MAX_GROUP_SIZE, rounded + 1)};
    }

    public static int calcTimeToAccumulateFlow(final World world, final City2CityFlows.Flow flow) {
        if (flow.getAccumulatedFlow() >= flow.getNextGroupSize()) {
            return 0;
        }

        final int dailyFlow = getDailyFlow(world, world.cityFlows().byCityFlow(flow).orElseThrow());
        final float remainingFlow = flow.getNextGroupSize() - flow.getAccumulatedFlow();
        final double requiredFlowToDistribute = remainingFlow / flow.getFlowFraction() / flow.getSuccessRate();

        return (int) (requiredFlowToDistribute * Time.ONE_DAY / dailyFlow);
    }

    public static int getDailyFlow(final World world, CityFlows.Flow fromCityFlow) {
        final Cities.City fromCity = world.cities().byId(fromCityFlow.getId()).orElseThrow();
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
