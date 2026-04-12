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

    private static final Logger log = LoggerFactory.getLogger(TransportFlightControl.class);

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

    public void releaseFlightTickets(final TransportFlights.Flight flight, final int tickets, final CabinLayout.Service service) {
        checkNotNull(flight);
        checkArgument(tickets >= 0);
        checkNotNull(service);

        final CabinLayout remainedTickets = flight.getRemainedTickets();
        flight.setRemainedTickets(remainedTickets.releaseSeats(tickets, service));
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
    }

    public void continueCheckIn(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);

        transportFlight.setHeartbeatTime(world.getWorldTime() + CHECKIN_TICK);
    }

    public void increasePaxCheckedIn(TransportFlights.Flight transportFlight, int groupSize) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);
        checkArgument(groupSize > 0);

        transportFlight.setPaxCheckedIn(transportFlight.getPaxCheckedIn() + groupSize);
    }

    public boolean areAllPaxCheckedIn(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);

        if (transportFlight.getRemainedTickets().getTotal() > 0) {
            // if some tickets still available then we can't tell that all PAX checked-in even if all PAX with tickets already checked-in
            // this will lead to a case that check-in will continue to be open till the end of check-in window if there are some tickets are still available
            return false;
        }

        return transportFlight.getPaxCheckedIn() >= transportFlight.getSoldTickets();
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
    }

    public void startBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(TransportFlightHelper.flightStatusAllowsToStartBoarding(transportFlight.getStatus()));

        transportFlight.setStatus(TransportFlights.Status.Boarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + BOARDING_TICK);

        paxManager().startBoarding(transportFlight);

        log.info("t/f #{} - boarding started", transportFlight.getId());
    }

    public void continueBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        paxManager().continueBoarding(transportFlight);

        transportFlight.setHeartbeatTime(world.getWorldTime() + BOARDING_TICK);
    }

    public boolean areAllPaxBoarded(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        return transportFlight.getPaxOnBoard() >= transportFlight.getSoldTickets();
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
    }

    public void whenFlightDepartsFromGate(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.Departure);

        log.info("t/f #{} - departed", transportFlight.getId());
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

        world.journeyControl().scheduleDeboardingForAllOnBoardJourneys(transportFlight);

        log.info("t/f #{} - deboarding started, PAX on board {}", transportFlight.getId(), transportFlight.getPaxOnBoard());
    }

    public void decreasePaxOnBoard(TransportFlights.Flight transportFlight, int groupSize) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);
        checkArgument(groupSize > 0);

        transportFlight.setPaxOnBoard(Math.max(0, transportFlight.getPaxOnBoard() - groupSize));
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
    }

    // Active flight is expectedly transportFlight1Id for all the journeys
    public void unloadJourneysForcefullyFromActiveFlight(TransportFlights.Flight transportFlight) {
        world.journeys()
                .filter(world.journeys().byTransportFlight1Id(transportFlight.getId()))
                .forEach(journey -> world.journeyControl().resetJourneyForcefully(journey));

        transportFlight.setPaxOnBoard(0);

        log.warn("t/f #{} - unloaded forcefully, set PAX on board to 0", transportFlight.getId());
    }
}
