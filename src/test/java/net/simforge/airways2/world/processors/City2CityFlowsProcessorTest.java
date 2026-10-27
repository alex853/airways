package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
    public void test__ten_flows_are_served_in_a_step_the_earliest_heartbeats_first() {
        final int now = world.getWorldTime();
        final int countryId = london.getCountryId();
        final List<Cities.City> all = new ArrayList<>(List.of(london, berlin, paris));
        for (int i = 0; i < 3; i++) {
            all.add(world.cities().create(countryId, "City" + i, 40 + i, 10 + i, 1_000_000));
        }
        world.cityFlows().createMissingCityFlows();

        // 15 due flows; the bigger the id, the older the heartbeat
        final List<City2CityFlows.Flow> flows = new ArrayList<>();
        final List<Integer> heartbeats = new ArrayList<>();
        outer:
        for (final Cities.City from : all) {
            for (final Cities.City to : all) {
                if (from == to) {
                    continue;
                }
                final int heartbeat = now - (flows.size() + 1) * Time.ONE_HOUR;
                flows.add(dueFlow(from, to, heartbeat));
                heartbeats.add(heartbeat);
                if (flows.size() == 15) {
                    break outer;
                }
            }
        }

        City2CityFlowsProcessor.process(world);

        // the oldest flow was served; the 5 newest were not reached: a step has 10 passes, and a flow which is still behind after a pass is taken again,
        // so the ten passes go to the oldest flows only
        assertTrue(flows.get(14).getHeartbeatTime() != heartbeats.get(14));
        for (int i = 0; i < 5; i++) {
            assertEquals(heartbeats.get(i), flows.get(i).getHeartbeatTime(), "flow #" + i);
        }
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
