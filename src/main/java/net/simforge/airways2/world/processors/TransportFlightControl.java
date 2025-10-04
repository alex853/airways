package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.ScheduledFlights;
import net.simforge.airways2.world.datamodel.TransportFlights;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static net.simforge.airways2.world.datamodel.EventsToProcess.Type.StartAutomaticDeboarding;

public class TransportFlightControl {
    private static final int CHECKIN_TICK = Time.ONE_MINUTE;
    private static final int BOARDING_TICK = Time.ONE_MINUTE;
    private static final int DEBOARDING_TICK = Time.ONE_MINUTE;

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

    // todo ak3 'cabin service'
    public void obtainFlightTickets(final TransportFlights.Flight flight, final int tickets, final CabinLayout.Service service) {
        final CabinLayout remainedTickets = flight.getRemainedTickets();
        final int newEconomy = remainedTickets.getEconomy() - tickets;
        flight.setRemainedTickets(CabinLayout.Y(newEconomy));
    }

    public boolean checkinTimeComes(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Scheduled);
        final FlightMissions.Mission flightMission = world.flightMissions().byId(transportFlight.getFlightMissionId()).orElseThrow();
        final int checkinStartTime = TransportFlightHelper.calcCheckinStartTime(flightMission);
        return checkinStartTime < world.getWorldTime();
    }

    public void startCheckIn(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Scheduled);
        transportFlight.setStatus(TransportFlights.Status.CheckIn);
        transportFlight.setHeartbeatTime(world.getWorldTime() + CHECKIN_TICK);
    }

    public void continueCheckIn(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);
        transportFlight.setHeartbeatTime(world.getWorldTime() + CHECKIN_TICK);
    }

    public boolean allPaxCheckedIn(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);
        final int remainedUnsold = transportFlight.getRemainedTickets().getTotal();
        if (remainedUnsold != 0) {
            // if some tickets still available then we can't tell that all PAX checked-in even if all PAX with tickets already checked-in
            // this will lead to a case that check-in will continue to be open till the end of check-in window if there are some tickets are still available
            return false;
        }

        final int ticketsSold = transportFlight.getTotalTickets().getTotal() - remainedUnsold;
        return transportFlight.getPaxCheckedIn() == ticketsSold;
    }

    public boolean checkinTimeEnds(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);
        final FlightMissions.Mission flightMission = world.flightMissions().byId(transportFlight.getFlightMissionId()).orElseThrow();
        final int checkinEndTime = TransportFlightHelper.calcCheckinEndTime(flightMission);
        return checkinEndTime < world.getWorldTime();
    }

    public void waitForBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);
        transportFlight.setStatus(TransportFlights.Status.WaitingForBoarding);
        transportFlight.setHeartbeatTime(0);
    }

    public void startBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(TransportFlightHelper.flightStatusAllowsToStartBoarding(transportFlight.getStatus()));
        transportFlight.setStatus(TransportFlights.Status.Boarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + BOARDING_TICK);
    }

    public void continueBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + BOARDING_TICK);
    }

    public boolean allPaxBoarded(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        return transportFlight.getPaxOnBoard() == transportFlight.getPaxCheckedIn(); // todo ak2 another check against sold tickets?
    }

    public boolean boardingTimeEnds(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);
        final FlightMissions.Mission flightMission = world.flightMissions().byId(transportFlight.getFlightMissionId()).orElseThrow();
        final int checkinEndTime = TransportFlightHelper.calcBoardingEndTime(flightMission);
        return checkinEndTime < world.getWorldTime();
    }

    public void waitForDeparture(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);
        transportFlight.setStatus(TransportFlights.Status.WaitingForDeparture);
        transportFlight.setHeartbeatTime(0);
    }

    public void scheduleAutomaticDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForDeboarding);
        world.eventsToProcess().sendEvent(StartAutomaticDeboarding, transportFlight.getId(), world.getWorldTime() + TransportFlightHelper.AUTOMATIC_DEBOARDING_DELAY);
    }

    public void startDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForDeboarding);
        transportFlight.setStatus(TransportFlights.Status.Deboarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + DEBOARDING_TICK);

        JourneyControl.instance(world).scheduleDeboardingForAllOnBoardJourneys(transportFlight);
    }

    public void continueDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + DEBOARDING_TICK);
    }

    public boolean allPaxDeboarded(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        return transportFlight.getPaxOnBoard() == 0;
    }

    public void finish(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);
        checkArgument(allPaxDeboarded(transportFlight));
        transportFlight.setStatus(TransportFlights.Status.Finished);
        transportFlight.setHeartbeatTime(0);
    }
}
