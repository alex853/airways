package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.ScheduledFlights;
import net.simforge.airways2.world.datamodel.TransportFlights;

public class TransportFlightControl {
    private final World world;

    private TransportFlightControl(final World world) {
        this.world = world;
    }

    public static TransportFlightControl instance(final World world) {
        return new TransportFlightControl(world);
    }


    public TransportFlights.Flight createTransportFlight(final FlightMissions.Mission flightMission) {
        return createTransportFlight(flightMission, null);
    }

    public TransportFlights.Flight createTransportFlight(final FlightMissions.Mission flightMission,
                                                         final ScheduledFlights.Flight scheduledFlight) {
        final TransportFlights.Flight transportFlight = world.transportFlights().create(
                flightMission,
                scheduledFlight,
                CabinLayout.Y(160));

        final int checkinStartsAt = TransportFlightHelper.calcCheckinStartTime(flightMission);
        transportFlight.setHeartbeatTime(checkinStartsAt);

        return transportFlight;
    }

    public boolean checkinTimeComes(final TransportFlights.Flight transportFlight) {
        final FlightMissions.Mission flightMission = world.flightMissions().byId(transportFlight.getFlightMissionId()).orElseThrow();
        final int checkinStartTime = TransportFlightHelper.calcCheckinStartTime(flightMission);
        return checkinStartTime < world.getWorldTime();
    }

    public void startCheckin(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.Checkin);
        // todo ak1 this is made incorrect intentionally
        //          this should be replaced by counting checked-in pax instead
        //          in fact, this should activate pax to do check-in
        //          OR it should do check-in while pax are passive
        transportFlight.setHeartbeatTime(world.getWorldTime() + (int)(Math.random() * Time.ONE_HOUR));
    }

    public boolean allPaxCheckedIn(final TransportFlights.Flight transportFlight) {
        return true; // todo ak1 this will be replaced by normal check-in implementation
    }

    public void waitForBoarding(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.WaitingForBoarding);
        transportFlight.setHeartbeatTime(0);
    }

    public void startBoarding(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.Boarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + 10 * Time.ONE_MINUTE); // todo ak1 normal implementation expected
    }

    public boolean allPaxBoarded(final TransportFlights.Flight transportFlight) {
        return true; // todo ak1 normal implementation expected
    }

    public void waitForDeparture(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.WaitingForDeparture);
        transportFlight.setHeartbeatTime(0);
    }

    public void startDeboarding(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.Deboarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + 10 * Time.ONE_MINUTE); // todo ak1 normal implementation expected
    }

    public boolean allPaxDeboarded(final TransportFlights.Flight transportFlight) {
        return true; // todo ak1 normal implementation expected
    }

    public void finish(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.Finished);
        transportFlight.setHeartbeatTime(0);
    }
}
