package net.simforge.airways2.world.processors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CityFlowHelperTest {
    @Test
    public void calcFlowUnits_dist_500nm_attr_1() {
        assertEquals(1.0, CityFlowHelper.calcFlowUnits(500, 1));
    }

    @Test
    public void calcFlowUnits_dist_1000nm_attr_1() {
        assertEquals(0.5, CityFlowHelper.calcFlowUnits(1000, 1));
    }

    @Test
    public void calcFlowUnits_dist_500nm_attr_2() {
        assertEquals(2.0, CityFlowHelper.calcFlowUnits(500, 2));
    }

    @Test
    public void calcFlowUnits_dist_1000nm_attr_2() {
        assertEquals(1, CityFlowHelper.calcFlowUnits(1000, 2));
    }

    @Test
    public void calcFlowUnits_dist_250nm_attr_1() {
        assertEquals(1.0, CityFlowHelper.calcFlowUnits(250, 1));
    }

    @Test
    public void calcFlowUnits_dist_100nm_attr_1() {
        assertEquals(0.25, CityFlowHelper.calcFlowUnits(100, 1));
    }

    @Test
    public void calcFlowUnits_dist_50nm_attr_1() {
        assertEquals(0.0, CityFlowHelper.calcFlowUnits(50, 1));
    }

    @Test
    public void calcFlowUnits_dist_49nm_attr_1() {
        assertEquals(0.0, CityFlowHelper.calcFlowUnits(49, 1));
    }

    @Test
    public void calcFlowUnits_dist_100nm_attr_2() {
        assertEquals(0.5, CityFlowHelper.calcFlowUnits(100, 2));
    }
}