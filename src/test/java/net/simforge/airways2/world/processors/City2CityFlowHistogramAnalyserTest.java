package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The hourly line about the heartbeats of the active flows: the overdue ones with min, avg and max, then the buckets of the days, weeks and months.
 */
class City2CityFlowHistogramAnalyserTest {
    private World world;
    private int toCityId;
    private int nextFromCityId;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        toCityId = world.cities().create(world.countries().create("GB", "United Kingdom").getId(), "London", 51.5, -0.1, 9_000_000).getId();
        nextFromCityId = 1000;
    }

    private City2CityFlows.Flow flow(final boolean active, final int heartbeatTime) {
        final City2CityFlows.Flow flow = world.city2cityFlows().createInactive(nextFromCityId++, toCityId);
        flow.setActive(active);
        flow.setHeartbeatTime(heartbeatTime);
        return flow;
    }

    private static String minutes(final int time) {
        return Time.toLdt(time).truncatedTo(ChronoUnit.MINUTES).toString();
    }

    @Test
    public void test__no_flows() {
        assertEquals("c2c h/b overdue 0 | D0 0 D1 0 D2 0 D3 0 D4 0 D5 0 D6 0 W2 0 W3 0 W4 0 M2 0 M3 0 M4 0 M5 0 M6 0 L 0",
                City2CityFlowHistogramAnalyser.histogramLine(world));
    }

    @Test
    public void test__overdue_flows_have_min_avg_and_max_and_the_rest_are_in_the_buckets() {
        final int now = world.getWorldTime();
        final int day = Time.ONE_DAY;
        flow(true, now - 3 * Time.ONE_HOUR);
        flow(true, now - Time.ONE_HOUR);
        flow(true, now - 2 * Time.ONE_HOUR);
        // not counted: inactive, no heartbeat
        flow(false, now - day);
        flow(true, 0);
        flow(true, now + Time.ONE_HOUR);                  // D0
        flow(true, now + 30 * Time.ONE_HOUR);             // D1
        flow(true, now + 6 * day + Time.ONE_HOUR);        // D6
        flow(true, now + 7 * day);                        // W2
        flow(true, now + 14 * day);                       // W3
        flow(true, now + 14 * day + 1);                   // W3
        flow(true, now + 29 * day);                       // W4
        flow(true, now + 30 * day);                       // M2
        flow(true, now + 59 * day);                       // M2
        flow(true, now + 60 * day);                       // M3
        flow(true, now + 179 * day);                      // M6
        flow(true, now + 180 * day);                      // L
        flow(true, now + 400 * day);                      // L

        assertEquals("c2c h/b overdue 3 min " + minutes(now - 3 * Time.ONE_HOUR) + " avg " + minutes(now - 2 * Time.ONE_HOUR) + " max " + minutes(now - Time.ONE_HOUR)
                        + " | D0 1 D1 1 D2 0 D3 0 D4 0 D5 0 D6 1 W2 1 W3 2 W4 1 M2 2 M3 1 M4 0 M5 0 M6 1 L 2",
                City2CityFlowHistogramAnalyser.histogramLine(world));
    }
}
