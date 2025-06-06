package net.simforge.airways2.app;

import net.simforge.airways2.world.Time;
import net.simforge.commons.misc.JavaTime;

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
}
