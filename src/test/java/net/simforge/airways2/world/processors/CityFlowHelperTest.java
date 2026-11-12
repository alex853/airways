package net.simforge.airways2.world.processors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static CityFlowHelper.RateCandidate candidate(final int cityId, final float units, final float successRate) {
        return new CityFlowHelper.RateCandidate(cityId, units, successRate);
    }

    @Test
    public void selectFlowsByRate_thickFlowsAreAllKept() {
        // 41 passengers a day shared by 4 equal destinations at the known success rate 0.3: 3 a day each
        final java.util.Set<Integer> kept = CityFlowHelper.selectFlowsByRate(41, java.util.List.of(
                candidate(1, 0.2f, 0.3f), candidate(2, 0.2f, 0.3f), candidate(3, 0.2f, 0.3f), candidate(4, 0.2f, 0.3f)));

        assertEquals(java.util.Set.of(1, 2, 3, 4), kept);
    }

    @Test
    public void selectFlowsByRate_thinFlowsAreDroppedAndTheirShareGoesToTheOthers() {
        // 10 passengers a day, one strong destination (9 units) and one thin (0.001 units, success rate 0.03): the thin one is under 0.01 a day
        final java.util.Set<Integer> kept = CityFlowHelper.selectFlowsByRate(10, java.util.List.of(
                candidate(1, 9f, 0.3f), candidate(2, 0.001f, 0.03f)));

        assertEquals(java.util.Set.of(1), kept);
    }

    @Test
    public void selectFlowsByRate_theWeakestGoesFirstSoABorderlineOneCanStay() {
        // 1 passenger a day, success rate 0.3 for all: with all three the rates are 0.2, 0.2 and 0.0002 (the last is dropped) and then 0.15 each
        final java.util.Set<Integer> kept = CityFlowHelper.selectFlowsByRate(1, java.util.List.of(
                candidate(1, 1f, 0.3f), candidate(2, 1f, 0.3f), candidate(3, 0.001f, 0.3f)));

        assertEquals(java.util.Set.of(1, 2), kept);
    }

    @Test
    public void selectFlowsByRate_theSuccessRateCounts_andZeroIsTheStartingOne() {
        // equal units; the destination with the known low success rate is thin, the new one (zero) is taken as the starting 0.33
        final java.util.Set<Integer> kept = CityFlowHelper.selectFlowsByRate(0.05, java.util.List.of(
                candidate(1, 1f, 0.0f), candidate(2, 1f, 0.0001f)));

        assertEquals(java.util.Set.of(1), kept);
    }

    @Test
    public void selectFlowsByRate_aCityWithoutPassengersKeepsTheStrongestDestinationOnly() {
        final java.util.Set<Integer> kept = CityFlowHelper.selectFlowsByRate(0, java.util.List.of(
                candidate(1, 0.5f, 0.3f), candidate(2, 0.9f, 0.3f), candidate(3, 0.2f, 0.3f)));

        assertEquals(1, kept.size());
        assertTrue(kept.size() == 1 && kept.stream().allMatch(id -> id >= 1 && id <= 3));
    }

    @Test
    public void selectFlowsByRate_noCandidatesIsEmpty() {
        assertTrue(CityFlowHelper.selectFlowsByRate(100, java.util.List.of()).isEmpty());
    }

    @Test
    public void groupSizeRange_middleFlowGetsWindowAroundPassengersPerDay() {
        assertArrayEquals(new int[] {4, 6}, CityFlowHelper.groupSizeRange(5));
        assertArrayEquals(new int[] {2, 4}, CityFlowHelper.groupSizeRange(3.2));
    }
}