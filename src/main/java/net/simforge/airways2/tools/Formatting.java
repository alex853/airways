package net.simforge.airways2.tools;

import java.text.DecimalFormat;

public class Formatting {
    public static final DecimalFormat df3 = new DecimalFormat("0.000");
    public static final DecimalFormat df5 = new DecimalFormat("0.00000");
    public static final DecimalFormat df6 = new DecimalFormat("0.000000");
    public static final DecimalFormat df7 = new DecimalFormat("0.0000000");

    private static final int SHORT_CITY_NAME_LENGTH = 5;

    /**
     * The city name for the log lines of the flows: the names longer than 5 characters are cut, "San Diego" is "San D".
     */
    public static String shortCity(final String cityName) {
        return cityName.length() > SHORT_CITY_NAME_LENGTH ? cityName.substring(0, SHORT_CITY_NAME_LENGTH) : cityName;
    }
}
