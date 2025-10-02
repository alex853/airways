package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.FlightMissions;

public class TransportFlightHelper {
    private static final int CHECKIN_STARTS_BEFORE = 120 * Time.ONE_MINUTE;

    public static int calcCheckinStartTime(final FlightMissions.Mission flightMission) {
        return flightMission.getPlannedDepartureWorldTime() - CHECKIN_STARTS_BEFORE;
    }

    // todo ak0 calcCheckinEndTime - 30 mins before planned departure time
}
