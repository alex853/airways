package net.simforge.airways2.world;

import com.google.common.base.Preconditions;
import net.simforge.commons.misc.JavaTime;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

// todo ak1 tests for this class
public class Time {
    public static final int ONE_MINUTE = 60;
    public static final int HALF_AN_HOUR = 1800;
    public static final int ONE_HOUR = 3600;

    public static final int TICK = 10;

    public static int now() {
        return (int) (JavaTime.nowUtc().toEpochSecond(ZoneOffset.UTC));
    }

    public static LocalDateTime toLdt(final int time) {
        Preconditions.checkArgument(time != 0, "time can not be zero");
        return LocalDateTime.ofEpochSecond(time, 0, ZoneOffset.UTC);
    }

    public static LocalDateTime toLdtOrNull(final int time) {
        return time != 0 ? toLdt(time) : null;
    }

    public static int fromLdt(final LocalDateTime time) {
        return (int) time.toEpochSecond(ZoneOffset.UTC);
    }
}
