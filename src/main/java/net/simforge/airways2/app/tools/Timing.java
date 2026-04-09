package net.simforge.airways2.app.tools;

import net.simforge.commons.misc.Str;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Timing {
    private static final Logger log = LoggerFactory.getLogger(Timing.class);
    private static final ConcurrentHashMap<String, LabelData> data = new ConcurrentHashMap<>();
    private static final int PRINT_STATUS_EVERY_N_MEASURES = 100000;
    private static final AtomicInteger counterToStatusPrinting = new AtomicInteger(PRINT_STATUS_EVERY_N_MEASURES);

    public static Timer label(final String label) {
        printStatusIfTimeComes();

        final LabelData labelData = data.computeIfAbsent(label, (key) -> new LabelData(label));
        return new Timer(labelData);
    }

    private static void printStatusIfTimeComes() {
        if (counterToStatusPrinting.decrementAndGet() > 0) {
            return;
        }

        data.values().stream()
                .map(LabelData::logInfoMessage)
                .sorted()
                .forEach(log::info);
        counterToStatusPrinting.set(PRINT_STATUS_EVERY_N_MEASURES);
    }

    public static String printStatusToString() {
        return data.values().stream()
                .map(LabelData::logInfoMessage)
                .sorted()
                .reduce("", (a, b) -> a + "\n" + b);
    }

    public static class Timer implements AutoCloseable {
        private final LabelData labelData;
        private final long start = System.nanoTime();

        private Timer(LabelData labelData) {
            this.labelData = labelData;
        }

        @Override
        public void close() {
            final long finish = System.nanoTime();
            labelData.measure(finish - start);
        }
    }

    private static class LabelData {
        private static final DecimalFormat df3 = new DecimalFormat("#.000");

        private final String label;
        private long count;
        private long totalDuration;

        public LabelData(final String label) {
            this.label = label;
        }

        public synchronized void measure(long duration) {
            count++;
            totalDuration += duration;
        }

        public String logInfoMessage() {
            return String.format("Timing info : %s AVG: %s ms, CALLS: %s, TOTAL: %s s",
                    Str.al(label, 60),
                    Str.ar(df3.format(totalDuration / (float) count / 1_000_000.0d), 8),
                    Str.ar(String.valueOf(count), 7),
                    Str.ar(df3.format(totalDuration / 1_000_000_000.0d), 10));
        }
    }
}
