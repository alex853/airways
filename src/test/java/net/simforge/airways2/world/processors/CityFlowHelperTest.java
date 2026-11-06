package net.simforge.airways2.world.processors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CityFlowHelperTest {
    @Test
    public void calcFlowUnits_dist_500nm() {
        assertEquals(1.0, CityFlowHelper.calcDistanceUnits(500));
    }

    @Test
    public void calcFlowUnits_dist_1000nm() {
        assertEquals(0.5, CityFlowHelper.calcDistanceUnits(1000));
    }

    @Test
    public void calcFlowUnits_dist_250nm() {
        assertEquals(1.0, CityFlowHelper.calcDistanceUnits(250));
    }

    @Test
    public void calcFlowUnits_dist_100nm() {
        assertEquals(0.25, CityFlowHelper.calcDistanceUnits(100));
    }

    @Test
    public void calcFlowUnits_dist_50nm() {
        assertEquals(0.0, CityFlowHelper.calcDistanceUnits(50));
    }

    @Test
    public void calcFlowUnits_dist_49nm() {
        assertEquals(0.0, CityFlowHelper.calcDistanceUnits(49));
    }

    @Test
    public void groupSizeRange_thickFlowGetsBigGroups() {
        assertArrayEquals(new int[] {9, 10}, CityFlowHelper.groupSizeRange(70));
        assertArrayEquals(new int[] {9, 10}, CityFlowHelper.groupSizeRange(10));
    }

    @Test
    public void groupSizeRange_thinFlowGetsSmallGroups() {
        assertArrayEquals(new int[] {1, 2}, CityFlowHelper.groupSizeRange(0.04));
        assertArrayEquals(new int[] {1, 2}, CityFlowHelper.groupSizeRange(0));
        assertArrayEquals(new int[] {1, 2}, CityFlowHelper.groupSizeRange(1));
    }

    private static final long DAY = 24 * 3600;

    @Test
    public void catchUpGroupSize_theCandidateIsCoveredByTheLag_theCandidate() {
        // the lag is 10 days, a passenger needs half a day: 20 passengers need exactly 10 days
        assertEquals(10, CityFlowHelper.catchUpGroupSize(10 * DAY, 0.5 * DAY, 10));
        assertEquals(20, CityFlowHelper.catchUpGroupSize(10 * DAY, 0.5 * DAY, 20));
    }

    @Test
    public void catchUpGroupSize_theCandidateIsInTheFuture_cutProportionally() {
        // the lag is 200 days, the candidate needs 300 or 600 days
        assertEquals(7, CityFlowHelper.catchUpGroupSize(200 * DAY, 30 * DAY, 10));
        assertEquals(7, CityFlowHelper.catchUpGroupSize(200 * DAY, 30 * DAY, 20));
        assertEquals(1, CityFlowHelper.catchUpGroupSize(8 * DAY, 23.3 * DAY, 10));
        assertEquals(1, CityFlowHelper.catchUpGroupSize(8 * DAY, Double.POSITIVE_INFINITY, 15));
    }

    @Test
    public void groupSizeRange_middleFlowGetsWindowAroundPassengersPerDay() {
        assertArrayEquals(new int[] {4, 6}, CityFlowHelper.groupSizeRange(5));
        assertArrayEquals(new int[] {2, 4}, CityFlowHelper.groupSizeRange(3.2));
    }
}