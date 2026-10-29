package net.simforge.airways2.app.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The memory of the JVM in the log, not more often than once in 10 minutes: Used, Free, Total (the heap taken from the system) and Max (the limit of the
 * heap). A heap which stays near its Max after the collections makes the garbage collector work all the time.
 */
public class MemoryReport {
    private static final Logger log = LoggerFactory.getLogger(MemoryReport.class);

    private static final long PERIOD_MILLIS = 10 * 60 * 1000;

    private static long lastMemoryReportTs;

    public static void print() {
        if (lastMemoryReportTs + PERIOD_MILLIS < System.currentTimeMillis()) {
            log.info("Memory report, MB: Used = {}, Free = {}, Total = {}, Max = {}", getUsedMB(), getFreeMB(), getTotalMB(), getMaxMB());

            lastMemoryReportTs = System.currentTimeMillis();
        }
    }

    private static int toMB(final long size) {
        return (int) (size / 0x100000L);
    }

    public static int getUsedMB() {
        return toMB(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    }

    public static int getFreeMB() {
        return toMB(Runtime.getRuntime().freeMemory());
    }

    public static int getTotalMB() {
        return toMB(Runtime.getRuntime().totalMemory());
    }

    public static int getMaxMB() {
        return toMB(Runtime.getRuntime().maxMemory());
    }
}
