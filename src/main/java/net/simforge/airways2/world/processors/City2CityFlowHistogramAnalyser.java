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
 * then how many are due later: D0..D6 are the days (D0 is the next 24 hours), W2..W12 the weeks (W2 is the days 7..13, W12 the days 77..83), L is everything
 * after that.
 */
public class City2CityFlowHistogramAnalyser {
    private static final Logger log = LoggerFactory.getLogger(City2CityFlowHistogramAnalyser.class);

    private static final int DAYS = 7;
    // the last week shown (the days 77..83), everything after it is L
    private static final int LAST_WEEK = 12;
    // D0..D6, W2..W12, L
    private static final int BUCKETS = DAYS + (LAST_WEEK - 1) + 1;

    private static long lastProcessTime = 0;

    public static void process(final World world) {
        if (lastProcessTime + 3600000 > System.currentTimeMillis()) {
            return;
        }

        lastProcessTime = System.currentTimeMillis();
        log.warn(histogramLine(world));
        log.warn(clockLine(world));
    }

    /**
     * The active flows with the oldest clock (a/t), the oldest first, one line each: what is the flow and why its clock does not move. The clock moves only when a group of
     * the flow is born: a flow waits until the clock plus the time to accumulate the next group comes, so the flow with a very old clock is a flow with a very long time to
     * accumulate (a small flow, a low success rate), or something is wrong with it. The time to accumulate and the sum of the clock and it are given in full (days, the sum
     * as a long number of seconds is not wrapped as an int is, the processor adds them as ints, see City2CityFlowsProcessor) so an overflow can be seen.
     */
    public static java.util.List<String> oldestByClock(final World world, final int limit) {
        final int worldTime = world.getWorldTime();
        final java.util.List<String[]> rows = world.city2cityFlows().all()
                .filter(City2CityFlows.Flow::isActive)
                .filter(c2c -> c2c.getAccumulatedFlowTime() != 0)
                .sorted(java.util.Comparator.comparingInt(City2CityFlows.Flow::getAccumulatedFlowTime))
                .limit(Math.max(0, limit))
                .map(c2c -> describeFlow(world, c2c, worldTime))
                .toList();
        return alignColumns(rows);
    }

    /** The cells of the rows are padded to the width of the widest cell of the column, so the separators go one under another; the last column is not padded. */
    private static java.util.List<String> alignColumns(final java.util.List<String[]> rows) {
        final int columns = rows.stream().mapToInt(row -> row.length).max().orElse(0);
        final int[] widths = new int[columns];
        for (final String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        return rows.stream().map(row -> {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    line.append(" | ");
                }
                line.append(row[i]);
                if (i < row.length - 1) {
                    line.append(" ".repeat(widths[i] - row[i].length()));
                }
            }
            return line.toString();
        }).toList();
    }

    private static String shortName(final String name) {
        return name.length() > 5 ? name.substring(0, 5) : name;
    }

    /**
     * How many passengers (the sum of the group sizes) the flow has to generate for its clock to reach the world time with the current parameters:
     * the delay in days multiplied by the passengers per day of the flow (see CityFlowHelper.getDailyPassengers). Zero for a flow which is not behind.
     */
    static long passengersToCatchUp(final World world, final City2CityFlows.Flow c2c, final int worldTime) {
        final long lag = (long) worldTime - c2c.getAccumulatedFlowTime();
        if (lag <= 0) {
            return 0;
        }
        return Math.round(lag / (double) Time.ONE_DAY * CityFlowHelper.getDailyPassengers(world, c2c));
    }

    private static String[] describeFlow(final World world, final City2CityFlows.Flow c2c, final int worldTime) {
        final String from = shortName(world.cities().byId(c2c.getFromCityId()).map(city -> city.getName()).orElse("?"));
        final String to = shortName(world.cities().byId(c2c.getToCityId()).map(city -> city.getName()).orElse("?"));
        String catchUp;
        try {
            catchUp = "need pax " + passengersToCatchUp(world, c2c, worldTime);
        } catch (final RuntimeException e) {
            catchUp = "need pax unknown (" + e.getClass().getSimpleName() + ")";
        }
        String toAccumulate;
        String sum;
        try {
            final int seconds = net.simforge.airways2.world.processors.CityFlowHelper.calcTimeToAccumulateFlow(world, c2c);
            final long sumSeconds = (long) c2c.getAccumulatedFlowTime() + seconds;
            toAccumulate = seconds + " s (" + seconds / Time.ONE_DAY + " d)";
            sum = Time.toLdtOrNull((int) sumSeconds) + (sumSeconds > Integer.MAX_VALUE ? " OVERFLOW of int, the sum is " + sumSeconds : "") + (sumSeconds > worldTime ? ", in the future" : ", already due");
        } catch (final RuntimeException e) {
            toAccumulate = "unknown (" + e.getClass().getSimpleName() + ")";
            sum = "unknown";
        }
        return new String[] {
                "#" + c2c.getFromCityId() + "/" + c2c.getToCityId() + " " + from + " - " + to,
                "a/t " + Time.toLdtOrNull(c2c.getAccumulatedFlowTime()) + " (" + (worldTime - c2c.getAccumulatedFlowTime()) / Time.ONE_DAY + " d behind)",
                catchUp,
                "h/b " + Time.toLdtOrNull(c2c.getHeartbeatTime()),
                "time to accumulate " + toAccumulate,
                "a/t + it = " + sum,
                "next g/s " + c2c.getNextGroupSize() + ", accumulated flow " + c2c.getAccumulatedFlow(),
                "s/r " + c2c.getSuccessRate() + ", flow fraction " + c2c.getFlowFraction()
        };
    }

    // by the heartbeat of the flows
    public static String histogramLine(final World world) {
        return line(world, "c2c h/b", City2CityFlows.Flow::getHeartbeatTime);
    }

    // by the accumulated flow time (the clock) of the flows
    public static String clockLine(final World world) {
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
                .append(" | W2-").append(LAST_WEEK).append(' ').append(join(counts, DAYS, BUCKETS - 1))
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

    // the bucket of the days from now: 0..6 are the days, then the weeks (W2 is the days 7..13) up to the week 12, the last one is everything later
    private static int bucket(final int day) {
        if (day < DAYS) {
            return day;
        }
        final int week = day / DAYS + 1;
        return week <= LAST_WEEK ? DAYS + (week - 2) : BUCKETS - 1;
    }

    private static String minutes(final int time) {
        return Time.toLdt(time).truncatedTo(ChronoUnit.MINUTES).toString();
    }
}
