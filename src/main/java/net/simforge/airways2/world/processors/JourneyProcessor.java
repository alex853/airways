package net.simforge.airways2.world.processors;

import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airport2City;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class JourneyProcessor {
    private static final Logger log = LoggerFactory.getLogger(JourneyProcessor.class);

    // A flight is found by the search of tickets while its status allows to buy a ticket (TransportFlightHelper.VALID_STATUSES_FOR_TICKET_PURCHASE: until the boarding is over),
    // not by its planned departure (decided 2026-10-30, it was a reserve of 4 hours before the departure): the flights are announced shortly and the planned time of a flight which
    // is tracked via VATSIM is only an estimate, the longer the flight stands at the gate the more journeys find it. A journey checks in in the statuses CheckIn, WaitingForBoarding
    // and Boarding (flightStatusAllowsToCheckIn).
    // the time between the arrival of the first flight of a stopover route and the departure of the second one
    static final int STOPOVER_CONNECTION_RESERVE = 2 * Time.ONE_HOUR;

    public static void process(final World world) {
        // the index of the flights lives for this world step only: the flights do not appear and do not change their status while the journeys are processed
        final RouteIndex routes = new RouteIndex(world);
        Processing.heartbeat(() -> world.journeys().nextForHeartbeat(world.getWorldTime()),
                journey -> processJourney(world, journey, routes));
    }

    /**
     * The flights open for tickets (by their status) grouped by the departure airport, built at the first search of a step: one scan of the flights per step instead of
     * the scans per journey (and per flight of a stopover route). The seats are not kept here, they change as the journeys book, so they are checked at every search.
     */
    static class RouteIndex {
        private final World world;
        private Map<Integer, List<TFM>> byDepartureAirportId;
        // the journeys of pilots which have skipped a flight because the account has less than the ticket, and the first of such flights (for the event)
        private final Map<Integer, TFM> skippedForMoney = new HashMap<>();

        RouteIndex(final World world) {
            this.world = world;
        }

        /**
         * The flights departing from the airports, which have seats of the journey (the preferred class, or one up or one down), in the order of the storage.
         */
        List<TFM> departingFrom(final Collection<Integer> airportIds, final Journeys.Journey journey) {
            if (byDepartureAirportId == null) {
                byDepartureAirportId = new HashMap<>();
                world.transportFlights().allOpenForTickets().forEach(tf -> {
                    final TFM tfm = toTfm(world, tf);
                    byDepartureAirportId.computeIfAbsent(tfm.fm.getDepartureAirportId(), id -> new ArrayList<>()).add(tfm);
                });
            }

            final List<TFM> result = new ArrayList<>();
            final Persons.Person pilot = journey.isPilotJourney() ? world.persons().byPilotJourneyId(journey.getId()).orElse(null) : null;
            final long balance = pilot != null ? world.bankAccounts().byId(pilot.getBankAccountId()).map(BankAccounts.BankAccount::getBalance).orElse(Long.MAX_VALUE) : Long.MAX_VALUE;
            for (final int airportId : airportIds) {
                for (final TFM tfm : byDepartureAirportId.getOrDefault(airportId, List.of())) {
                    if (tfm.tf.getRemainedTickets().chooseService(journey.getGroupSize(), journey.getPreferredCabinService()) != null) {
                        // the pilot takes a flight whose ticket the account can pay (no reserve, decided 2026-11-10); the others are skipped, and it is told to the pilot
                        if (pilot != null && world.journeyControl().ticketPrice(tfm.tf, journey) > balance) {
                            skippedForMoney.putIfAbsent(journey.getId(), tfm);
                            continue;
                        }
                        result.add(tfm);
                    }
                }
            }
            if (airportIds.size() > 1) {
                result.sort(Comparator.comparingInt(tfm -> tfm.tf.getId()));
            }
            return result;
        }
    }

    private static void processJourney(final World world, final Journeys.Journey journey, final RouteIndex routes) {
        journey.setHeartbeatTime(0);

        // the journey of a pilot which has no pilot (the link is lost): nobody travels by it, it is finished as orphaned
        if (journey.isPilotJourney() && world.persons().byPilotJourneyId(journey.getId()).isEmpty()
                && journey.getStatus() != Journeys.Status.Finished && journey.getStatus() != Journeys.Status.Orphaned && journey.getStatus() != Journeys.Status.CouldNotFindTickets) {
            log.warn("j/y #{} - the journey of a pilot has no pilot, {} status, orphaned", journey.getId(), journey.getStatus());
            world.journeyControl().orphaned(journey);
            return;
        }

        switch (journey.getStatus()) {
            case LookingForTickets -> lookingForTickets(world, journey, routes);
            case WaitingForCheckIn -> waitingForCheckin(world, journey);
            case WaitingForBoarding -> waitingForBoarding(world, journey);
            case WaitingForDeboarding -> waitingForDeboarding(world, journey);
            case JustArrived -> justArrived(world, journey);
            case ItinerariesDone -> itinerariesDone(world, journey);
            case SpendingTheirTime -> switchToReturnTrip(world, journey);
            case TooLateToBoard -> world.journeyControl().restartAfterTooLateToBoard(journey);
            case Finished, CouldNotFindTickets, Orphaned -> cleanup(world, journey);
        }
    }

    // The search of tickets of a journey, in this order (decided 2026-10-30): a flight to a hub which brings the journey closer to its destination (findHubFlight: the journey goes
    // to the hub, lands there, is not at its destination and starts the search again from the hub, see itinerariesDone and docs/journeys-hub-mode.md; a flight which goes right to
    // the destination, if it is a hub, is the best of them), then a direct flight, then a route of two flights with a stopover (findStopoverRoutes); only if there is nothing
    // the attempt is counted. The stopover routes were switched off for a day (2026-10-30: the second leg took the places of a flight of a pilot and brought no income) and are
    // back since every flight of such a route has its own price (price1 and price2 of the journey), see docs/journeys-hub-mode.md.
    private static void lookingForTickets(final World world, final Journeys.Journey journey, final RouteIndex routes) {
        final Set<Integer> fromAirportIds = world.airport2city()
                .allByCityId(journey.getLocationCityId())
                .map(Airport2City.Link::getAirportId)
                .collect(Collectors.toSet());
        final Set<Integer> toAirportIds = world.airport2city()
                .allByCityId(journey.getToCityId())
                .map(Airport2City.Link::getAirportId)
                .collect(Collectors.toSet());

        final Optional<TransportFlights.Flight> hubFlight = findHubFlight(world, journey, fromAirportIds, routes);
        if (hubFlight.isPresent()) {
            log.info("j/y #{} - goes to the hub by t/f #{}", journey.getId(), hubFlight.get().getId());
            world.journeyControl().bookDirectFlightJourneyNoChecks(journey, chooseCabinService(journey, hubFlight.get()), hubFlight.get());
            world.journeyControl().waitForCheckin(journey);
            return;
        }

        final Collection<TransportFlights.Flight> foundDirectFlights = findDirectFlights(journey, fromAirportIds, toAirportIds, routes);
        if (!foundDirectFlights.isEmpty()) {
            final TransportFlights.Flight directFlight = pickRandom(foundDirectFlights);
            world.journeyControl().bookDirectFlightJourneyNoChecks(journey, chooseCabinService(journey, directFlight), directFlight);
            world.journeyControl().waitForCheckin(journey);
            return;
        }

        final Collection<List<TransportFlights.Flight>> stopoverRoutes = findStopoverRoutes(journey, fromAirportIds, toAirportIds, routes);
        if (!stopoverRoutes.isEmpty()) {
            final List<TransportFlights.Flight> stopoverRoute = stopoverRoutes.iterator().next();
            if (stopoverRoute.size() == 2) {
                final TransportFlights.Flight flight1 = stopoverRoute.get(0);
                final TransportFlights.Flight flight2 = stopoverRoute.get(1);
                world.journeyControl().bookStopoverFlightsJourneyNoChecks(journey, flight1, flight2);
                world.journeyControl().waitForCheckin(journey);
                return;
            }
        }

        if (journey.isPilotJourney()) {
            // nothing is booked: if a flight was skipped for the money, the pilot is told
            final TFM skipped = routes.skippedForMoney.get(journey.getId());
            if (skipped != null) {
                world.persons().byPilotJourneyId(journey.getId()).ifPresent(person -> world.personControl().onPilotJourneyCouldNotAfford(person,
                        skipped.fm.getDepartureAirportId(), skipped.fm.getDestinationAirportId(), world.journeyControl().ticketPrice(skipped.tf, journey)));
            }
            // the journey of a pilot never gives up and does not count the attempts: the pilot or the user ends it
            journey.setHeartbeatTime(world.getWorldTime() + world.journeyControl().nextSearchDelay(journey));
            return;
        }

        if (journey.getAttemptCounter() < 3) {
            journey.setHeartbeatTime(world.getWorldTime() + Tools.random(Time.ONE_DAY, 3 * Time.ONE_DAY));
            journey.setAttemptCounter(journey.getAttemptCounter() + 1);
        } else {
            world.journeyControl().couldNotFindTickets(journey);
        }
    }

    private static Collection<TransportFlights.Flight> findDirectFlights(final Journeys.Journey journey, final Set<Integer> fromAirportIds, final Set<Integer> toAirportIds, final RouteIndex routes) {
        return routes.departingFrom(fromAirportIds, journey).stream()
                .filter(tfm -> isThereDirectRouteAvailable(tfm, fromAirportIds, toAirportIds))
                .map(tfm -> tfm.tf)
                .toList();
    }

    private static Collection<List<TransportFlights.Flight>> findStopoverRoutes(final Journeys.Journey journey, final Set<Integer> fromAirportIds, final Set<Integer> toAirportIds, final RouteIndex routes) {
        final Collection<List<TransportFlights.Flight>> result = new ArrayList<>();

        final Collection<TFM> allFlight1s = routes.departingFrom(fromAirportIds, journey).stream()
                .filter(tfm -> isThereEnoughTickets(journey, tfm.tf))
                .toList();

        for (final TFM flight1 : allFlight1s) {
            final Collection<TFM> allFlight2s = routes.departingFrom(Collections.singleton(flight1.fm.getDestinationAirportId()), journey).stream()
                    .filter(tfm -> isThereEnoughTickets(journey, tfm.tf)
                            && flightArrivesTo(tfm, toAirportIds)
                            && flightDepartsLaterThan(tfm, flight1.fm.getPlannedArrivalWorldTime() + STOPOVER_CONNECTION_RESERVE)
                    ).toList();
            allFlight2s.forEach(flight2 -> result.add(Arrays.asList(flight1.tf, flight2.tf)));
        }

        return result;
    }

    /**
     * The flight to a hub for a journey which has no direct flight and no stopover route: a flight from the airports of the city where the journey is now, which has seats of the class of the journey, and goes to a hub (AirportHubs) which is closer to the destination of the journey than the
     * city where the journey is now. The criterion is the same as of ShadowJetLogic.findHubTestJourney, but from the side of the journey: of all such flights the one which
     * cuts the distance to the destination most is taken, of equal ones (to the same hub) a random one. The distance only gets smaller, so a journey cannot go in circles between the hubs.
     */
    static Optional<TransportFlights.Flight> findHubFlight(final World world, final Journeys.Journey journey, final Set<Integer> fromAirportIds, final RouteIndex routes) {
        final Geo.Coords locationCoords;
        final Geo.Coords targetCoords;
        try {
            locationCoords = world.cities().byId(journey.getLocationCityId()).orElseThrow().getCoords();
            targetCoords = world.cities().byId(journey.getToCityId()).orElseThrow().getCoords();
        } catch (final RuntimeException e) {
            log.warn("j/y #{} - failed to get the coordinates of the cities, no hub search", journey.getId(), e);
            return Optional.empty();
        }
        final double distanceNow = Geo.distance(locationCoords, targetCoords);

        // the flights which cut the distance most (the flights to the same hub are equal), the journey takes one of them at random
        final List<TransportFlights.Flight> best = new ArrayList<>();
        double bestReduction = 0;
        for (final TFM tfm : routes.departingFrom(fromAirportIds, journey)) {
            final Optional<Airports.Airport> hub = world.airports().byId(tfm.fm.getDestinationAirportId());
            if (hub.isEmpty() || !hub.get().isHub()) {
                continue;
            }
            final double reduction = distanceNow - Geo.distance(hub.get().getCoords(), targetCoords);
            if (reduction <= 0) {
                continue;
            }
            if (best.isEmpty() || reduction > bestReduction) {
                best.clear();
                best.add(tfm.tf);
                bestReduction = reduction;
            } else if (reduction == bestReduction) {
                best.add(tfm.tf);
            }
        }
        return best.isEmpty() ? Optional.empty() : Optional.of(pickRandom(best));
    }

    // Uniform choice: the flights found at the same moment (announced together) are filled evenly, no flight has the priority by its id or its departure time
    // (decided 2026-11-03, before that the first flight in the order of ids took all the journeys until it was full). Tools.random(list) is not used: it gives the first and the last element half of the chance.
    static <T> T pickRandom(final Collection<T> items) {
        final int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(items.size());
        return items instanceof List<T> list ? list.get(index) : items.stream().skip(index).findFirst().orElseThrow();
    }

    private static boolean flightDepartsLaterThan(TFM tfm, int departureTimeThreshold) {
        return tfm.fm.getPlannedDepartureWorldTime() >= departureTimeThreshold;
    }

    private static boolean flightArrivesTo(TFM tfm, Set<Integer> toAirportIds) {
        return toAirportIds.contains(tfm.fm.getDestinationAirportId());
    }

    private record TFM(TransportFlights.Flight tf, FlightMissions.Mission fm) {
    }

    private static TFM toTfm(final World world, final TransportFlights.Flight tf) {
        return new TFM(tf, world.flightMissions()
                .byId(tf.getFlightMissionId())
                .orElseThrow(() -> new NoSuchElementException("Unable to find F/M # " + tf.getFlightMissionId() + " for T/F " + tf)));
    }

    // The class of the ticket: the search finds a flight which has seats in the preferred class or one class up or one class down (TransportFlights.enoughSeatsAvailable,
    // CabinLayout.chooseService), the journey is booked in the class which has the seats (chooseCabinService) and the actual class is kept in 'booked cabin service'.
    // Known limitation: it is so for the direct flights and the flights to a hub only. bookStopoverFlightsJourneyNoChecks books both flights in the preferred class, so the
    // stopover routes are looked for only among the flights which have seats in the preferred class (isThereEnoughTickets). The stopover routes are for the network of
    // scheduled flights, and they are switched off (or almost off).
    // the class in which the journey flies on the flight (the search has found the flight, so there is one)
    private static CabinLayout.Service chooseCabinService(final Journeys.Journey journey, final TransportFlights.Flight flight) {
        return flight.getRemainedTickets().chooseService(journey.getGroupSize(), journey.getPreferredCabinService());
    }

    private static boolean isThereEnoughTickets(final Journeys.Journey journey, final TransportFlights.Flight tf) {
        return tf.getRemainedTickets().get(journey.getPreferredCabinService()) >= journey.getGroupSize();
    }

    private static boolean isThereDirectRouteAvailable(TFM tfm, Set<Integer> fromAirportIds, Set<Integer> toAirportIds) {
        return fromAirportIds.contains(tfm.fm.getDepartureAirportId()) && toAirportIds.contains(tfm.fm.getDestinationAirportId());
    }

    private static void waitingForCheckin(final World world, final Journeys.Journey journey) {
        final Optional<TransportFlights.Flight> flight = world.transportFlights().byId(journey.getTransportFlight1Id());
        if (flight.isEmpty()) {
            log.warn("j/y #{} - t/f #{} not found, journey is waiting for check-in, forceful return", journey.getId(), journey.getTransportFlight1Id());
            world.journeyControl().resetJourneyForcefully(journey);
            FlightStats.event("journey - waiting for check-in - no TF found");
        } else if (TransportFlightHelper.flightStatusBeforeCheckin(flight.get().getStatus())) {
            final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flight.get().getFlightMissionId());
            if (mission.isEmpty()) {
                log.warn("j/y #{} - t/f #{} has no mission, journey is waiting for check-in, forceful return", journey.getId(), flight.get().getId());
                world.journeyControl().resetJourneyForcefully(journey);
                FlightStats.event("journey - waiting for check-in - no FM found");
                return;
            }
            journey.setHeartbeatTime(Math.max(
                    TransportFlightHelper.calcCheckinStartTime(mission.get()) + (int) (0.8 * Math.random() * TransportFlightHelper.CHECKIN_DURATION),
                    world.getWorldTime() + 5 * Time.ONE_MINUTE));
        } else if (TransportFlightHelper.flightStatusAllowsToCheckIn(flight.get().getStatus())) {
            world.journeyControl().checkin(journey);
        } else { // checkin & boarding finished -> journey is too late
            world.journeyControl().tooLateToBoard(journey);
        }
    }

    private static void waitingForBoarding(final World world, final Journeys.Journey journey) {
        // most of the logic is in pax manager, here the journey only watches that the boarding is not over yet
        final Optional<TransportFlights.Flight> flight = world.transportFlights().byId(journey.getTransportFlight1Id());
        if (flight.isEmpty()) {
            log.warn("j/y #{} - t/f #{} not found, journey is waiting for boarding, forceful return", journey.getId(), journey.getTransportFlight1Id());
            world.journeyControl().resetJourneyForcefully(journey);
            FlightStats.event("journey - waiting for boarding - no TF found");
        } else if (TransportFlightHelper.flightStatusAllowsToBoard(flight.get().getStatus())) {
            journey.setHeartbeatTime(world.getWorldTime() + Time.ONE_MINUTE);
        } else { // checkin & boarding finished -> journey is too late
            world.journeyControl().tooLateToBoard(journey);
        }
    }

    private static void waitingForDeboarding(final World world, final Journeys.Journey journey) {
        final Optional<TransportFlights.Flight> flight = world.transportFlights().byId(journey.getTransportFlight1Id());
        if (flight.isEmpty()) {
            // the transport flight is lost, the journey is returned back to the city where it was before the flight
            log.warn("j/y #{} - t/f #{} not found, journey is waiting for deboarding, forceful return", journey.getId(), journey.getTransportFlight1Id());
            world.journeyControl().resetJourneyForcefully(journey);
            FlightStats.event("journey - waiting for deboarding - no TF found");
        } else if (flight.get().getStatus() == TransportFlights.Status.Deboarding) {
            // the flight lets the journeys out (PaxManager, deboarding counter), the journey only keeps watching the flight
            journey.setHeartbeatTime(world.getWorldTime() + PaxManager.Deboarding.JOURNEY_WATCH_INTERVAL);
        } else if (flight.get().getStatus() == TransportFlights.Status.Finished) {
            // the flight has been finished before the deboarding counter reached the journey (the window is over), so it deboards anyway
            log.warn("j/y #{} - t/f #{} is already finished, deboarding", journey.getId(), flight.get().getId());
            world.journeyControl().deboard(journey);
        } else {
            // not expected status, we do not understand what it is, so the journey is orphaned (no reset, PAX on board is not touched)
            log.warn("j/y #{} - t/f #{} is in {} status, journey is waiting for deboarding, orphaned", journey.getId(), flight.get().getId(), flight.get().getStatus());
            FlightStats.event("journey - waiting for deboarding - unexpected TF status");
            world.journeyControl().orphaned(journey);
        }
    }

    private static void justArrived(final World world, final Journeys.Journey journey) {
        world.journeyControl().justArrived(journey);
    }

    private static void itinerariesDone(final World world, final Journeys.Journey journey) {
        if (!world.journeyControl().isToCityReached(journey)) {
            // the pilot asked to leave at the next landing: the journey ends here
            if (world.journeyControl().leavePilotAtLandingIfAsked(journey)) {
                return;
            }
            world.journeyControl().toCityNotReached(journey);
            return;
        }

        if (journey.isPilotJourney()) {
            world.journeyControl().finishPilotJourney(journey);
            return;
        }

        if (journey.isReturningBack()) {
            world.journeyControl().finish(journey);
        } else {
            world.journeyControl().startSpendingTheirTime(journey);
        }
    }

    private static void switchToReturnTrip(final World world, final Journeys.Journey journey) {
        world.journeyControl().switchToReturnTrip(journey);
    }

    private static void cleanup(final World world, final Journeys.Journey journey) {
        world.journeys().deleteById(journey.getId());
    }
}
