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
 * then the buckets of the days and weeks (up to the week 12).
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
        assertEquals("c2c h/b overdue 0 | D0-6 0,0,0,0,0,0,0 | W2-12 0,0,0,0,0,0,0,0,0,0,0 | L 0",
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
        flow(true, now + 29 * day);                       // W5
        flow(true, now + 34 * day);                       // W5
        flow(true, now + 56 * day);                       // W9
        flow(true, now + 62 * day);                       // W9
        flow(true, now + 83 * day);                       // W12
        flow(true, now + 84 * day);                       // L
        flow(true, now + 91 * day);                       // L
        flow(true, now + 180 * day);                      // L
        flow(true, now + 400 * day);                      // L

        assertEquals("c2c h/b overdue 3 min " + minutes(now - 3 * Time.ONE_HOUR) + " avg " + minutes(now - 2 * Time.ONE_HOUR)
                        + " | O-0 3 | D0-6 1,1,0,0,0,0,1 | W2-12 1,2,0,2,0,0,0,2,0,0,1 | L 4",
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
                        + " | O-10 1 | O-2 1 | D0-6 0,0,0,0,0,0,0 | W2-12 0,0,0,0,0,0,0,0,0,0,0 | L 0",
                City2CityFlowHistogramAnalyser.clockLine(world));
        assertEquals("c2c h/b overdue 0 | D0-6 3,0,0,0,0,0,0 | W2-12 0,0,0,0,0,0,0,0,0,0,0 | L 0",
                City2CityFlowHistogramAnalyser.histogramLine(world));
    }

    private City2CityFlows.Flow flowWithClock(final int clock, final boolean active) {
        final City2CityFlows.Flow flow = flow(active, world.getWorldTime() + Time.ONE_HOUR);
        flow.setAccumulatedFlowTime(clock);
        return flow;
    }

    @Test
    public void test__oldest_by_clock_lists_the_active_flows_with_the_oldest_clock_first() {
        final int now = world.getWorldTime();
        final City2CityFlows.Flow oldest = flowWithClock(now - 400 * Time.ONE_DAY, true);
        final City2CityFlows.Flow middle = flowWithClock(now - 10 * Time.ONE_DAY, true);
        flowWithClock(now - 3 * Time.ONE_DAY, true);
        flowWithClock(now - 900 * Time.ONE_DAY, false); // inactive: not shown
        flowWithClock(0, true);                         // no clock yet: not shown

        final java.util.List<String> lines = City2CityFlowHistogramAnalyser.oldestByClock(world, 2);

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("#" + oldest.getFromCityId() + "/" + oldest.getToCityId()), lines.get(0));
        assertTrue(lines.get(0).contains("400 d behind"), lines.get(0));
        assertTrue(lines.get(1).startsWith("#" + middle.getFromCityId() + "/"), lines.get(1));
        // the flow has accumulated everything it needs (no group size yet): the time to accumulate is zero and the clock plus it is already due
        assertTrue(lines.get(0).contains("time to accumulate 0 s"), lines.get(0));
        assertTrue(lines.get(0).contains("already due"), lines.get(0));
    }

    @Test
    public void test__oldest_by_clock_columns_are_aligned_and_have_the_passengers_to_catch_up() {
        final int now = world.getWorldTime();
        flowWithClock(now - 400 * Time.ONE_DAY, true);
        flowWithClock(now - 10 * Time.ONE_DAY, true);

        final java.util.List<String> lines = City2CityFlowHistogramAnalyser.oldestByClock(world, 2);

        assertEquals(2, lines.size());
        for (int i = 0; i < 2; i++) {
            assertTrue(lines.get(i).contains(" | need pax "), lines.get(i));
        }
        // every separator is at the same position in all the lines up to the last column
        int from0 = 0;
        int from1 = 0;
        for (int column = 0; column < 4; column++) {
            final int p0 = lines.get(0).indexOf(" | ", from0);
            final int p1 = lines.get(1).indexOf(" | ", from1);
            assertEquals(p0, p1, lines.get(0) + "\n" + lines.get(1));
            from0 = p0 + 1;
            from1 = p1 + 1;
        }
    }

    @Test
    public void test__oldest_by_clock_with_a_zero_limit_is_empty() {
        flowWithClock(world.getWorldTime() - Time.ONE_DAY, true);

        assertTrue(City2CityFlowHistogramAnalyser.oldestByClock(world, 0).isEmpty());
    }
}
