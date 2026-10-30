package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;

import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class TransportFlightHelper {
    public static final int CHECKIN_DURATION = 90 * Time.ONE_MINUTE;
    private static final int CHECKIN_ENDS_BEFORE_DEPARTURE = 30 * Time.ONE_MINUTE;
    public static final int BOARDING_ENDS_BEFORE_DEPARTURE = 10 * Time.ONE_MINUTE;
    public static final int AUTOMATIC_DEBOARDING_DELAY = 3 * Time.ONE_MINUTE;

    public static final Set<TransportFlights.Status> VALID_STATUSES_FOR_TICKET_PURCHASE = Set.of(
            TransportFlights.Status.Scheduled,
            TransportFlights.Status.CheckIn,
            TransportFlights.Status.WaitingForBoarding,
            TransportFlights.Status.Boarding);
    public static final Set<Integer> VALID_STATUS_CODES_FOR_TICKET_PURCHASE = VALID_STATUSES_FOR_TICKET_PURCHASE.stream().map(TransportFlights.Status::code).collect(Collectors.toSet());

    public static int calcCheckinStartTime(final FlightMissions.Mission flightMission) {
        checkNotNull(flightMission);
        return flightMission.getPlannedDepartureWorldTime() - (CHECKIN_DURATION + CHECKIN_ENDS_BEFORE_DEPARTURE);
    }

    public static int calcCheckinEndTime(final FlightMissions.Mission flightMission) {
        checkNotNull(flightMission);
        return flightMission.getPlannedDepartureWorldTime() - CHECKIN_ENDS_BEFORE_DEPARTURE;
    }

    /**
     * The planned start of the boarding: the planned end (10 minutes before the planned departure) minus the duration of the boarding by the cabin layout of the flight
     * (PaxManager.Boarding.estimatePlannedDuration; the fixed 10 minutes of BOARDING_DURATION are gone, 2026-10-30).
     */
    public static int calcBoardingStartTime(final FlightMissions.Mission flightMission, final CabinLayout cabinLayout) {
        checkNotNull(flightMission);
        checkNotNull(cabinLayout);
        return PaxManager.Boarding.plannedStartTime(flightMission, cabinLayout);
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

    /**
     * The pilot of a user starts the boarding by the button: while the check-in is going (the transport flight goes from CheckIn to Boarding directly, the pilot does not wait
     * until the check-in is over) and after it (WaitingForBoarding). Not from Scheduled: the check-in has not even started then.
     */
    public static boolean flightStatusAllowsPilotToStartBoarding(final TransportFlights.Status status) {
        return status == TransportFlights.Status.CheckIn
                || status == TransportFlights.Status.WaitingForBoarding;
    }

    public static boolean flightStatusAllowsToBoard(final TransportFlights.Status status) {
        return status == TransportFlights.Status.CheckIn
                || status == TransportFlights.Status.WaitingForBoarding
                || status == TransportFlights.Status.Boarding;
    }
}
