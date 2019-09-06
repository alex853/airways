/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.legacy.airways;

import org.joda.time.DateTime;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.ResultSet;

import net.simforge.legacy.airways.model.Flight;

public class FlightHelper {
    public static final int CheckinDuration = 50;
    public static final int CheckinDoneDuration = 10;
    public static final int BoardingDuration = 20;
    public static final int DepartingDuration = 10;
    public static final int LandedDuration = 10;
    public static final int UnboardingDuration = 20;
    public static final int UnboardingDoneDuration = 3;

    public static final int Checkin2Departure = CheckinDuration + CheckinDoneDuration + BoardingDuration + DepartingDuration;

    public static int getPOB(Connection connx, Flight flight) throws SQLException {
        int pob = 0;
        Statement st = connx.createStatement();
        ResultSet rs = st.executeQuery("select sum(size) from aw_pg where position_flight_id = " + flight.getId());
        if (rs.next()) {
            pob = rs.getInt(1);
        }
        rs.close();
        st.close();
        return pob;
    }

    public static int getSoldPercentage(Flight flight) {
        return (int) Math.round(((flight.getTotalTickets() - flight.getFreeTickets()) / (1.0 * flight.getTotalTickets()))*100);
    }

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
}
