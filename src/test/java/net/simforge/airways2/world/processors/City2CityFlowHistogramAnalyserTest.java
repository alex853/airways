package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hourly lines about the active flows, by the heartbeat and by the accumulated flow time: the overdue ones with min and avg and by the days of the delay,
 * then the buckets of the days, weeks and months.
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
        assertEquals("c2c h/b overdue 0 | D0-6 0,0,0,0,0,0,0 | W2-4 0,0,0 | M2-6 0,0,0,0,0 | L 0",
                City2CityFlowHistogramAnalyser.histogramLine(world));
    }

    @Test
    public void test__overdue_flows_have_min_and_avg_and_the_rest_are_in_the_buckets() {
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

        assertEquals("c2c h/b overdue 3 min " + minutes(now - 3 * Time.ONE_HOUR) + " avg " + minutes(now - 2 * Time.ONE_HOUR)
                        + " | O-0 3 | D0-6 1,1,0,0,0,0,1 | W2-4 1,2,1 | M2-6 2,1,0,0,1 | L 2",
                City2CityFlowHistogramAnalyser.histogramLine(world));
    }

    @Test
    public void test__overdue_flows_are_counted_by_the_whole_days_of_the_delay_and_only_the_days_with_flows_are_shown() {
        final int now = world.getWorldTime();
        final int day = Time.ONE_DAY;
        flow(true, now - 135 * day - Time.ONE_HOUR);       // O135
        flow(true, now - 5 * day);                          // O5
        flow(true, now - 5 * day - 20 * Time.ONE_HOUR);    // O5
        flow(true, now - 4 * day - Time.ONE_HOUR);          // O4
        flow(true, now - Time.ONE_HOUR);                    // O0

        final String line = City2CityFlowHistogramAnalyser.histogramLine(world);

        // the most late day first, no O1, O2, O3
        assertTrue(line.contains(" | O-135 1 | O-5 2 | O-4 1 | O-0 1 | D0-6 "), line);
    }

    @Test
    public void test__the_clock_line_is_the_same_by_the_accumulated_flow_time() {
        final int now = world.getWorldTime();
        final int day = Time.ONE_DAY;
        // the heartbeat is in the future, the clocks are late
        final City2CityFlows.Flow a = flow(true, now + Time.ONE_HOUR);
        a.setAccumulatedFlowTime(now - 10 * day);
        final City2CityFlows.Flow b = flow(true, now + Time.ONE_HOUR);
        b.setAccumulatedFlowTime(now - 2 * day);
        final City2CityFlows.Flow noClock = flow(true, now + Time.ONE_HOUR); // the clock is not started: not counted

        assertEquals("c2c a/t overdue 2 min " + minutes(now - 10 * day) + " avg " + minutes(now - 6 * day)
                        + " | O-10 1 | O-2 1 | D0-6 0,0,0,0,0,0,0 | W2-4 0,0,0 | M2-6 0,0,0,0,0 | L 0",
                City2CityFlowHistogramAnalyser.clockLine(world));
        assertEquals("c2c h/b overdue 0 | D0-6 3,0,0,0,0,0,0 | W2-4 0,0,0 | M2-6 0,0,0,0,0 | L 0",
                City2CityFlowHistogramAnalyser.histogramLine(world));
    }
}
