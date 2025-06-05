package net.simforge.airways2.app;

import net.simforge.airways2.world.Time;
import net.simforge.commons.misc.JavaTime;

import java.time.LocalDateTime;

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

    public static String hmOrNull(final int time) {
        return time != 0
                ? JavaTime.toHhmm(Time.toLdt(time).toLocalTime())
                : null;
    }
}
