package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class JourneyControl {
    private static final Logger log = LoggerFactory.getLogger(JourneyControl.class);

    private static final int MAX_STAY_AT_DESTINATION = 7 * Time.ONE_DAY;
    private static final int MIN_STAY_AT_DESTINATION = Time.ONE_DAY;

    private static final int TERMINAL_STATUS_DURATION = 3 * Time.ONE_DAY;

    private final World world;

    public JourneyControl(World world) {
        this.world = world;
    }

    public Journeys.Journey create(final City2CityFlows.Flow c2cFlow, final CabinLayout.Service service) {
        checkNotNull(c2cFlow);
        checkNotNull(service);

        final Journeys.Journey journey = world.journeys().create(
                Journeys.Status.LookingForTickets,
                c2cFlow.getFromCityId(),
                c2cFlow.getToCityId(),
                c2cFlow.getNextGroupSize(),
                service);
        journey.setHeartbeatTime(world.getWorldTime());
        return journey;
    }

    // 'No checks' means that the method does not check if the operation is valid with all those parameters
    public void bookDirectFlightJourneyNoChecks(final Journeys.Journey journey, final TransportFlights.Flight flight) {
        checkNotNull(journey);
        checkNotNull(flight);

        journey.setTransportFlight1Id(flight.getId());
        world.transportFlightControl().obtainFlightTickets(flight, journey.getGroupSize(), journey.getCabinService());
    }

    // 'No checks' means that the method does not check if the operation is valid with all those parameters
    public void bookStopoverFlightsJourneyNoChecks(final Journeys.Journey journey, final TransportFlights.Flight flight1, final TransportFlights.Flight flight2) {
        checkNotNull(journey);
        checkNotNull(flight1);
        checkNotNull(flight2);

        journey.setTransportFlight1Id(flight1.getId());
        world.transportFlightControl().obtainFlightTickets(flight1, journey.getGroupSize(), journey.getCabinService());

        journey.setTransportFlight2Id(flight2.getId());
        world.transportFlightControl().obtainFlightTickets(flight2, journey.getGroupSize(), journey.getCabinService());
    }

    public void waitForCheckin(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(EnumSet.of(
                        Journeys.Status.LookingForTickets,
                        Journeys.Status.JustArrived)
                .contains(journey.getStatus()));

        journey.setStatus(Journeys.Status.WaitingForCheckIn);
        journey.setHeartbeatTime(world.getWorldTime());

        world.c2cFlowControl().updateSuccessRate(journey, 0.005f);
    }

    public void checkin(Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(EnumSet.of(
                        Journeys.Status.WaitingForCheckIn)
                .contains(journey.getStatus()));

        journey.setStatus(Journeys.Status.WaitingForBoarding);
        journey.setHeartbeatTime(world.getWorldTime());

        final TransportFlights.Flight flight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        world.transportFlightControl().increasePaxCheckedIn(flight, journey.getGroupSize());
    }

    public void board(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.WaitingForBoarding);

        journey.setStatus(Journeys.Status.OnBoard);

        world.c2cFlowControl().updateSuccessRate(journey, 0.001f);
    }

    public void justArrived(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.JustArrived);

        final TransportFlights.Flight transportFlight1 = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        world.c2cFlowControl().updateSuccessRate(transportFlight1, 0.005f);

        shiftToNextTransportFlight(journey);

        if (noMoreTransportFlights(journey)) {
            journey.setStatus(Journeys.Status.ItinerariesDone);
            journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * Time.ONE_HOUR));
        } else {
            waitForCheckin(journey);
        }
    }

    public void switchToReturnTrip(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.ItinerariesDone);

        journey.setReturningBack(true);

        final int fromCityId = journey.getFromCityId();
        final int toCityId = journey.getToCityId();
        journey.setFromCityId(toCityId);
        journey.setToCityId(fromCityId);

        journey.setStatus(Journeys.Status.LookingForTickets);
        journey.setAttemptCounter(0);
        journey.setHeartbeatTime(world.getWorldTime() + Tools.random(MIN_STAY_AT_DESTINATION, MAX_STAY_AT_DESTINATION));

        world.c2cFlowControl().updateSuccessRate(journey, 0.04f);

        log.info("j/y #{} - switched for return trip, cities swapped, looking for tickets scheduled", journey.getId());
    }

    public void finish(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.ItinerariesDone);

        journey.setStatus(Journeys.Status.Finished);
        journey.setHeartbeatTime(world.getWorldTime() + TERMINAL_STATUS_DURATION);

        world.c2cFlowControl().updateSuccessRate(journey, 0.06f);

        log.info("j/y #{} - finished, cleanup scheduled", journey.getId());
    }

    public void tooLateToBoard(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(EnumSet.of(
                        Journeys.Status.WaitingForCheckIn,
                        Journeys.Status.WaitingForBoarding)
                .contains(journey.getStatus()));

        // todo ak2 'cancel journey safely' with removal all following tickets etc

        Journeys.Status oldStatus = journey.getStatus();
        int oldHeartbeatTime = journey.getHeartbeatTime();

        TransportFlights.Flight transportFlight1 = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        world.c2cFlowControl().updateSuccessRate(transportFlight1, -0.02f);
        
        journey.setStatus(Journeys.Status.TooLateToBoard);
        journey.setHeartbeatTime(world.getWorldTime() + TERMINAL_STATUS_DURATION);

        log.info("j/y #{} - too late to board, was in {} status and heartbeat time {}, cleanup scheduled", journey.getId(), oldStatus, Time.toLdtOrNull(oldHeartbeatTime));
    }

    public void couldNotFindTickets(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(Journeys.Status.LookingForTickets == journey.getStatus());

        journey.setStatus(Journeys.Status.CouldNotFindTickets);
        journey.setHeartbeatTime(world.getWorldTime() + TERMINAL_STATUS_DURATION);

        world.c2cFlowControl().updateSuccessRate(journey, -1.0f);

        log.info("j/y #{} - could not find tickets, cleanup scheduled", journey.getId());
    }

    public void scheduleDeboardingForAllOnBoardJourneys(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);

        world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.OnBoard))
                .forEach(this::scheduleDeboardingAtRandomTime);
    }

    private boolean noMoreTransportFlights(final Journeys.Journey journey) {
        return journey.getTransportFlight1Id() == 0;
    }

    private void shiftToNextTransportFlight(final Journeys.Journey journey) {
        journey.setTransportFlight1Id(journey.getTransportFlight2Id());
        journey.setTransportFlight2Id(0);
    }
    
    private void scheduleDeboardingAtRandomTime(final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.WaitingForDeboarding);
        journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * TransportFlightHelper.DEBOARDING_DURATION));
    }

    // The method is intended for returning a journey back to LookingForTickets when a VATSIM flight is cancelled from departing or flying states
    public void resetJourneyForcefully(Journeys.Journey journey) {
        Journeys.Status oldStatus = journey.getStatus();
        int oldHeartbeatTime = journey.getHeartbeatTime();

        log.info("j/y #{} - OLD {} status and heartbeat time {}", journey.getId(), oldStatus, Time.toLdtOrNull(oldHeartbeatTime)); // todo ak0 some bug here

        checkNotNull(journey);
        // Any status accepted

        journey.setStatus(Journeys.Status.LookingForTickets);
        journey.setHeartbeatTime(world.getWorldTime() + Time.ONE_HOUR);

        // No need to release tickets, they are not used anymore on that the flight
        journey.setTransportFlight1Id(0);

        if (journey.getTransportFlight2Id() != 0) {
            world.transportFlightControl().releaseFlightTickets(
                    world.transportFlights().byId(journey.getTransportFlight2Id()).orElseThrow(),
                    journey.getGroupSize(),
                    journey.getCabinService());
            journey.setTransportFlight2Id(0);
        }

        log.info("j/y #{} - has been reset forcefully back to LookingForTickets, was in {} status and heartbeat time {}", journey.getId(), oldStatus, Time.toLdtOrNull(oldHeartbeatTime));
    }
}
