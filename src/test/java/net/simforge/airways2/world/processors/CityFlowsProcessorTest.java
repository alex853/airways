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

    private void redistributeAllCities() {
        final long cities = world.cities().all().count();
        for (int i = 0; i < cities; i++) {
            CityFlowsProcessor.process(world);
        }
    }

    private City2CityFlows.Flow flowOf(final Cities.City from, final Cities.City to) {
        return world.city2cityFlows().getFromCityIdToCityId(from.getId(), to.getId()).orElseThrow();
    }

    @Test
    public void test__the_attraction_of_the_destination_raises_its_share_of_the_flow() {
        final int countryId = london.getCountryId();
        // the same population and the same distance from the origin, the attraction is different
        final Cities.City origin = world.cities().create(countryId, "Origin", 51.5, 0.0, 4_000_000);
        final Cities.City attractive = world.cities().create(countryId, "Attractive", 52.5, 13.5, 4_000_000);
        final Cities.City plain = world.cities().create(countryId, "Plain", 52.5, -13.5, 4_000_000);
        world.cityFlows().createMissingCityFlows();
        world.cityFlows().byCityId(attractive.getId()).orElseThrow().setAttractionFactor(4);

        redistributeAllCities();

        final float ratio = flowOf(origin, attractive).getFlowFraction() / flowOf(origin, plain).getFlowFraction();
        assertEquals(4.0f, ratio, 0.05f, "the share of the attractive city is 4 times bigger");
    }

    @Test
    public void test__the_attraction_of_the_origin_does_not_open_the_flows_to_the_far_big_cities() {
        final int countryId = london.getCountryId();
        // a small tourist island: its own attraction 7 is not a reason for flows to the other side of the world
        final Cities.City island = world.cities().create(countryId, "Island", 37.79, 20.9, 41_000);
        final Cities.City faraway = world.cities().create(countryId, "Faraway", 1.35, 103.8, 6_000_000);
        world.cityFlows().createMissingCityFlows();
        world.cityFlows().byCityId(island.getId()).orElseThrow().setAttractionFactor(7);

        redistributeAllCities();

        final java.util.Optional<City2CityFlows.Flow> flow = world.city2cityFlows().getFromCityIdToCityId(island.getId(), faraway.getId());
        assertTrue(flow.isEmpty() || !flow.get().isActive(), "the island does not have a flow to the far big city");
    }

    @Test
    public void test__a_city_without_passengers_keeps_only_its_strongest_flow() {
        final int countryId = london.getCountryId();
        // a city whose people do not travel (mobility 0, the daily flow is zero): both London and Berlin pass the flow units, but every flow is thinner than
        // the minimum, so the strongest one stays
        final Cities.City tiny = world.cities().create(countryId, "Tiny", 51.0, 5.0, 20_000);
        world.cityFlows().createMissingCityFlows();
        world.cityFlows().byCityId(tiny.getId()).orElseThrow().setMobilityFactor(0);

        redistributeAllCities();

        final long active = world.city2cityFlows().allFromCityId(tiny.getId()).filter(City2CityFlows.Flow::isActive).count();
        assertEquals(1, active, "only the strongest flow is kept");
        // London is the bigger one of the two destinations and it is the nearest enough: the strongest by the flow units
        assertTrue(flowOf(tiny, london).isActive());
    }

    @Test
    public void test__a_very_attractive_destination_is_reachable_even_from_a_small_city() {
        final int countryId = london.getCountryId();
        final Cities.City small = world.cities().create(countryId, "Small", 37.79, 20.9, 41_000);
        final Cities.City hub = world.cities().create(countryId, "Hub", 1.35, 103.8, 6_000_000);
        world.cityFlows().createMissingCityFlows();
        world.cityFlows().byCityId(hub.getId()).orElseThrow().setAttractionFactor(10);

        redistributeAllCities();

        assertTrue(flowOf(small, hub).isActive(), "attraction 10 of the destination brings the units over the threshold");
    }
}
