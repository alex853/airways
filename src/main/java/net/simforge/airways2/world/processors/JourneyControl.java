package net.simforge.airways2.world.processors;

import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class JourneyControl {
    private static final Logger log = LoggerFactory.getLogger(JourneyControl.class);

    private static final int MAX_STAY_AT_DESTINATION = 7 * Time.ONE_DAY;
    private static final int MIN_STAY_AT_DESTINATION = Time.ONE_DAY;

    private static final int TERMINAL_STATUS_DURATION = Time.ONE_DAY;

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
        journey.setLastKnownLocationCityId(c2cFlow.getFromCityId());
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

        world.c2cFlowControl().updateSuccessRate(journey, 0.01f);
    }

    public void checkin(Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(EnumSet.of(
                        Journeys.Status.WaitingForCheckIn)
                .contains(journey.getStatus()));

        journey.setStatus(Journeys.Status.WaitingForBoarding);
        journey.setHeartbeatTime(world.getWorldTime());

        if (journey.getLocationCityId() != 0) {
            journey.setLastKnownLocationCityId(journey.getLocationCityId());
        }
        journey.setLocationCityId(0);

        final TransportFlights.Flight flight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        world.transportFlightControl().increasePaxCheckedIn(flight, journey.getGroupSize());

        log.info("j/y #{} - checked-in to t/f #{}", journey.getId(), journey.getTransportFlight1Id());
    }

    public void board(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.WaitingForBoarding);

        journey.setStatus(Journeys.Status.OnBoard);

        world.c2cFlowControl().updateSuccessRate(journey, 0.025f);

        log.info("j/y #{} - boarded to t/f #{}", journey.getId(), journey.getTransportFlight1Id());
    }

    public void deboard(Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.WaitingForDeboarding);

        journey.setStatus(Journeys.Status.JustArrived);
        journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * Time.ONE_HOUR));

        final TransportFlights.Flight flight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        // the flight could be already finished, then there is nothing to decrease
        if (flight.getStatus() == TransportFlights.Status.Deboarding) {
            world.transportFlightControl().decreasePaxOnBoard(flight, journey.getGroupSize());
        }

        log.info("j/y #{} - deboarded from t/f #{}, g/s {}, new PAX on board {}", journey.getId(), journey.getTransportFlight1Id(), journey.getGroupSize(), flight.getPaxOnBoard());
    }

    public void justArrived(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.JustArrived);

        final TransportFlights.Flight transportFlight1 = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        world.c2cFlowControl().updateSuccessRate(transportFlight1, 0.01f);

        // the journey pays for the transport flight it has come with, before the next transport flight (if there is one) becomes the first one
        payForTransportation(journey);

        shiftToNextTransportFlight(journey);

        moveToLandingAirportCity(journey, transportFlight1);

        if (noMoreTransportFlights(journey)) {
            journey.setStatus(Journeys.Status.ItinerariesDone);
            journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * Time.ONE_HOUR));
        } else {
            waitForCheckin(journey);
        }
    }

    /**
     * What the journey has paid to an operator.
     */
    public record Payout(int operatorId, int amount) {
    }

    /**
     * The journey which has just arrived pays the price it has (see Journey.getPrice) for the transport flight it has come with. If the journey has
     * a next transport flight (a stopover) the price is divided between them in proportion to their distances, the part for the next one stays in the
     * price and is paid when the journey arrives with it. What is paid is taken off the price, so the price is zero when everything is paid.
     * <p>
     * For a flight of a user (its flight mission has a user) the payment is a transfer from the account of the passengers to the account of the operator
     * of the flight (and it is written to the log). If an account does not exist, nothing moves, see BankControl.transferIfAccountsExist.
     *
     * @return what the journey has paid to the operator of the flight, empty if the journey has no price or the flight is not a flight of a user
     */
    public Optional<Payout> payForTransportation(final Journeys.Journey journey) {
        final int price = journey.getPrice();
        if (price <= 0) {
            return Optional.empty();
        }

        final TransportFlights.Flight transportFlight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(transportFlight.getFlightMissionId());

        int payout = price;
        if (journey.getTransportFlight2Id() > 0) {
            final double distance1 = mission.map(m -> distanceOf(m, true)).orElse(0.0);
            final double distance2 = world.transportFlights().byId(journey.getTransportFlight2Id())
                    .flatMap(tf -> world.flightMissions().byId(tf.getFlightMissionId()))
                    .map(m -> distanceOf(m, false))
                    .orElse(0.0);
            if (distance1 + distance2 > 0) {
                payout = (int) Math.round(price * distance1 / (distance1 + distance2));
            }
        }
        // what is paid is taken off the price: the part for the next transport flight (if there is one) stays
        journey.setPrice(price - payout);

        if (mission.isEmpty() || mission.get().getUserId() <= 0) {
            return Optional.empty();
        }

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.get().getAircraftId()).orElseThrow();
        final int operatorId = aircraft.getAircraftOperatorId();
        final String operatorName = world.aircraftOperators().byId(operatorId).map(AircraftOperators.AircraftOperator::getName).orElse("?");
        log.info("j/y #{} - pays {} to operator #{} {} for t/f #{}, f/m #{}", journey.getId(), payout, operatorId, operatorName, transportFlight.getId(), mission.get().getId());

        world.bankControl().transferIfAccountsExist(World25.PassengersBankAccountId, world.bankControl().operatorAccountId(operatorId), payout,
                BankTransactions.Type.JourneyPayment, "j/y #" + journey.getId());
        return Optional.of(new Payout(operatorId, payout));
    }

    // The distance of the transport flight of the mission: the airport of departure and the airport where the aircraft has landed (the destination one if it is not known)
    private double distanceOf(final FlightMissions.Mission mission, final boolean landed) {
        final int arrivalAirportId = landed && mission.getActualLandingAirportId() > 0 ? mission.getActualLandingAirportId() : mission.getDestinationAirportId();
        final Airports.Airport from = world.airports().byId(mission.getDepartureAirportId()).orElseThrow();
        final Airports.Airport to = world.airports().byId(arrivalAirportId).orElseThrow();
        return Geo.distance(from.getCoords(), to.getCoords());
    }

    public void startSpendingTheirTime(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.ItinerariesDone);

        journey.setStatus(Journeys.Status.SpendingTheirTime);
        journey.setHeartbeatTime(world.getWorldTime() + Tools.random(MIN_STAY_AT_DESTINATION, MAX_STAY_AT_DESTINATION));

        journey.setBookedCabinService(journey.getPreferredCabinService());

        world.c2cFlowControl().updateSuccessRate(journey, 0.05f);

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
        // location must match from city, otherwise it could keep a random city of the landing airport (e.g. after diversion)
        journey.setLocationCityId(toCityId);
        journey.setLastKnownLocationCityId(toCityId);

        scheduleLookingForTickets(journey);

        log.info("j/y #{} - switched for return trip, cities swapped, looking for tickets scheduled", journey.getId());
    }

    public boolean isToCityReached(final Journeys.Journey journey) {
        checkNotNull(journey);

        return journey.getLocationCityId() == journey.getToCityId();
    }

    // The journey has finished its itineraries, but landed not in TO city (diversion, unlinked airport etc.), so it should continue
    public void toCityNotReached(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.ItinerariesDone);

        scheduleLookingForTickets(journey);

        log.info("j/y #{} - TO city not reached, looking for tickets scheduled", journey.getId());
    }

    private void scheduleLookingForTickets(final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.LookingForTickets);
        journey.setAttemptCounter(0);
        journey.setHeartbeatTime(world.getWorldTime() + Tools.random(Time.ONE_DAY, 3 * Time.ONE_DAY));
    }

    public void finish(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.ItinerariesDone);

        journey.setStatus(Journeys.Status.Finished);
        journey.setHeartbeatTime(world.getWorldTime() + TERMINAL_STATUS_DURATION);

        world.c2cFlowControl().updateSuccessRate(journey, 0.1f);

        log.info("j/y #{} - finished, cleanup scheduled", journey.getId());
    }

    public void tooLateToBoard(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(EnumSet.of(
                        Journeys.Status.WaitingForCheckIn,
                        Journeys.Status.WaitingForBoarding)
                .contains(journey.getStatus()));

        Journeys.Status oldStatus = journey.getStatus();
        int oldHeartbeatTime = journey.getHeartbeatTime();

        final Optional<TransportFlights.Flight> transportFlight1O = world.transportFlights().byId(journey.getTransportFlight1Id());
        if (transportFlight1O.isEmpty()) {
            orphaned(journey);
            return;
        }
        final TransportFlights.Flight transportFlight1 = transportFlight1O.get();
        world.c2cFlowControl().updateSuccessRate(transportFlight1, -0.01f);

        // the status is not terminal, the journey gets back to looking for tickets in restartAfterTooLateToBoard
        // the short delay gives a good chance that the transport flight still exists, see FlightCleanup
        journey.setStatus(Journeys.Status.TooLateToBoard);
        journey.setHeartbeatTime(world.getWorldTime() + Tools.random(Time.ONE_MINUTE, 5 * Time.ONE_MINUTE));

        moveBackToDepartureCity(journey);

        log.info("j/y #{} - too late to board, was in {} status and heartbeat time {}, restart scheduled at {}", journey.getId(), oldStatus, Time.toLdtOrNull(oldHeartbeatTime), Time.toLdt(journey.getHeartbeatTime()));
    }

    // The transport flight or the flight mission of the journey is not known, so nothing can be done with the journey,
    // it is cleaned up after a day
    public void orphaned(final Journeys.Journey journey) {
        checkNotNull(journey);

        final Journeys.Status oldStatus = journey.getStatus();

        final int transportFlightId = journey.getTransportFlight1Id();
        final Optional<TransportFlights.Flight> transportFlight = world.transportFlights().byId(transportFlightId);
        final int flightMissionId = transportFlight.map(TransportFlights.Flight::getFlightMissionId).orElse(0);
        final boolean flightMissionExists = flightMissionId > 0 && world.flightMissions().byId(flightMissionId).isPresent();

        releaseTransportFlight2Tickets(journey);

        journey.setStatus(Journeys.Status.Orphaned);
        journey.setHeartbeatTime(world.getWorldTime() + TERMINAL_STATUS_DURATION);

        log.info("j/y #{} - orphaned, was in {} status, t/f #{} {}, f/m #{} {}, cleanup scheduled",
                journey.getId(), oldStatus,
                transportFlightId, transportFlight.isPresent() ? "exists" : "not found",
                flightMissionId, flightMissionExists ? "exists" : "not found");
    }

    public void restartAfterTooLateToBoard(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.TooLateToBoard);

        // the missed itinerary is dropped, no need to release its tickets, the flight is already boarding or departed
        journey.setTransportFlight1Id(0);

        releaseTransportFlight2Tickets(journey);

        // location city is already set to the city next to the airport in tooLateToBoard

        scheduleLookingForTickets(journey);

        log.info("j/y #{} - too late to board, itineraries dropped, looking for tickets scheduled", journey.getId());
    }

    public void couldNotFindTickets(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(Journeys.Status.LookingForTickets == journey.getStatus());

        journey.setStatus(Journeys.Status.CouldNotFindTickets);
        journey.setHeartbeatTime(world.getWorldTime() + TERMINAL_STATUS_DURATION);

        world.c2cFlowControl().updateSuccessRate(journey, -0.5f);

        log.info("j/y #{} - could not find tickets, cleanup scheduled", journey.getId());
    }

    public void scheduleDeboardingForAllOnBoardJourneys(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);

        world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.OnBoard))
                .forEach(this::scheduleDeboarding);
    }

    private boolean noMoreTransportFlights(final Journeys.Journey journey) {
        return journey.getTransportFlight1Id() == 0;
    }

    private void shiftToNextTransportFlight(final Journeys.Journey journey) {
        journey.setTransportFlight1Id(journey.getTransportFlight2Id());
        journey.setTransportFlight2Id(0);
    }
    
    // The moment of the deboarding is not chosen here: the flight lets the journeys out one after another (PaxManager, deboarding
    // counter). The heartbeat of the journey is only a safety net, see JourneyProcessor.waitingForDeboarding
    private void scheduleDeboarding(final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.WaitingForDeboarding);
        journey.setHeartbeatTime(world.getWorldTime() + PaxManager.Deboarding.JOURNEY_WATCH_INTERVAL);

        log.info("j/y #{} - waiting for deboarding from t/f #{}, first check at {}", journey.getId(), journey.getTransportFlight1Id(), Time.toLdt(journey.getHeartbeatTime()));
    }

    // The method is intended for returning a journey back to LookingForTickets when a VATSIM flight is cancelled from departing or flying states
    public void resetJourneyForcefully(Journeys.Journey journey) {
        checkNotNull(journey);
        // Any status accepted

        Journeys.Status oldStatus = journey.getStatus();

        journey.setStatus(Journeys.Status.LookingForTickets);
        journey.setHeartbeatTime(world.getWorldTime() + Time.ONE_HOUR);

        moveBackToDepartureCity(journey);

        // No need to release tickets, they are not used anymore on that the flight
        journey.setTransportFlight1Id(0);

        releaseTransportFlight2Tickets(journey);

        log.info("j/y #{} - has been reset forcefully back to LookingForTickets, status BEFORE was {}", journey.getId(), oldStatus);
        FlightStats.event("journey - reset journey forcefully");
    }

    private void releaseTransportFlight2Tickets(Journeys.Journey journey) {
        if (journey.getTransportFlight2Id() == 0) {
            return;
        }

        // the transport flight could be removed already, then there is nothing to release
        world.transportFlights().byId(journey.getTransportFlight2Id())
                .ifPresent(transportFlight2 -> world.transportFlightControl().releaseFlightTickets(
                        transportFlight2,
                        journey.getGroupSize(),
                        journey.getBookedCabinService()));
        journey.setTransportFlight2Id(0);
    }

    // Returns the journey to the city where it was before the flight. The stored last known location goes first, the transport
    // flight is not needed for it and may be lost; otherwise (old records) the city is guessed using the flight mission
    private void moveBackToDepartureCity(final Journeys.Journey journey) {
        final int lastKnownLocationCityId = journey.getLastKnownLocationCityId();
        if (lastKnownLocationCityId != 0) {
            journey.setLocationCityId(lastKnownLocationCityId);
            log.info("j/y #{} - moved back to the last known location, city #{} {}", journey.getId(), lastKnownLocationCityId,
                    world.cities().byId(lastKnownLocationCityId).map(city -> city.getName()).orElse("<unknown>"));
            return;
        }

        final Optional<TransportFlights.Flight> transportFlight1 = world.transportFlights().byId(journey.getTransportFlight1Id());

        FlightStats.event("journey - move back to departure city - no last known location");

        // the counter of these messages shows how often the journeys still go the old way
        log.warn("j/y #{} - last known location is not set, moving back the old way, t/f #{} {}", journey.getId(), journey.getTransportFlight1Id(), transportFlight1.isPresent() ? "exists" : "not found");

        final Optional<FlightMissions.Mission> flightMission = transportFlight1
                .flatMap(tf -> world.flightMissions().byId(tf.getFlightMissionId()));
        if (flightMission.isPresent()) {
            updateLocationCity(journey, journey.getFromCityId(), flightMission.get().getDepartureAirportId());
        } else {
            // the transport flight or its mission is lost and the city before the flight is not known, the best guess
            journey.setLocationCityId(journey.getFromCityId());
            journey.setLastKnownLocationCityId(journey.getFromCityId());
            log.warn("j/y #{} - t/f #{} or its mission not found, last known location is not set, location is set to the from city", journey.getId(), journey.getTransportFlight1Id());
            FlightStats.event("journey - move back to departure city - no FM found");
        }
    }

    private void moveToLandingAirportCity(Journeys.Journey journey, TransportFlights.Flight transportFlight1) {
        FlightMissions.Mission flightMission = world.flightMissions().byId(transportFlight1.getFlightMissionId()).orElseThrow();
        updateLocationCity(journey, journey.getToCityId(), flightMission.getActualLandingAirportId());
    }

    private void updateLocationCity(Journeys.Journey journey, int targetCityId, int targetAirportId) {
        List<Integer> cities = world.airport2city().allByAirportId(targetAirportId).map(Airport2City.Link::getCityId).toList();
        if (!cities.isEmpty()) {
            if (cities.contains(targetCityId)) {
                journey.setLocationCityId(targetCityId);
                journey.setLastKnownLocationCityId(targetCityId);
            } else {
                final int randomCityId = Tools.random(cities).orElseThrow();
                journey.setLocationCityId(randomCityId);
                journey.setLastKnownLocationCityId(randomCityId);
            }
        } else {
            String icao = world.airports().byId(targetAirportId).orElseThrow().getIcao();
            log.warn("j/y #{} - no city found for {}", journey.getId(), icao);
            FlightStats.event("journey - no city for airport " + icao);
        }
    }
}
