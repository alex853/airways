package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;

/**
 * Once per hour writes one line about the heartbeats of the active flows of cities: how many are overdue (the heartbeat is before the world time) with the
 * oldest, the average and the newest heartbeat of them, and how many are due later: D0..D6 are the days (D0 is the next 24 hours), W2..W4 the second, third
 * and fourth week up to the day 30, M2..M6 the months of 30 days, L is everything after six months.
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
    }

    static String histogramLine(final World world) {
        final int worldTime = world.getWorldTime();

        final int[] counts = new int[BUCKETS];
        final int[] overdue = new int[1];
        final int[] oldest = {Integer.MAX_VALUE};
        final int[] newest = {Integer.MIN_VALUE};
        final long[] sum = new long[1];

        world.city2cityFlows().all()
                .filter(City2CityFlows.Flow::isActive)
                .forEach(c2c -> {
                    final int heartbeatTime = c2c.getHeartbeatTime();
                    if (heartbeatTime == 0) {
                        return;
                    }

                    if (heartbeatTime < worldTime) {
                        overdue[0]++;
                        sum[0] += heartbeatTime;
                        oldest[0] = Math.min(oldest[0], heartbeatTime);
                        newest[0] = Math.max(newest[0], heartbeatTime);
                        return;
                    }

                    counts[bucket((heartbeatTime - worldTime) / Time.ONE_DAY)]++;
                });

        final StringBuilder line = new StringBuilder("c2c h/b overdue ").append(overdue[0]);
        if (overdue[0] > 0) {
            line.append(" min ").append(minutes(oldest[0]))
                    .append(" avg ").append(minutes((int) (sum[0] / overdue[0])))
                    .append(" max ").append(minutes(newest[0]));
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
