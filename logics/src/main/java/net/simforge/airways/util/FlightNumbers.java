package net.simforge.airways.util;

import net.simforge.airways.model.Airline;
import net.simforge.commons.misc.Str;

public class FlightNumbers {
    public static String makeFlightNumber(String iataCode, int flightNumber) {
        return iataCode + (flightNumber < 1000 ? Str.z(flightNumber, 3) : flightNumber);
    }

    public static String increaseFlightNumber(String flightNumber) {
        String iataCode = flightNumber.substring(0, 2);
        String digits = flightNumber.substring(2);
        return makeFlightNumber(iataCode, Integer.parseInt(digits) + 1);
    }

    public static String makeCallsign(Airline airline, String flightNumber) {
        String digits = flightNumber.substring(2);
        return airline.getIcao() + Integer.parseInt(digits);
    }

    public static String randomFlightNumber4Digits(Airline airline) {
        int flightNumber = (int) (Math.random() * (9999 - 1000)) + 1000;
        return makeFlightNumber(airline.getIata(), flightNumber);
    }
}
