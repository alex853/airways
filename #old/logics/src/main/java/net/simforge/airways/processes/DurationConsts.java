/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes;

public class DurationConsts {
    public static final long END_OF_CHECKIN_TO_DEPARTURE_MINS = 40;
    public static final long START_OF_CHECKIN_TO_DEPARTURE_MINS = 90;
    public static final long START_OF_BOARDING_TO_DEPARTURE_MINS = 35;
    public static final long DO_NOT_SCHEDULE_FLIGHTS_CLOSER_THAN_THAT_HOURS = 6;

    /* old code

        public static final int CheckinDuration = 50;
    public static final int CheckinDoneDuration = 10;
    public static final int BoardingDuration = 20;
    public static final int DepartingDuration = 10;
    public static final int LandedDuration = 10;
    public static final int UnboardingDuration = 20;
    public static final int UnboardingDoneDuration = 3;

    public static final int Checkin2Departure = CheckinDuration + CheckinDoneDuration + BoardingDuration + DepartingDuration;

    public static DateTime getCheckinDT(Flight flight) {
        DateTime depTime = flight.getDepTime();
        return DT.addMinutes(depTime, -Checkin2Departure);
    }

    public static DateTime getCheckinDoneDT(Flight flight) {
        DateTime depTime = flight.getDepTime();
        return DT.addMinutes(depTime, -(CheckinDoneDuration + BoardingDuration + DepartingDuration));
    }

    public static DateTime getBoardingDT(Flight flight) {
        DateTime depTime = flight.getDepTime();
        return DT.addMinutes(depTime, -(BoardingDuration + DepartingDuration));
    }

    public static DateTime getDeparingDT(Flight flight) {
        DateTime depTime = flight.getDepTime();
        return DT.addMinutes(depTime, -DepartingDuration);
    }

    public static DateTime getUnboardingDT(Flight flight) {
        DateTime arrTime = flight.getArrTime();
        return DT.addMinutes(arrTime, LandedDuration);
    }

    public static DateTime getDoneDT(Flight flight) {
        DateTime arrTime = flight.getArrTime();
        return DT.addMinutes(arrTime, LandedDuration + UnboardingDuration);
    }

    public static DateTime getEndOfUnboardingDoneDT(Flight flight) {
        DateTime startOfUnboardingDone = flight.getStatusDt();
        if (startOfUnboardingDone == null) {
            return getDoneDT(flight);
        }
        return DT.addMinutes(startOfUnboardingDone, UnboardingDoneDuration);
    }


     */
}
