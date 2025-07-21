package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.ScheduledFlights;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.worldbuilder.World25;

import java.io.IOException;

public class TransportFlightHelper {
    private static final int CHECKIN_STARTS_BEFORE = 120 * Time.ONE_MINUTE;

    public static TransportFlights.Flight createTransportFlight(final World world,
                                                                final FlightMissions.Mission flightMission,
                                                                final ScheduledFlights.Flight scheduledFlight) {
        final TransportFlights.Flight transportFlight = world.transportFlights().create(flightMission, scheduledFlight, 160);

        final int checkinStartsAt = calcCheckinStartTime(flightMission);
        transportFlight.setHeartbeatTime(checkinStartsAt);

        return transportFlight;
    }

    public static int calcCheckinStartTime(final FlightMissions.Mission flightMission) {
        return flightMission.getPlannedDepartureWorldTime() - CHECKIN_STARTS_BEFORE;
    }

    public static void updateExistingFlights() throws IOException {
        final World world = World25.load();

        world.transportFlights()
                .filter(tf -> tf.getStatus() == TransportFlights.Status.Scheduled
                        && tf.getHeartbeatTime() == 0)
                .forEach(tf -> world.flightMissions()
                        .byId(tf.getFlightMissionId())
                        .ifPresent(fm -> tf.setHeartbeatTime(calcCheckinStartTime(fm))
                ));

        world.save();
    }
}
