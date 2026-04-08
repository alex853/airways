package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;

import static com.google.common.base.Preconditions.checkNotNull;

public class TransportFlightHelper {
    public static final int CHECKIN_DURATION = 90 * Time.ONE_MINUTE;
    private static final int CHECKIN_ENDS_BEFORE_DEPARTURE = 30 * Time.ONE_MINUTE;
    public static final int BOARDING_DURATION = 10 * Time.ONE_MINUTE; // todo ak2 obsolete, can be/should be replaced
    private static final int BOARDING_ENDS_BEFORE_DEPARTURE = 10 * Time.ONE_MINUTE;
    public static final int AUTOMATIC_DEBOARDING_DELAY = 3 * Time.ONE_MINUTE;
    public static final int DEBOARDING_DURATION = 10 * Time.ONE_MINUTE;

    public static int calcCheckinStartTime(final FlightMissions.Mission flightMission) {
        checkNotNull(flightMission);
        return flightMission.getPlannedDepartureWorldTime() - (CHECKIN_DURATION + CHECKIN_ENDS_BEFORE_DEPARTURE);
    }

    public static int calcCheckinEndTime(final FlightMissions.Mission flightMission) {
        checkNotNull(flightMission);
        return flightMission.getPlannedDepartureWorldTime() - CHECKIN_ENDS_BEFORE_DEPARTURE;
    }

    public static int calcBoardingStartTime(final FlightMissions.Mission flightMission) {
        checkNotNull(flightMission);
        return flightMission.getPlannedDepartureWorldTime() - (BOARDING_DURATION + BOARDING_ENDS_BEFORE_DEPARTURE);
    }

    public static boolean flightStatusAllowsToPurchaseTicket(final TransportFlights.Status status) {
        return status == TransportFlights.Status.Scheduled
                || status == TransportFlights.Status.CheckIn
                || status == TransportFlights.Status.WaitingForBoarding
                || status == TransportFlights.Status.Boarding;
    }

    public static boolean flightStatusBeforeCheckin(final TransportFlights.Status status) {
        return status == TransportFlights.Status.Scheduled;
    }

    public static boolean flightStatusAllowsToCheckIn(TransportFlights.Status status) {
        return status == TransportFlights.Status.CheckIn
                || status == TransportFlights.Status.WaitingForBoarding
                || status == TransportFlights.Status.Boarding;
    }

    public static boolean flightStatusAllowsToStartBoarding(final TransportFlights.Status status) {
        return status == TransportFlights.Status.Scheduled
                || status == TransportFlights.Status.CheckIn
                || status == TransportFlights.Status.WaitingForBoarding;
    }
}
