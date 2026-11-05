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
    public void groupSizeRange_farBehindAndTheBiggestGroupIsInThePast_theBiggest() {
        // the lag is 10 days, the biggest group needs 5 days; a thin flow too
        assertArrayEquals(new int[] {10, 10}, CityFlowHelper.groupSizeRange(0.04, 10 * DAY, 5 * DAY));
        assertArrayEquals(new int[] {10, 10}, CityFlowHelper.groupSizeRange(0.04, 10 * DAY, 10 * DAY));
    }

    @Test
    public void groupSizeRange_farBehindAndTheBiggestGroupIsInTheFuture_cutProportionally() {
        assertArrayEquals(new int[] {7, 7}, CityFlowHelper.groupSizeRange(70, 200 * DAY, 300 * DAY));
        assertArrayEquals(new int[] {1, 1}, CityFlowHelper.groupSizeRange(70, 8 * DAY, 233 * DAY));
        assertArrayEquals(new int[] {1, 1}, CityFlowHelper.groupSizeRange(70, 8 * DAY, Double.POSITIVE_INFINITY));
    }

    @Test
    public void groupSizeRange_notFarBehind_byTheThicknessOfTheFlow() {
        assertArrayEquals(new int[] {1, 2}, CityFlowHelper.groupSizeRange(0.04, 7 * DAY, 300 * DAY));
        assertArrayEquals(new int[] {9, 10}, CityFlowHelper.groupSizeRange(70, 0, 300 * DAY));
        assertArrayEquals(new int[] {1, 2}, CityFlowHelper.groupSizeRange(0.04, -5 * DAY, 300 * DAY));
    }

    @Test
    public void groupSizeRange_middleFlowGetsWindowAroundPassengersPerDay() {
        assertArrayEquals(new int[] {4, 6}, CityFlowHelper.groupSizeRange(5));
        assertArrayEquals(new int[] {2, 4}, CityFlowHelper.groupSizeRange(3.2));
    }
}