package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.FlightMissions;

public class TransportFlightHelper {
    private static final int CHECKIN_STARTS_BEFORE = 120 * Time.ONE_MINUTE;
    private static final int CHECKIN_ENDS_BEFORE = 30 * Time.ONE_MINUTE;

    public static int calcCheckinStartTime(final FlightMissions.Mission flightMission) {
        checkNotNull(flightMission);
        return flightMission.getPlannedDepartureWorldTime() - CHECKIN_STARTS_BEFORE;
    }

    public static int calcCheckinEndTime(final FlightMissions.Mission flightMission) {
        checkNotNull(flightMission);
        return flightMission.getPlannedDepartureWorldTime() - CHECKIN_ENDS_BEFORE;
    }
}
