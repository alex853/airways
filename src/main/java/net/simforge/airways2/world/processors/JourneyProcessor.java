package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airport2City;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class JourneyProcessor {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(JourneyProcessor.class);

    public static void process(final World world) {
        Processing.heartbeat(() -> world.journeys().nextForHeartbeat(world.getWorldTime()),
                journey -> processJourney(world, journey));
    }

    private static void processJourney(final World world, final Journeys.Journey journey) {
        journey.setHeartbeatTime(0);
        switch (journey.getStatus()) {
            case LookingForTickets -> lookingForTickets(world, journey);
            case WaitingForCheckIn -> waitingForCheckin(world, journey);
            case WaitingForBoarding -> waitingForBoarding(world, journey);
            case WaitingForDeboarding -> waitingForDeboarding(world, journey);
            case JustArrived -> justArrived(world, journey);
            case ItinerariesDone -> itinerariesDone(world, journey);
            case Finished, CouldNotFindTickets, TooLateToBoard -> cleanup(world, journey);
        }
    }

    private static void lookingForTickets(final World world, final Journeys.Journey journey) {
        final Set<Integer> fromAirportIds = world.airport2city()
                .allByCityId(journey.getFromCityId()).stream()
                .map(Airport2City.Link::getAirportId)
                .collect(Collectors.toSet());
        final Set<Integer> toAirportIds = world.airport2city()
                .allByCityId(journey.getToCityId()).stream()
                .map(Airport2City.Link::getAirportId)
                .collect(Collectors.toSet());

        final Collection<TransportFlights.Flight> foundDirectFlights = findDirectFlights(world, journey, fromAirportIds, toAirportIds);
        if (!foundDirectFlights.isEmpty()) {
            final TransportFlights.Flight directFlight = foundDirectFlights.iterator().next();
            bookDirectFlightJourney(world, journey, directFlight);
            world.journeyControl().waitForCheckin(journey);
            return;
        }

        final Collection<List<TransportFlights.Flight>> stopoverRoutes = findStopoverRoutes(world, journey, fromAirportIds, toAirportIds);
        if (!stopoverRoutes.isEmpty()) {
            final List<TransportFlights.Flight> stopoverRoute = stopoverRoutes.iterator().next();
            if (stopoverRoute.size() == 2) {
                final TransportFlights.Flight flight1 = stopoverRoute.get(0);
                final TransportFlights.Flight flight2 = stopoverRoute.get(1);
                bookStopoverFlightsJourney(world, journey, flight1, flight2);
                world.journeyControl().waitForCheckin(journey);
                return;
            }
        }

        if (journey.getAttemptCounter() < 3) {
            journey.setHeartbeatTime(world.getWorldTime() + Tools.random(Time.ONE_HOUR, Time.ONE_DAY));
            journey.setAttemptCounter(journey.getAttemptCounter() + 1);
        } else {
            world.journeyControl().couldNotFindTickets(journey);
        }
    }

    private static Collection<TransportFlights.Flight> findDirectFlights(final World world, final Journeys.Journey journey, final Set<Integer> fromAirportIds, final Set<Integer> toAirportIds) {
        final int TIME_RESERVE = 4 * Time.ONE_HOUR;

        return world.transportFlights()
                .filter(tf -> TransportFlightHelper.flightStatusAllowsToPurchaseTicket(tf.getStatus())
                                && isThereEnoughTickets(journey, tf)).stream()
                .map(tf -> toTfm(world, tf))
                .filter(tfm -> flightDepartsLaterThan(tfm, world.getWorldTime() + TIME_RESERVE)
                        && isThereDirectRouteAvailable(tfm, fromAirportIds, toAirportIds))
                .map(tfm -> tfm.tf)
                .toList();
    }

    private static Collection<List<TransportFlights.Flight>> findStopoverRoutes(final World world, final Journeys.Journey journey, final Set<Integer> fromAirportIds, final Set<Integer> toAirportIds) {
        final Collection<List<TransportFlights.Flight>> result = new ArrayList<>();

        final int TIME_RESERVE = 4 * Time.ONE_HOUR;

        final Collection<TFM> allFlight1s = world.transportFlights()
                .filter(tf -> TransportFlightHelper.flightStatusAllowsToPurchaseTicket(tf.getStatus())
                        && isThereEnoughTickets(journey, tf)).stream()
                .map(tf -> toTfm(world, tf))
                .filter(tfm -> flightDepartsFrom(tfm, fromAirportIds)
                        && flightDepartsLaterThan(tfm, world.getWorldTime() + TIME_RESERVE)
                ).toList();

        for (final TFM flight1 : allFlight1s) {
            final Collection<TFM> allFlight2s = world.transportFlights()
                    .filter(tf -> TransportFlightHelper.flightStatusAllowsToPurchaseTicket(tf.getStatus())
                            && isThereEnoughTickets(journey, tf)).stream()
                    .map(tf -> toTfm(world, tf))
                    .filter(tfm -> flightDepartsFrom(tfm, Collections.singleton(flight1.fm.getDestinationAirportId()))
                            && flightArrivesTo(tfm, toAirportIds)
                            && flightDepartsLaterThan(tfm, flight1.fm.getPlannedArrivalWorldTime() + TIME_RESERVE)
                    ).toList();
            allFlight2s.forEach(flight2 -> result.add(Arrays.asList(flight1.tf, flight2.tf)));
        }

        return result;
    }

    private static boolean flightDepartsLaterThan(TFM tfm, int departureTimeThreshold) {
        return tfm.fm.getPlannedDepartureWorldTime() >= departureTimeThreshold;
    }

    private static boolean flightDepartsFrom(TFM tfm, Set<Integer> fromAirportIds) {
        return fromAirportIds.contains(tfm.fm.getDepartureAirportId());
    }

    private static boolean flightArrivesTo(TFM tfm, Set<Integer> toAirportIds) {
        return toAirportIds.contains(tfm.fm.getDestinationAirportId());
    }

    private record TFM(TransportFlights.Flight tf, FlightMissions.Mission fm) {
    }

    private static TFM toTfm(final World world, final TransportFlights.Flight tf) {
        return new TFM(tf, world.flightMissions().byId(tf.getFlightMissionId()).orElseThrow());
    }

    private static boolean isThereEnoughTickets(final Journeys.Journey journey, final TransportFlights.Flight tf) {
        return tf.getRemainedTickets().get(journey.getCabinService()) >= journey.getGroupSize();
    }

    private static void bookDirectFlightJourney(final World world, final Journeys.Journey journey, final TransportFlights.Flight flight) {
        journey.setTransportFlight1Id(flight.getId());
        world.transportFlightControl().obtainFlightTickets(flight, journey.getGroupSize(), journey.getCabinService());
    }

    private static void bookStopoverFlightsJourney(final World world, final Journeys.Journey journey, final TransportFlights.Flight flight1, final TransportFlights.Flight flight2) {
        journey.setTransportFlight1Id(flight1.getId());
        world.transportFlightControl().obtainFlightTickets(flight1, journey.getGroupSize(), journey.getCabinService());

        journey.setTransportFlight2Id(flight2.getId());
        world.transportFlightControl().obtainFlightTickets(flight2, journey.getGroupSize(), journey.getCabinService());
    }

    private static boolean isThereDirectRouteAvailable(TFM tfm, Set<Integer> fromAirportIds, Set<Integer> toAirportIds) {
        return fromAirportIds.contains(tfm.fm.getDepartureAirportId()) && toAirportIds.contains(tfm.fm.getDestinationAirportId());
    }

    private static void waitingForCheckin(final World world, final Journeys.Journey journey) {
        final Optional<TransportFlights.Flight> flight = world.transportFlights().byId(journey.getTransportFlight1Id());
        //noinspection StatementWithEmptyBody todo ak1 resolve it
        if (flight.isEmpty()) {
            // todo ak1 'cancel journey safely'
        } else if (TransportFlightHelper.flightStatusBeforeCheckin(flight.get().getStatus())) {
            final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flight.get().getFlightMissionId());
            checkArgument(mission.isPresent()); // todo ak1 what if mission is empty - 'cancel journey safely'
            journey.setHeartbeatTime(Math.max(
                    TransportFlightHelper.calcCheckinStartTime(mission.get()) + (int) (0.8 * Math.random() * TransportFlightHelper.CHECKIN_DURATION), // todo ak1 consider actual times here
                    world.getWorldTime() + 5 * Time.ONE_MINUTE));
        } else if (TransportFlightHelper.flightStatusAllowsToCheckIn(flight.get().getStatus())) {
            checkin(world, journey);
        } else { // checkin & boarding finished -> journey is too late
            world.journeyControl().tooLateToBoard(journey);
        }
    }

    private static void checkin(final World world, final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.WaitingForBoarding);
        journey.setHeartbeatTime(world.getWorldTime());

        // todo ak1 move into tfc or rework into pax manager
        final TransportFlights.Flight flight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        flight.setPaxCheckedIn(flight.getPaxCheckedIn() + journey.getGroupSize());
    }

    private static void waitingForBoarding(final World world, final Journeys.Journey journey) {
        // most of the logic is in pax manager
        final Optional<TransportFlights.Flight> flight = world.transportFlights().byId(journey.getTransportFlight1Id());
        //noinspection StatementWithEmptyBody todo ak1 resolve this
        if (flight.isEmpty()) {
            // todo ak1 'cancel journey safely'
        } else if (!TransportFlightHelper.flightStatusAllowsToStartBoarding(flight.get().getStatus())) { // checkin & boarding finished -> journey is too late
            world.journeyControl().tooLateToBoard(journey);
        }
    }

    private static void waitingForDeboarding(final World world, final Journeys.Journey journey) {
        final Optional<TransportFlights.Flight> flight = world.transportFlights().byId(journey.getTransportFlight1Id());
        //noinspection StatementWithEmptyBody todo ak1 resolve this
        if (flight.isEmpty()) {
            // todo ak1 'cancel journey safely'
        } else //noinspection StatementWithEmptyBody todo ak1 resolve this
            if (flight.get().getStatus() == TransportFlights.Status.Deboarding) {
            deboarding(world, journey);
        } else {
            // todo ak1 ???
        }
    }

    private static void deboarding(final World world, final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.JustArrived);
        journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * Time.ONE_HOUR));

        // todo ak1 move into tfc or rework into pax manager
        final TransportFlights.Flight flight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        flight.setPaxOnBoard(flight.getPaxOnBoard() - journey.getGroupSize());
    }

    private static void justArrived(final World world, final Journeys.Journey journey) {
        // todo ak0 'update stats' - airport pair delta

        journey.setTransportFlight1Id(journey.getTransportFlight2Id());
        journey.setTransportFlight2Id(0);
        if (journey.getTransportFlight1Id() == 0) {
            journey.setStatus(Journeys.Status.ItinerariesDone);
            journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * Time.ONE_HOUR));
        } else {
            world.journeyControl().waitForCheckin(journey);
        }
    }

    private static void itinerariesDone(final World world, final Journeys.Journey journey) {
        if (journey.isReturningBack()) {
            world.journeyControl().finish(journey);
        } else {
            world.journeyControl().switchToReturnTrip(journey);
        }
    }

    private static void cleanup(final World world, final Journeys.Journey journey) {
        world.journeys().deleteById(journey.getId());
    }
}
