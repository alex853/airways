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
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Scheduled);
        transportFlight.setStatus(TransportFlights.Status.Checkin);
        transportFlight.setHeartbeatTime(world.getWorldTime() + CHECKIN_TICK);
    }

    public void continueCheckin(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Checkin);
        transportFlight.setHeartbeatTime(world.getWorldTime() + CHECKIN_TICK);
    }

    public boolean allPaxCheckedIn(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Checkin);
        final int remainedUnsold = transportFlight.getRemainedTickets().getTotal();
        if (remainedUnsold != 0) {
            // if some tickets still available then we can't tell that all PAX checked-in even if all PAX with tickets already checked-in
            // this will lead to a case that check-in will continue be open till the end of check-in window if there are some tickets are still available
            return false;
        }

        final int ticketsSold = transportFlight.getTotalTickets().getTotal() - remainedUnsold; 
        final int paxCheckedIn = 0; // todo ak1 iterate through journeys and check their states - world.journeys().filter(j -> j.getFlightId() == tfId && j.getStatus() == WaitingForDeparture).sum(j.groupSize)
        return paxCheckedIn == ticketsSold;
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
