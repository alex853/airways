package net.simforge.airways2.world;

import com.google.common.base.Preconditions;
import net.simforge.commons.misc.JavaTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

public class Time {
    private static final Logger log = LoggerFactory.getLogger(Time.class);
    public static final int START_TIME_EPOCH_SECONDS = 1735689600;
    public static final int ONE_SECOND = 1;
    public static final int ONE_MINUTE = 60;
    public static final int HALF_AN_HOUR = 30 * ONE_MINUTE;
    public static final int ONE_HOUR = 60 * ONE_MINUTE;
    public static final int ONE_DAY = 24 * ONE_HOUR;

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

    public static LocalDate toLdOrNull(final int time) {
        return time != 0 ? toLdt(time).toLocalDate() : null;
    }

    public static LocalTime toLtOrNull(final int time) {
        return time != 0 ? toLdt(time).toLocalTime() : null;
    }

    public static int fromLdt(final LocalDateTime time) {
        return (int) time.toEpochSecond(ZoneOffset.UTC);
    }

    public static int fromLdtOrNull(final LocalDateTime time) {
        return time != null
                ? (int) time.toEpochSecond(ZoneOffset.UTC)
                : 0;
    }

    public static int fromLdLt(final LocalDate date, final LocalTime time) {
        if (date == null || time == null) {
            return 0;
        }
        return fromLdt(date.atTime(time));
    }
}
