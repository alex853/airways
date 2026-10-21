package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The redistribution of the flows of a city (once per 24 hours) creates and updates the flows to the other cities.
 */
class CityFlowsProcessorTest {
    private World world;
    private Cities.City london;
    private Cities.City berlin;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final int countryId = world.countries().create("GB", "United Kingdom").getId();
        london = world.cities().create(countryId, "London", 51.5, -0.1, 9_000_000);
        berlin = world.cities().create(countryId, "Berlin", 52.5, 13.4, 4_000_000);
        world.cityFlows().createMissingCityFlows();
    }

    private City2CityFlows.Flow londonToBerlin() {
        return world.city2cityFlows().getFromCityIdToCityId(london.getId(), berlin.getId()).orElseThrow();
    }

    private void redistributeAll() {
        for (int i = 0; i < 2; i++) {
            CityFlowsProcessor.process(world);
        }
    }

    @Test
    public void test__first_redistribution_activates_the_flow_and_sets_its_heartbeat() {
        redistributeAll();

        final City2CityFlows.Flow flow = londonToBerlin();
        assertTrue(flow.isActive());
        assertEquals(world.getWorldTime(), flow.getAccumulatedFlowTime());
        assertTrue(flow.getHeartbeatTime() > world.getWorldTime());
    }

    @Test
    public void test__next_redistribution_does_not_move_the_heartbeat_of_an_active_flow() {
        redistributeAll();
        final City2CityFlows.Flow flow = londonToBerlin();
        // the flow has waited for a long time already: its heartbeat is in the future and is not "now + the wait"
        final int heartbeat = world.getWorldTime() + 5 * Time.ONE_HOUR;
        final int accumulatedFlowTime = world.getWorldTime() - 3 * Time.ONE_HOUR;
        flow.setHeartbeatTime(heartbeat);
        flow.setAccumulatedFlowTime(accumulatedFlowTime);
        flow.setFlowFraction(0.0001f);

        // a day passes, the redistribution comes again
        world.setWorldTime(world.getWorldTime() + CityFlowHelper.REDISTRIBUTION_PERIOD + Time.ONE_MINUTE);
        redistributeAll();

        assertTrue(flow.isActive());
        // the fraction is updated, the timing is not
        assertTrue(flow.getFlowFraction() > 0.5f, "fraction " + flow.getFlowFraction());
        assertEquals(heartbeat, flow.getHeartbeatTime());
        assertEquals(accumulatedFlowTime, flow.getAccumulatedFlowTime());
    }
}
