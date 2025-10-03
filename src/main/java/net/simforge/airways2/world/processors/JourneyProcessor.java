package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
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

public class JourneyProcessor {
    private static final Logger log = LoggerFactory.getLogger(JourneyProcessor.class);

    public static void process(final World world) {
        final int worldTime = world.getWorldTime();

        final JourneyControl journeyControl = JourneyControl.instance(world);
        while (true) {
            final Optional<Journeys.Journey> journey = world.journeys().nextForHeartbeat(worldTime);
            if (journey.isEmpty()) {
                break;
            }

            try {
                processJourney(world, journeyControl, journey.get());
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
            case WaitingForCheckin -> waitingForCheckin(world, journeyControl, journey);
            case WaitingForBoarding -> waitingForBoarding(world, journeyControl, journey);
        }
    }

    private static void lookingForTickets(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        final int fromCityId = journey.getFromCityId();
        final Set<Integer> fromAirportIds = world.airport2city().allByCityId(fromCityId).stream().map(Airport2City.Link::getAirportId).collect(Collectors.toSet());

        final int toCityId = journey.getToCityId();
        final Set<Integer> toAirportIds = world.airport2city().allByCityId(toCityId).stream().map(Airport2City.Link::getAirportId).collect(Collectors.toSet());

        final Collection<TransportFlights.Flight> foundFlights = world.transportFlights().filter(tf -> flightStatusAllowsToPurchaseTicket(tf.getStatus())
                && isThereDirectRouteAvailable(world, tf, fromAirportIds, toAirportIds)
                && tf.getRemainedTickets().getTotal() >= journey.getGroupSize());

        if (foundFlights.isEmpty()) {
            journey.setHeartbeatTime(world.getWorldTime() + (int)(Math.random() * Time.ONE_DAY));
            return;
        }

        final TransportFlights.Flight flight = foundFlights.iterator().next();

        bookDirectFlightJourney(world, journey, flight);
    }

    private static void bookDirectFlightJourney(World world, Journeys.Journey journey, TransportFlights.Flight flight) {
        journey.setStatus(Journeys.Status.WaitingForCheckin);
        journey.setHeartbeatTime(world.getWorldTime());

        journey.setTransportFlight1Id(flight.getId());

        // todo ak3 support required service type
        final CabinLayout remainedTickets = flight.getRemainedTickets();
        final int newEconomy = remainedTickets.getEconomy() - journey.getGroupSize();
        flight.setRemainedTickets(CabinLayout.Y(newEconomy));
    }

    private static boolean isThereDirectRouteAvailable(World world, TransportFlights.Flight tf, Set<Integer> fromAirportIds, Set<Integer> toAirportIds) {
        final FlightMissions.Mission mission = world.flightMissions().byId(tf.getFlightMissionId()).orElseThrow();
        return fromAirportIds.contains(mission.getDepartureAirportId()) && toAirportIds.contains(mission.getDestinationAirportId());
    }

    private static void waitingForCheckin(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        final Optional<TransportFlights.Flight> flight = world.transportFlights().byId(journey.getTransportFlight1Id());
        if (flight.isEmpty()) {
            // todo ak1 cancel journey, update stats
        } else if (flightStatusBeforeCheckin(flight.get().getStatus())) {
            final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flight.get().getFlightMissionId());
            // todo ak1 what if mission is empty - cancel journey, update stats
            final int checkinStartTime = TransportFlightHelper.calcCheckinStartTime(mission.get());
            final int checkinEndTime = TransportFlightHelper.calcCheckinEndTime(mission.get());
            journey.setHeartbeatTime(checkinStartTime + (int) (0.8 * Math.random() * (checkinEndTime - checkinStartTime)));
        } else if (flightStatusAllowsCheckin(flight.get().getStatus())) {
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
            // todo ak1 cancel journey, update stats
        } else if (flightStatusBeforeBoarding(flight.get().getStatus())) {
            final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flight.get().getFlightMissionId());
            // todo ak1 what if mission is empty - cancel journey, update stats
            final int boardingStartTime = TransportFlightHelper.calcBoardingStartTime(mission.get());
            final int boardingEndTime = TransportFlightHelper.calcBoardingEndTime(mission.get());
            journey.setHeartbeatTime(boardingStartTime + (int) (0.8 * Math.random() * (boardingEndTime - boardingStartTime)));
        } else if (flight.get().getStatus() == TransportFlights.Status.Boarding) {
            boarding(world, journeyControl, journey);
        } else { // checkin & boarding finished -> journey is too late
            journey.setStatus(Journeys.Status.TooLateToBoard);
        }
    }

    private static void boarding(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.OnBoard);
        // heartbeat is turned off till deboarding

        final TransportFlights.Flight flight = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow();
        flight.setPaxOnBoard(flight.getPaxOnBoard() + journey.getGroupSize());
    }

    // todo ak0 journey sleeps till deboarding

    // these methods will go to tf-helper
    private static boolean flightStatusAllowsToPurchaseTicket(final TransportFlights.Status status) {
        return status == TransportFlights.Status.Scheduled
                || status == TransportFlights.Status.Checkin
                || status == TransportFlights.Status.WaitingForBoarding
                || status == TransportFlights.Status.Boarding;
    }

    private static boolean flightStatusBeforeCheckin(final TransportFlights.Status status) {
        return status == TransportFlights.Status.Scheduled;
    }

    private static boolean flightStatusAllowsCheckin(TransportFlights.Status status) {
        return status == TransportFlights.Status.Checkin
                || status == TransportFlights.Status.WaitingForBoarding
                || status == TransportFlights.Status.Boarding;
    }

    private static boolean flightStatusBeforeBoarding(final TransportFlights.Status status) {
        return status == TransportFlights.Status.Scheduled
                || status == TransportFlights.Status.Checkin
                || status == TransportFlights.Status.WaitingForBoarding;
    }
}
