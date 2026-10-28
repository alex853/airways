package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

/**
 * Once per hour writes two lines about the active flows of cities, the same lines for two times of a flow: the heartbeat (h/b) and the clock of the flow, the
 * accumulated flow time (a/t). Every line: how many flows are overdue (the time is before the world time) with the oldest and the average time of them, then
 * the overdue ones by the whole days of the delay (O-135 1 is one flow which is 135 days late, only the days which have flows are shown, the most late first),
 * then how many are due later: D0..D6 are the days (D0 is the next 24 hours), W2..W4 the second, third and fourth week up to the day 30, M2..M6 the months
 * of 30 days, L is everything after six months.
 */
public class City2CityFlowHistogramAnalyser {
    private static final Logger log = LoggerFactory.getLogger(City2CityFlowHistogramAnalyser.class);

    private static final int DAYS = 7;
    private static final int MONTH_DAYS = 30;
    private static final int MONTHS = 6;
    // D0..D6, W2, W3, W4, M2..M6, L
    private static final int BUCKETS = DAYS + 3 + (MONTHS - 1) + 1;

    private static long lastProcessTime = 0;

    public static void process(final World world) {
        if (lastProcessTime + 3600000 > System.currentTimeMillis()) {
            return;
        }

        lastProcessTime = System.currentTimeMillis();
        log.warn(histogramLine(world));
        log.warn(clockLine(world));
    }

    // by the heartbeat of the flows
    static String histogramLine(final World world) {
        return line(world, "c2c h/b", City2CityFlows.Flow::getHeartbeatTime);
    }

    // by the accumulated flow time (the clock) of the flows
    static String clockLine(final World world) {
        return line(world, "c2c a/t", City2CityFlows.Flow::getAccumulatedFlowTime);
    }

    private static String line(final World world, final String tag, final ToIntFunction<City2CityFlows.Flow> timeOf) {
        final int worldTime = world.getWorldTime();

        final int[] counts = new int[BUCKETS];
        final int[] overdue = new int[1];
        final int[] oldest = {Integer.MAX_VALUE};
        final long[] sum = new long[1];
        // the whole days of the delay -> the flows, the most late day first
        final TreeMap<Integer, Integer> late = new TreeMap<>((a, b) -> Integer.compare(b, a));

        world.city2cityFlows().all()
                .filter(City2CityFlows.Flow::isActive)
                .forEach(c2c -> {
                    final int time = timeOf.applyAsInt(c2c);
                    if (time == 0) {
                        return;
                    }

                    if (time < worldTime) {
                        overdue[0]++;
                        sum[0] += time;
                        oldest[0] = Math.min(oldest[0], time);
                        late.merge((worldTime - time) / Time.ONE_DAY, 1, Integer::sum);
                        return;
                    }

                    counts[bucket((time - worldTime) / Time.ONE_DAY)]++;
                });

        final StringBuilder line = new StringBuilder(tag).append(" overdue ").append(overdue[0]);
        if (overdue[0] > 0) {
            line.append(" min ").append(minutes(oldest[0]))
                    .append(" avg ").append(minutes((int) (sum[0] / overdue[0])));
            // every day of the delay is a part of the line of its own: " | O-135 1 | O-5 30000"
            for (final Map.Entry<Integer, Integer> entry : late.entrySet()) {
                line.append(" | O-").append(entry.getKey()).append(' ').append(entry.getValue());
            }
        }
        line.append(" | D0-6 ").append(join(counts, 0, DAYS))
                .append(" | W2-4 ").append(join(counts, DAYS, DAYS + 3))
                .append(" | M2-6 ").append(join(counts, DAYS + 3, BUCKETS - 1))
                .append(" | L ").append(counts[BUCKETS - 1]);
        return line.toString();
    }

    private static String join(final int[] counts, final int from, final int to) {
        final StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) {
                sb.append(',');
            }
            sb.append(counts[i]);
        }
        return sb.toString();
    }

    // the bucket of the days from now: 0..6 are the days, then the weeks up to the day 30, then the months of 30 days, the last one is everything later
    private static int bucket(final int day) {
        if (day < DAYS) {
            return day;
        }
        if (day < 2 * DAYS) {
            return DAYS; // W2
        }
        if (day < 3 * DAYS) {
            return DAYS + 1; // W3
        }
        if (day < MONTH_DAYS) {
            return DAYS + 2; // W4
        }
        final int month = day / MONTH_DAYS + 1; // the days 30..59 are the month 2
        return month <= MONTHS ? DAYS + 3 + (month - 2) : BUCKETS - 1;
    }

    private static String minutes(final int time) {
        return Time.toLdt(time).truncatedTo(ChronoUnit.MINUTES).toString();
    }
}
