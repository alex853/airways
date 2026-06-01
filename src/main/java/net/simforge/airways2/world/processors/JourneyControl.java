package net.simforge.airways2.world.processors;

import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;

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
        journey.setLocationCityId(c2cFlow.getFromCityId());
        return journey;
    }

    // 'No checks' means that the method does not check if the operation is valid with all those parameters
    public void bookDirectFlightJourneyNoChecks(Journeys.Journey journey, TransportFlights.Flight flight) {
        checkNotNull(journey);
        checkNotNull(flight);

        bookDirectFlightJourneyNoChecks(journey, journey.getPreferredCabinService(), flight);
    }

    // 'No checks' means that the method does not check if the operation is valid with all those parameters
    public void bookDirectFlightJourneyNoChecks(Journeys.Journey journey, CabinLayout.Service cabinService, TransportFlights.Flight flight) {
        checkNotNull(journey);
        checkNotNull(cabinService);
        checkNotNull(flight);

        journey.setTransportFlight1Id(flight.getId());
        world.transportFlightControl().obtainFlightTickets(flight, journey.getGroupSize(), cabinService);

        journey.setTransportFlight2Id(0);

        journey.setBookedCabinService(cabinService);
    }

    // 'No checks' means that the method does not check if the operation is valid with all those parameters
    public void bookStopoverFlightsJourneyNoChecks(final Journeys.Journey journey, final TransportFlights.Flight flight1, final TransportFlights.Flight flight2) {
        checkNotNull(journey);
        checkNotNull(flight1);
        checkNotNull(flight2);

        journey.setTransportFlight1Id(flight1.getId());
        world.transportFlightControl().obtainFlightTickets(flight1, journey.getGroupSize(), journey.getPreferredCabinService());

        journey.setTransportFlight2Id(flight2.getId());
        world.transportFlightControl().obtainFlightTickets(flight2, journey.getGroupSize(), journey.getPreferredCabinService());

        journey.setBookedCabinService(journey.getPreferredCabinService());
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
        journey.setLocationCityId(0);

        final TransportFlights.Flight flight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        world.transportFlightControl().increasePaxCheckedIn(flight, journey.getGroupSize());

        log.info("j/y #{} - checked-in to t/f #{}", journey.getId(), journey.getTransportFlight1Id());
    }

    public void board(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.WaitingForBoarding);

        journey.setStatus(Journeys.Status.OnBoard);

        world.c2cFlowControl().updateSuccessRate(journey, 0.001f);

        log.info("j/y #{} - boarded to t/f #{}", journey.getId(), journey.getTransportFlight1Id());
    }

    public void deboard(Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.WaitingForDeboarding);

        journey.setStatus(Journeys.Status.JustArrived);
        journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * Time.ONE_HOUR));

        final TransportFlights.Flight flight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        world.transportFlightControl().decreasePaxOnBoard(flight, journey.getGroupSize());

        log.info("j/y #{} - deboarded from t/f #{}, g/s {}, new PAX on board {}", journey.getId(), journey.getTransportFlight1Id(), journey.getGroupSize(), flight.getPaxOnBoard());
    }

    public void justArrived(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.JustArrived);

        final TransportFlights.Flight transportFlight1 = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        world.c2cFlowControl().updateSuccessRate(transportFlight1, 0.005f);

        shiftToNextTransportFlight(journey);

        moveToCityNextToLanding(journey, transportFlight1);

        if (noMoreTransportFlights(journey)) {
            journey.setStatus(Journeys.Status.ItinerariesDone);
            journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * Time.ONE_HOUR));
        } else {
            waitForCheckin(journey);
        }
    }

    public void startSpendingTheirTime(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.ItinerariesDone);

        journey.setStatus(Journeys.Status.SpendingTheirTime);
        journey.setHeartbeatTime(world.getWorldTime() + Tools.random(MIN_STAY_AT_DESTINATION, MAX_STAY_AT_DESTINATION));

        journey.setBookedCabinService(journey.getPreferredCabinService());

        world.c2cFlowControl().updateSuccessRate(journey, 0.04f);

        log.info("j/y #{} - start spending their time", journey.getId());
    }

    public void switchToReturnTrip(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.SpendingTheirTime);

        journey.setReturningBack(true);

        final int fromCityId = journey.getFromCityId();
        final int toCityId = journey.getToCityId();
        journey.setFromCityId(toCityId);
        journey.setToCityId(fromCityId);

        journey.setStatus(Journeys.Status.LookingForTickets);
        journey.setAttemptCounter(0);
        journey.setHeartbeatTime(world.getWorldTime() + Tools.random(MIN_STAY_AT_DESTINATION, MAX_STAY_AT_DESTINATION));

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

        releaseTransportFlight2Tickets(journey);

        Journeys.Status oldStatus = journey.getStatus();
        int oldHeartbeatTime = journey.getHeartbeatTime();

        TransportFlights.Flight transportFlight1 = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        world.c2cFlowControl().updateSuccessRate(transportFlight1, -0.02f);
        
        journey.setStatus(Journeys.Status.TooLateToBoard);
        journey.setHeartbeatTime(world.getWorldTime() + TERMINAL_STATUS_DURATION);

        moveBackToFromCity(journey, transportFlight1);

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

        log.info("j/y #{} - deboarding from t/f #{} scheduled at {}", journey.getId(), journey.getTransportFlight1Id(), Time.toLdt(journey.getHeartbeatTime()));
    }

    // The method is intended for returning a journey back to LookingForTickets when a VATSIM flight is cancelled from departing or flying states
    public void resetJourneyForcefully(Journeys.Journey journey) {
        checkNotNull(journey);
        // Any status accepted

        Journeys.Status oldStatus = journey.getStatus();

        journey.setStatus(Journeys.Status.LookingForTickets);
        journey.setHeartbeatTime(world.getWorldTime() + Time.ONE_HOUR);

        moveBackToFromCity(journey, world.transportFlights().byId(journey.getId()).orElseThrow());

        // No need to release tickets, they are not used anymore on that the flight
        journey.setTransportFlight1Id(0);

        releaseTransportFlight2Tickets(journey);

        log.info("j/y #{} - has been reset forcefully back to LookingForTickets, status BEFORE was {}", journey.getId(), oldStatus);
    }

    private void releaseTransportFlight2Tickets(Journeys.Journey journey) {
        if (journey.getTransportFlight2Id() == 0) {
            return;
        }

        world.transportFlightControl().releaseFlightTickets(
                world.transportFlights().byId(journey.getTransportFlight2Id()).orElseThrow(),
                journey.getGroupSize(),
                journey.getBookedCabinService());
        journey.setTransportFlight2Id(0);
    }

    private void moveBackToFromCity(Journeys.Journey journey, TransportFlights.Flight transportFlight1) {
        FlightMissions.Mission flightMission = world.flightMissions().byId(transportFlight1.getFlightMissionId()).orElseThrow();
        updateLocationCity(journey, journey.getFromCityId(), flightMission.getDepartureAirportId());
    }

    private void moveToCityNextToLanding(Journeys.Journey journey, TransportFlights.Flight transportFlight1) {
        FlightMissions.Mission flightMission = world.flightMissions().byId(transportFlight1.getFlightMissionId()).orElseThrow();
        updateLocationCity(journey, journey.getToCityId(), flightMission.getActualLandingAirportId());
    }

    private void updateLocationCity(Journeys.Journey journey, int targetCityId, int targetAirportId) {
        List<Integer> cities = world.airport2city().allByAirportId(targetAirportId).map(Airport2City.Link::getCityId).toList();
        if (!cities.isEmpty()) {
            if (cities.contains(targetCityId)) {
                journey.setLocationCityId(targetCityId);
            } else {
                journey.setLocationCityId(Tools.random(cities).get());
            }
        } else {
            log.warn("j/y #{} - no city found for a/p #{}", journey.getId(), targetAirportId);
            FlightStats.event("journey - no city for airport " + targetAirportId);
        }
    }
}
