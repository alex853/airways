package net.simforge.airways2.tools;

import net.simforge.airways2.world.Time;
import net.simforge.commons.misc.JavaTime;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TimeTools {
    public static String ts(final int time) {
        return (time != 0)
                ? Time.toLdt(time).toString()
                : null;
    }

    public static String ts(final LocalDateTime ldt) {
        return (ldt != null)
                ? ldt.toString()
                : null;
    }

    public static String ymdOrNull(final int time) {
        return time != 0
                ? Time.toLdt(time).toLocalDate().toString()
                : null;
    }

    public static String hhmmOrNull(final int time) {
        return time != 0
                ? JavaTime.toHhmm(Time.toLdt(time).toLocalTime())
                : null;
    }

    public static String hhmmOrNull(final LocalTime time) {
        return time != null
                ? JavaTime.toHhmm(time)
                : null;
    }

    public static String hhmmPlusDaysOrNull(final int time) {
        if (time == 0) {
            return null;
        }
        
        return hhmmPlusDaysOrNull(Time.toLdt(time));
    }

    public static String hhmmPlusDaysOrNull(final LocalDateTime time) {
        if (time == null) {
            return null;
        }

        final LocalDateTime now = JavaTime.nowUtc();

        final String hhmm = JavaTime.toHhmm(time.toLocalTime());

        final long daysDiff = Duration.between(now.toLocalDate().atStartOfDay(), time.toLocalDate().atStartOfDay()).toDays();

        if (daysDiff == 0) {
            return hhmm;
        } else {
            return hhmm + (daysDiff > 0 ? "+" : "") + daysDiff;
        }
    }
}
