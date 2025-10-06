package net.simforge.airways2.world.processors;

import org.junit.jupiter.api.Test;

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
}