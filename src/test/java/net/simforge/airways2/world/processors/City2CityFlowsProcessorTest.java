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
 * The flow of cities is served by the heartbeat: the earliest due flow first, and a flow which cannot be served does not block the others.
 */
class City2CityFlowsProcessorTest {
    private World world;
    private Cities.City london;
    private Cities.City berlin;
    private Cities.City paris;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final int countryId = world.countries().create("GB", "United Kingdom").getId();
        london = world.cities().create(countryId, "London", 51.5, -0.1, 9_000_000);
        berlin = world.cities().create(countryId, "Berlin", 52.5, 13.4, 4_000_000);
        paris = world.cities().create(countryId, "Paris", 48.9, 2.3, 2_000_000);
        world.cityFlows().createMissingCityFlows();
    }

    private City2CityFlows.Flow dueFlow(final Cities.City from, final Cities.City to, final int heartbeatTime) {
        final City2CityFlows.Flow flow = world.city2cityFlows().createInactive(from.getId(), to.getId());
        flow.setActive(true);
        flow.setFlowFraction(0.5f);
        flow.setNextGroupSize(2);
        flow.setAccumulatedFlowTime(heartbeatTime - Time.ONE_DAY);
        flow.setHeartbeatTime(heartbeatTime);
        return flow;
    }

    @Test
    public void test__the_flow_of_a_later_city_with_the_older_heartbeat_is_served_first() {
        final int now = world.getWorldTime();
        final City2CityFlows.Flow londonFlow = dueFlow(london, berlin, now - Time.ONE_HOUR);
        final City2CityFlows.Flow parisFlow = dueFlow(paris, berlin, now - 3 * Time.ONE_DAY);
        final int londonHeartbeat = londonFlow.getHeartbeatTime();

        City2CityFlowsProcessor.process(world);

        // the flow of Paris has the older heartbeat although it was created later: it moved, the flow of London did not
        assertTrue(parisFlow.getHeartbeatTime() != now - 3 * Time.ONE_DAY);
        assertEquals(londonHeartbeat, londonFlow.getHeartbeatTime());
    }

    @Test
    public void test__a_flow_of_a_city_without_its_own_flow_does_not_block_the_others() {
        final int now = world.getWorldTime();
        // a city which was added after the flows of the cities were created has no flow of its own
        final Cities.City rome = world.cities().create(london.getCountryId(), "Rome", 41.9, 12.5, 3_000_000);
        final City2CityFlows.Flow stuck = dueFlow(rome, berlin, now - 3 * Time.ONE_DAY);
        final City2CityFlows.Flow other = dueFlow(paris, berlin, now - Time.ONE_DAY);

        City2CityFlowsProcessor.process(world); // the stuck flow is the earliest, it cannot be served
        City2CityFlowsProcessor.process(world); // so the next one is served

        assertTrue(stuck.getHeartbeatTime() > now);
        assertTrue(other.getHeartbeatTime() != now - Time.ONE_DAY);
    }
}
