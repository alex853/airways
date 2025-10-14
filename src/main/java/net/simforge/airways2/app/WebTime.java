package net.simforge.airways2.app;

import net.simforge.airways2.world.Time;
import net.simforge.commons.misc.JavaTime;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class WebTime {
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
        
        final LocalDateTime ldt = Time.toLdt(time);
        final LocalDateTime now = JavaTime.nowUtc();

        final String hhmm = JavaTime.toHhmm(ldt.toLocalTime());

        final long daysDiff = Duration.between(now.toLocalDate().atStartOfDay(), ldt.toLocalDate().atStartOfDay()).toDays();

        if (daysDiff == 0) {
            return hhmm;
        } else {
            return hhmm + (daysDiff > 0 ? "+" : "") + daysDiff;
        }
    }
}
