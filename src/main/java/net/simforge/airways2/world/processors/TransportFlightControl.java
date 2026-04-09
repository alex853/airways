package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static net.simforge.airways2.world.datamodel.EventsToProcess.Type.StartAutomaticDeboarding;

public class TransportFlightControl {
    private static final int CHECKIN_TICK = Time.ONE_MINUTE;
    private static final int BOARDING_TICK = Time.TICK;
    private static final int DEBOARDING_TICK = Time.ONE_MINUTE;

    private static final Logger log = LoggerFactory.getLogger(FlightMissionControl.class);

    private final World world;

    public TransportFlightControl(final World world) {
        this.world = world;
    }

    private PaxManager paxManager() {
        return world.paxManager();
    }

    @SuppressWarnings("UnusedReturnValue")
    public TransportFlights.Flight createTransportFlight(FlightMissions.Mission flightMission) {
        return createTransportFlight(flightMission, chooseDefaultCabinLayout(flightMission));
    }

    public TransportFlights.Flight createTransportFlight(FlightMissions.Mission flightMission, CabinLayout cabinLayout) {
        return createTransportFlight(flightMission, null, cabinLayout);
    }

    public TransportFlights.Flight createTransportFlight(FlightMissions.Mission flightMission,
                                                         ScheduledFlights.Flight scheduledFlight) {
        return createTransportFlight(flightMission, scheduledFlight, chooseDefaultCabinLayout(flightMission));
    }

    public TransportFlights.Flight createTransportFlight(FlightMissions.Mission flightMission,
                                                         ScheduledFlights.Flight scheduledFlight,
                                                         CabinLayout cabinLayout) {
        checkNotNull(flightMission);
        checkNotNull(cabinLayout);

        final TransportFlights.Flight transportFlight = world.transportFlights().create(
                flightMission,
                scheduledFlight,
                cabinLayout);

        final int checkinStartsAt = TransportFlightHelper.calcCheckinStartTime(flightMission);
        transportFlight.setHeartbeatTime(checkinStartsAt);

        world.log(EventLog.EventType.TransportFlightCreated, EventLog.id(transportFlight), flightMission, scheduledFlight != null ? EventLog.id(scheduledFlight) : null);
        log.info("t/f #{} - created t/f for f/m #{}, s/f #{}", transportFlight.getId(), flightMission.getId(), scheduledFlight != null ? scheduledFlight.getId() : "///");

        return transportFlight;
    }

    private CabinLayout chooseDefaultCabinLayout(FlightMissions.Mission flightMission) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(flightMission.getAircraftId()).orElseThrow();
        final String aircraftType = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao();

        return switch (aircraftType) {
            case "B773" -> CabinLayout.FJWY(8, 49, 40, 138);
            case "A320" -> CabinLayout.JY(8, 138);
            default -> CabinLayout.Y(99);
        };
    }

    public void obtainFlightTickets(final TransportFlights.Flight flight, final int tickets, final CabinLayout.Service service) {
        checkNotNull(flight);
        checkArgument(tickets >= 0);
        checkNotNull(service);

        final CabinLayout remainedTickets = flight.getRemainedTickets();
        flight.setRemainedTickets(remainedTickets.occupySeats(tickets, service));
    }

    public boolean ifCheckInTimeComes(final TransportFlights.Flight transportFlight) {
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

        log.info("t/f #{} - check-in started", transportFlight.getId());
        world.log(EventLog.EventType.TransportFlightCheckInStarted, EventLog.id(transportFlight));
    }

    public void continueCheckIn(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);

        transportFlight.setHeartbeatTime(world.getWorldTime() + CHECKIN_TICK);
    }

    public boolean areAllPaxCheckedIn(final TransportFlights.Flight transportFlight) {
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

    public boolean isCheckInFinishTimePassed(final TransportFlights.Flight transportFlight) {
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

        log.info("t/f #{} - check-in ended, waiting for boarding", transportFlight.getId());
        world.log(EventLog.EventType.TransportFlightCheckInEnded, EventLog.id(transportFlight));
    }

    public void startBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(TransportFlightHelper.flightStatusAllowsToStartBoarding(transportFlight.getStatus()));

        paxManager().startBoarding(transportFlight);

        transportFlight.setStatus(TransportFlights.Status.Boarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + BOARDING_TICK);

        log.info("t/f #{} - boarding started", transportFlight.getId());
        world.log(EventLog.EventType.TransportFlightBoardingStarted, EventLog.id(transportFlight));
    }

    public void continueBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        paxManager().continueBoarding(transportFlight);

        transportFlight.setHeartbeatTime(world.getWorldTime() + BOARDING_TICK);
    }

    public boolean areAllPaxBoarded(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        return transportFlight.getPaxOnBoard() == transportFlight.getPaxCheckedIn(); // todo ak2 another check against sold tickets?
    }

    public boolean isBoardingFinishTimePassed(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        return paxManager().wasBoardingFinishTimePassed(transportFlight);
    }

    public void waitForDeparture(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        paxManager().finishBoarding(transportFlight);

        transportFlight.setStatus(TransportFlights.Status.WaitingForDeparture);
        transportFlight.setHeartbeatTime(0);

        log.info("t/f #{} - boarding ended, waiting for departure", transportFlight.getId());
        world.log(EventLog.EventType.TransportFlightBoardingEnded, EventLog.id(transportFlight));
    }

    public void whenFlightDepartsFromGate(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.Departure);

        log.info("t/f #{} - departed", transportFlight.getId());
        world.log(EventLog.EventType.TransportFlightDeparted, EventLog.id(transportFlight));
    }

    public void whenFlightTakeoffs(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.Flying);

        log.info("t/f #{} - flying", transportFlight.getId());
    }

    public void whenFlightLands(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.Arrival);

        log.info("t/f #{} - arrival", transportFlight.getId());
    }

    public void whenFlightArrivesToGate(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.WaitingForDeboarding);

        log.info("t/f #{} - arrived, waiting for deboarding", transportFlight.getId());
        world.log(EventLog.EventType.TransportFlightArrived, EventLog.id(transportFlight));
    }

    public void scheduleAutomaticDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForDeboarding);

        world.eventsToProcess().sendEvent(StartAutomaticDeboarding, transportFlight.getId(), world.getWorldTime() + TransportFlightHelper.AUTOMATIC_DEBOARDING_DELAY);

        log.info("t/f #{} - automatic deboarding scheduled", transportFlight.getId());
    }

    public void startDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForDeboarding);
        transportFlight.setStatus(TransportFlights.Status.Deboarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + DEBOARDING_TICK);

        log.info("t/f #{} - deboarding started", transportFlight.getId());
        world.log(EventLog.EventType.TransportFlightDeboardingStarted, EventLog.id(transportFlight));

        world.journeyControl().scheduleDeboardingForAllOnBoardJourneys(transportFlight);
    }

    public void continueDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + DEBOARDING_TICK);
    }

    public boolean areAllPaxDeboarded(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        return transportFlight.getPaxOnBoard() == 0;
    }

    public void finish(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);
        checkArgument(areAllPaxDeboarded(transportFlight));

        transportFlight.setStatus(TransportFlights.Status.Finished);
        transportFlight.setHeartbeatTime(0);

        log.info("t/f #{} - finished", transportFlight.getId());
        world.log(EventLog.EventType.TransportFlightFinished, EventLog.id(transportFlight));
    }
}
