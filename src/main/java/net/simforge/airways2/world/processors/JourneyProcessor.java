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
        }
    }

    private static void lookingForTickets(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        final int fromCityId = journey.getFromCityId();
        final Set<Integer> fromAirportIds = world.airport2city().allByCityId(fromCityId).stream().map(Airport2City.Link::getAirportId).collect(Collectors.toSet());

        final int toCityId = journey.getToCityId();
        final Set<Integer> toAirportIds = world.airport2city().allByCityId(toCityId).stream().map(Airport2City.Link::getAirportId).collect(Collectors.toSet());

        final Collection<TransportFlights.Flight> foundFlights = world.transportFlights().filter(tf -> statusAllowsToPurchaseTicket(tf.getStatus())
                && isDirectRoute(world, tf, fromAirportIds, toAirportIds)
                && tf.getRemainedTickets().getTotal() >= journey.getGroupSize());

        if (foundFlights.isEmpty()) {
            journey.setHeartbeatTime(world.getWorldTime() + (int)(Math.random() * Time.ONE_DAY));
            return;
        }

        final TransportFlights.Flight flight = foundFlights.iterator().next();

        journey.setStatus(Journeys.Status.WaitingForCheckin);
        journey.setTransportFlight1Id(flight.getId());
        journey.setHeartbeatTime(world.getWorldTime());

        // todo ak3 support required service type
        final CabinLayout remainedTickets = flight.getRemainedTickets();
        final int newEconomy = remainedTickets.getEconomy() - journey.getGroupSize();
        flight.setRemainedTickets(CabinLayout.Y(newEconomy));
    }

    private static boolean statusAllowsToPurchaseTicket(final TransportFlights.Status status) {
        return status == TransportFlights.Status.Scheduled
                || status == TransportFlights.Status.Checkin
                || status == TransportFlights.Status.WaitingForBoarding
                || status == TransportFlights.Status.Boarding;
    }

    private static boolean isDirectRoute(World world, TransportFlights.Flight tf, Set<Integer> fromAirportIds, Set<Integer> toAirportIds) {
        final FlightMissions.Mission mission = world.flightMissions().byId(tf.getFlightMissionId()).orElseThrow();
        return fromAirportIds.contains(mission.getDepartureAirportId()) && toAirportIds.contains(mission.getDestinationAirportId());
    }

    private static void waitingForCheckin(final World world, final JourneyControl journeyControl, final Journeys.Journey journey) {
        // todo ak0 temporary code, need to put real code here
        journey.setHeartbeatTime(world.getWorldTime() + (int)(Math.random() * Time.ONE_HOUR));
    }
}
