package net.simforge.airways2.app;

import net.simforge.airways2.world.Time;

public class WebTime {
    public static String full(final int time) {
        return (time != 0)
                ? Time.toLdt(time).toString()
                : null;
    }
}
