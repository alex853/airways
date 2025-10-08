package net.simforge.airways2.world.processors;

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

// todo ak0 what if boarding has delayed? 
//          there is the fix however it seems like time of boarding start should be stored somewhere
//          and all following actions should be based on that time
// todo ak0 approach around check-in, boarding, deboarding is a bit non-natural
//          durations do not depend on airplane size, amount of doors open, etc
//          it can be improved
//          however the existing solution should work relatively fine
//          some observations are required to make a decision
public class JourneyProcessor {
    private static final Logger log = LoggerFactory.getLogger(JourneyProcessor.class);
    private static final int CLEANUP_TIMEOUT = 3 * Time.ONE_DAY;

    public static void process(final World world) {
        final int worldTime = world.getWorldTime();
        int processedThisTime = 0;

        final JourneyControl journeyControl = JourneyControl.instance(world);
        while (true) {
            final Optional<Journeys.Journey> journey = world.journeys().nextForHeartbeat(worldTime);
            if (journey.isEmpty()) {
                return;
            }

            if (processedThisTime == 10) {
                log.warn("TOO MANY JOURNEYS TO PROCESS, found j/y #{}, exiting", journey.get().getId());
                return;
            }

            try {
                processJourney(world, journeyControl, journey.get());
                processedThisTime++;
            } catch (final RuntimeException e) {
                log.warn("j/y #{} processing error", journey.get().getId(), e);
                throw e;
            }
        }
    }

    private static void processJourney(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        journey.setHeartbeatTime(0);
        switch (journey.getStatus()) {
            case LookingForTickets -> lookingForTickets(world, journeyControl, journey);
            case WaitingForCheckIn -> waitingForCheckin(world, journeyControl, journey);
            case WaitingForBoarding -> waitingForBoarding(world, journeyControl, journey);
            case WaitingForDeboarding -> waitingForDeboarding(world, journeyControl, journey);
            case JustArrived -> justArrived(world, journeyControl, journey);
            case ItinerariesDone -> itinerariesDone(world, journeyControl, journey);
            case Finished -> cleanup(world, journeyControl, journey);
        }
    }

    private static void lookingForTickets(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        // todo ak0 'roundtrip support' - consider 'direction' when determining fromCityId and toCityId

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
            journeyControl.waitForCheckin(journey);
            return;
        }

        final Collection<List<TransportFlights.Flight>> stopoverRoutes = findStopoverRoutes(world, journey, fromAirportIds, toAirportIds);
        if (!stopoverRoutes.isEmpty()) {
            final List<TransportFlights.Flight> stopoverRoute = stopoverRoutes.iterator().next();
            if (stopoverRoute.size() == 2) {
                final TransportFlights.Flight flight1 = stopoverRoute.get(0);
                final TransportFlights.Flight flight2 = stopoverRoute.get(1);
                bookStopoverFlightsJourney(world, journey, flight1, flight2);
                journeyControl.waitForCheckin(journey);
                return;
            }
        }

        journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * Time.ONE_DAY));
        // todo ak1 'limiting counter' to limit number of searches and in case of reaching some limit then journey goes to could-find-tickets and then it decreases stats
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

    private static class TFM {
        private final TransportFlights.Flight tf;
        private final FlightMissions.Mission fm;

        public TFM(TransportFlights.Flight tf, FlightMissions.Mission fm) {
            this.tf = tf;
            this.fm = fm;
        }
    }

    private static TFM toTfm(final World world, final TransportFlights.Flight tf) {
        return new TFM(tf, world.flightMissions().byId(tf.getFlightMissionId()).orElseThrow());
    }

    private static boolean isThereEnoughTickets(final Journeys.Journey journey, final TransportFlights.Flight tf) {
        return tf.getRemainedTickets().get(journey.getCabinService()) >= journey.getGroupSize();
    }

    private static void bookDirectFlightJourney(final World world, final Journeys.Journey journey, final TransportFlights.Flight flight) {
        journey.setTransportFlight1Id(flight.getId());
        TransportFlightControl.instance(world).obtainFlightTickets(flight, journey.getGroupSize(), journey.getCabinService());
    }

    private static void bookStopoverFlightsJourney(final World world, final Journeys.Journey journey, final TransportFlights.Flight flight1, final TransportFlights.Flight flight2) {
        journey.setTransportFlight1Id(flight1.getId());
        TransportFlightControl.instance(world).obtainFlightTickets(flight1, journey.getGroupSize(), journey.getCabinService());

        journey.setTransportFlight2Id(flight2.getId());
        TransportFlightControl.instance(world).obtainFlightTickets(flight2, journey.getGroupSize(), journey.getCabinService());
    }

    private static boolean isThereDirectRouteAvailable(TFM tfm, Set<Integer> fromAirportIds, Set<Integer> toAirportIds) {
        return fromAirportIds.contains(tfm.fm.getDepartureAirportId()) && toAirportIds.contains(tfm.fm.getDestinationAirportId());
    }

    private static void waitingForCheckin(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        final Optional<TransportFlights.Flight> flight = world.transportFlights().byId(journey.getTransportFlight1Id());
        if (flight.isEmpty()) {
            // todo ak1 cancel journey, 'update stats'
        } else if (TransportFlightHelper.flightStatusBeforeCheckin(flight.get().getStatus())) {
            final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flight.get().getFlightMissionId());
            // todo ak1 what if mission is empty - cancel journey, 'update stats'
            journey.setHeartbeatTime(Math.max(
                    TransportFlightHelper.calcCheckinStartTime(mission.get()) + (int) (0.8 * Math.random() * TransportFlightHelper.CHECKIN_DURATION), // todo ak0 consider actual times here
                    world.getWorldTime() + 5 * Time.ONE_MINUTE));
        } else if (TransportFlightHelper.flightStatusAllowsToCheckIn(flight.get().getStatus())) {
            checkin(world, journeyControl, journey);
        } else { // checkin & boarding finished -> journey is too late
            journey.setStatus(Journeys.Status.TooLateToBoard);
        }
    }

    private static void checkin(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.WaitingForBoarding);
        journey.setHeartbeatTime(world.getWorldTime());

        final TransportFlights.Flight flight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        flight.setPaxCheckedIn(flight.getPaxCheckedIn() + journey.getGroupSize());
    }

    private static void waitingForBoarding(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        final Optional<TransportFlights.Flight> flight = world.transportFlights().byId(journey.getTransportFlight1Id());
        if (flight.isEmpty()) {
            // todo ak1 cancel journey, 'update stats'
        } else if (TransportFlightHelper.flightStatusAllowsToStartBoarding(flight.get().getStatus())) {
            final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flight.get().getFlightMissionId());
            // todo ak1 what if mission is empty - cancel journey, 'update stats'
            journey.setHeartbeatTime(Math.max(
                    TransportFlightHelper.calcBoardingStartTime(mission.get()) + (int) (0.8 * Math.random() * TransportFlightHelper.BOARDING_DURATION), // todo ak0 consider actual times here
                    world.getWorldTime() + 5 * Time.ONE_MINUTE));
        } else if (flight.get().getStatus() == TransportFlights.Status.Boarding) {
            boarding(world, journeyControl, journey);
        } else { // checkin & boarding finished -> journey is too late
            journey.setStatus(Journeys.Status.TooLateToBoard);
        }
    }

    private static void boarding(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.OnBoard);
        // journey heartbeat is turned off till deboarding

        final TransportFlights.Flight flight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        flight.setPaxOnBoard(flight.getPaxOnBoard() + journey.getGroupSize());
    }

    private static void waitingForDeboarding(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        final Optional<TransportFlights.Flight> flight = world.transportFlights().byId(journey.getTransportFlight1Id());
        if (flight.isEmpty()) {
            // todo ak1 cancel journey, 'update stats'
        } else if (flight.get().getStatus() == TransportFlights.Status.Deboarding) {
            deboarding(world, journeyControl, journey);
        } else {
            // todo ak1 ???
        }
    }

    private static void deboarding(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.JustArrived);
        journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * Time.ONE_HOUR));

        final TransportFlights.Flight flight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        flight.setPaxOnBoard(flight.getPaxOnBoard() - journey.getGroupSize());
    }

    private static void justArrived(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        // todo ak1 'update stats' small increase to all c2c-s which can be connected via this airport pair

        journey.setTransportFlight1Id(journey.getTransportFlight2Id());
        journey.setTransportFlight2Id(0);
        if (journey.getTransportFlight1Id() == 0) {
            journey.setStatus(Journeys.Status.ItinerariesDone);
            journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * Time.ONE_HOUR));
        } else {
            journeyControl.waitForCheckin(journey);
        }
    }

    private static void itinerariesDone(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        // todo ak0 'update stats' big increase to c2c between original journey c2c and to reciprocal c2c

        // todo ak0 'roundtrip support'
        //          if this is a trip 'to', then switch flag 'return trip' and switch to 'looking for tickets'
        //          if this is a 'return trip' then finish the journey

        journey.setStatus(Journeys.Status.Finished);
        journey.setHeartbeatTime(world.getWorldTime() + CLEANUP_TIMEOUT);
    }

    private static void cleanup(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        world.journeys().deleteById(journey.getId());
    }
}
