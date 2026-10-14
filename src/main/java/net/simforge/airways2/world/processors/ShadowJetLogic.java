package net.simforge.airways2.world.processors;

import com.google.common.collect.Sets;
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.simforge.airways2.storage.Storage.Condition.and;

public class ShadowJetLogic {
    private static final Logger log = LoggerFactory.getLogger(ShadowJetLogic.class);
    private static final Random random = new Random();

    // todo ak2 hubs are hardcoded for the hub test, later should be calculated from flight stats
    private static final Set<String> HUB_ICAOS = Set.of("EGLL", "EDDF", "EHAM", "KJFK", "OMDB");

    public static Aircrafts.Aircraft findAvailableAircraftOrCreateNew(
            final World world,
            final AircraftTypes.AircraftType aircraftType,
            final Airports.Airport locationAirport) {
        final Optional<Aircrafts.Aircraft> existingAircraft = world.aircrafts()
                .byAircraftOperatorIdAndIdleAndParkedAtAirport(World25.ShadowJetOperatorId)
                .filter(a -> a.getAircraftTypeId() == aircraftType.getId())
                .min(Comparator.comparing(a -> Geo.distance(locationAirport.getCoords(), a.getLocationCoords())));

        if (existingAircraft.isPresent()) {
            if (existingAircraft.get().getLocationAirportId() != locationAirport.getId()) {
                log.info("Fleet - moving a/c #{}, {}, reg no {} located at {} to {}",
                        existingAircraft.get().getId(), aircraftType.getIcao(), existingAircraft.get().getRegNo(),
                        world.airports().byId(existingAircraft.get().getLocationAirportId()).orElseThrow().getIcao(),
                        locationAirport.getIcao());
                FlightStats.event("shadowJet moving");
                AircraftHelper.moveParkedAircraftToAnotherAirport(world, existingAircraft.get(), locationAirport);
            } else {
                log.info("Fleet - selecting a/c #{}, {}, reg no {} located at {}",
                        existingAircraft.get().getId(), aircraftType.getIcao(), existingAircraft.get().getRegNo(),
                        locationAirport.getIcao());
                FlightStats.event("shadowJet selecting");
            }

            return existingAircraft.get();
        }

        String newRegNo = null;
        for (int i = 0; i < 100; i++) {
            final String regNo = generateRandomShadowJetRegNo();
            final Optional<Aircrafts.Aircraft> aircraftByRegNo = world.aircrafts().byRegNo(regNo);
            if (aircraftByRegNo.isEmpty()) {
                newRegNo = regNo;
                break;
            }
        }

        if (newRegNo == null) {
            log.error("Fleet - could not find non-occupied SJ-xxx reg no");
            throw new IllegalStateException("ShadowJet Fleet - could not find non-occupied SJ-xxx reg no");
        }

        final Aircrafts.Aircraft newAircraft = world.aircrafts().create(aircraftType, newRegNo, locationAirport);
        newAircraft.setAircraftOperatorId(World25.ShadowJetOperatorId);
        log.info("Fleet - creating a/c #{}, {}, reg no {} at {}", newAircraft.getId(), aircraftType.getIcao(), newRegNo, locationAirport.getIcao());
        FlightStats.event("shadowJet creating");
        return newAircraft;
    }

    private static String generateRandomShadowJetRegNo() {
        final String suffix = random.ints(3, 'A', 'Z' + 1)
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());

        return "SJ-" + suffix;
    }

    public static void provideTransportFlightIfRequired(World world, FlightMissions.Mission mission) {
        String from = world.airports().getIcao(mission.getDepartureAirportId()).orElseThrow();
        String to = world.airports().getIcao(mission.getDestinationAirportId()).orElseThrow();
        String route = from + "-" + to;

        if (from.equals(to)) {
            log.warn("T/f prov - f/m #{} - {} - from equals to, route not allowed, SKIP", mission.getId(), route);
            return;
        }

        Set<Integer> fromCitiesId = world.airport2city().allByAirportId(mission.getDepartureAirportId()).map(Airport2City.Link::getCityId).collect(Collectors.toSet());
        Set<Integer> toCitiesId = world.airport2city().allByAirportId(mission.getDestinationAirportId()).map(Airport2City.Link::getCityId).collect(Collectors.toSet());

        Sets.SetView<Integer> intersection = Sets.intersection(fromCitiesId, toCitiesId);
        if (!intersection.isEmpty()) {
            log.warn("T/f prov - f/m #{} - {} - intersection {} detected, SKIP", mission.getId(), route, intersection);
            return;
        }

        TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(mission);
        world.c2cFlowControl().updateSuccessRate(transportFlight, 0.1f);

        world.transportFlightControl().startCheckIn(transportFlight);

        int journeyBooked = 0;
        int paxBooked = 0;

        double loadFactor = Tools.random(40, 60) / 100.0;
        int consideredAvgPaxPerJourney = 5;
        int maxCount = (int) ((transportFlight.getTotalTickets().getTotal() * loadFactor) / consideredAvgPaxPerJourney);

        List<Journeys.Journey> collected = new ArrayList<>();

        CabinLayout remained = transportFlight.getRemainedTickets();

        Stream<Journeys.Journey> journeyStream = world.journeys()
                .filter(world.journeys().byStatus(Journeys.Status.LookingForTickets))
                .filter(j -> fromCitiesId.contains(j.getLocationCityId())
                        && toCitiesId.contains(j.getToCityId()));
        Iterator<Journeys.Journey> it = journeyStream.iterator();
        Map<Integer, CabinLayout.Service> actualCabinServices = new HashMap<>();
        while (it.hasNext()
                && (remained.getTotal() > 0)
                && (collected.size() < maxCount)) {
            Journeys.Journey journey = it.next();

            final Seating seating = trySeat(remained, journey);
            if (seating == null) {
                continue;
            }

            actualCabinServices.put(journey.getId(), seating.service());
            collected.add(journey);
            remained = seating.remained();
        }
        log.info("T/f prov - f/m #{}, t/f #{} - Selected load factor {}, Remained seats {}, Found journeys: {}", mission.getId(), transportFlight.getId(), loadFactor, remained, collected.stream().map(Journeys.Journey::getId).toList());

        // hub test - exactly one journey which does not have a direct flight here, but gets closer to its destination via the hub
        final HubTestCandidate hubTest = findHubTestJourney(world, mission, transportFlight, fromCitiesId, toCitiesId, collected, remained);
        if (hubTest != null) {
            actualCabinServices.put(hubTest.journey().getId(), hubTest.seating().service());
            collected.add(hubTest.journey());
            remained = hubTest.seating().remained();
        }

        // some journeys to ping 'looking for tickets' processing randomly distributed in next 5 minutes
        int someToAwake = Tools.random(10, 20);
        int awaken = 0;
        for (int i = 0; i < someToAwake; i++) {
            if (!it.hasNext()) {
                break;
            }
            Journeys.Journey journey = it.next();
            journey.setHeartbeatTime(world.getWorldTime() + Tools.random(0, 5 * Time.ONE_MINUTE));
            awaken++;
        }
        log.info("T/f prov - f/m #{}, t/f #{} - Awaken {} journeys", mission.getId(), transportFlight.getId(), awaken);

        for (Journeys.Journey journey : collected) {
            world.journeyControl().bookDirectFlightJourneyNoChecks(journey, actualCabinServices.get(journey.getId()), transportFlight);
            world.journeyControl().waitForCheckin(journey);
            world.journeyControl().checkin(journey);

            journeyBooked++;
            paxBooked += journey.getGroupSize();

            log.info("T/f prov - f/m #{}, t/f #{} - Journey {} booked to the flight and checked-in", mission.getId(), transportFlight.getId(), journey);
            if (hubTest != null && hubTest.journey().getId() == journey.getId()) {
                log.info("hub test - f/m #{}, t/f #{} - j/y #{} booked to the flight and checked-in", mission.getId(), transportFlight.getId(), journey.getId());
            }
        }

        world.transportFlightControl().startBoarding(transportFlight);
        log.info("T/f prov - f/m #{}, t/f #{} - {} journeys / {} PAX booked and checked-in, boarding started, DONE", mission.getId(), transportFlight.getId(), journeyBooked, paxBooked);
    }

    private record Seating(CabinLayout remained, CabinLayout.Service service) {
    }

    // returns null if there are no seats for the journey even with one class upgrade/downgrade
    private static Seating trySeat(final CabinLayout remained, final Journeys.Journey journey) {
        final CabinLayout.Service cabinService = journey.getPreferredCabinService();
        final int groupSize = journey.getGroupSize();
        if (remained.hasEnoughSeats(groupSize, cabinService)) {
            return new Seating(remained.occupySeats(groupSize, cabinService), cabinService);
        } else if (cabinService.upgradeAvailable() && remained.hasEnoughSeats(groupSize, cabinService.upgradedService())) {
            return new Seating(remained.occupySeats(groupSize, cabinService.upgradedService()), cabinService.upgradedService());
        } else if (cabinService.downgradeAvailable() && remained.hasEnoughSeats(groupSize, cabinService.downgradedService())) {
            return new Seating(remained.occupySeats(groupSize, cabinService.downgradedService()), cabinService.downgradedService());
        }
        return null;
    }

    private record HubTestCandidate(Journeys.Journey journey, Seating seating) {
    }

    /**
     * 'Journeys via hubs' test. If the flight goes to a hub, finds exactly one looking-for-tickets journey which is located
     * at the departure cities, has no direct flight here, and gets closer to its destination after flying to the hub.
     * The journey which gets the biggest distance reduction is chosen. It is booked and checked-in together with the direct
     * journeys. After landing at the hub it goes through the regular itineraries-done logic (TO city not reached ->
     * looking for tickets again). All hub test lines in the log start with 'hub test - '.
     */
    private static HubTestCandidate findHubTestJourney(final World world,
                                                       final FlightMissions.Mission mission,
                                                       final TransportFlights.Flight transportFlight,
                                                       final Set<Integer> fromCitiesId,
                                                       final Set<Integer> toCitiesId,
                                                       final Collection<Journeys.Journey> alreadyCollected,
                                                       final CabinLayout remained) {
        try {
            final Airports.Airport hubAirport = world.airports().byId(mission.getDestinationAirportId()).orElseThrow();
            final String hubIcao = hubAirport.getIcao();
            if (!HUB_ICAOS.contains(hubIcao)) {
                return null;
            }

            final String departureIcao = world.airports().byId(mission.getDepartureAirportId()).orElseThrow().getIcao();
            final String aircraftTypeIcao = world.aircrafts().byId(mission.getAircraftId())
                    .flatMap(a -> world.aircraftTypes().byId(a.getAircraftTypeId()))
                    .map(AircraftTypes.AircraftType::getIcao)
                    .orElse("?");

            final Set<Integer> collectedIds = alreadyCollected.stream().map(Journeys.Journey::getId).collect(Collectors.toSet());
            final Geo.Coords hubCoords = hubAirport.getCoords();
            final Map<Integer, Geo.Coords> cityCoordsCache = new HashMap<>();

            int closer = 0;
            int notCloser = 0;
            Journeys.Journey best = null;
            Seating bestSeating = null;
            double bestReduction = 0;
            double bestDistanceNow = 0;
            double bestDistanceFromHub = 0;

            final Iterator<Journeys.Journey> it = world.journeys()
                    .filter(world.journeys().byStatus(Journeys.Status.LookingForTickets))
                    .iterator();
            while (it.hasNext()) {
                final Journeys.Journey journey = it.next();

                // only journeys which are located at the cities of departure airport are able to take this flight
                final int locationCityId = journey.getLocationCityId();
                if (!fromCitiesId.contains(locationCityId)) {
                    continue;
                }

                // journeys heading to the hub cities have a direct flight here, they are handled by the regular logic
                // and are not counted at all, otherwise they would always look like 'getting closer'
                if (toCitiesId.contains(journey.getToCityId()) || collectedIds.contains(journey.getId())) {
                    continue;
                }

                final Geo.Coords locationCoords = cityCoordsCache.computeIfAbsent(locationCityId,
                        id -> world.cities().byId(id).orElseThrow().getCoords());
                final Geo.Coords targetCoords = cityCoordsCache.computeIfAbsent(journey.getToCityId(),
                        id -> world.cities().byId(id).orElseThrow().getCoords());

                final double distanceNow = Geo.distance(locationCoords, targetCoords);
                final double distanceFromHub = Geo.distance(hubCoords, targetCoords);

                if (distanceFromHub >= distanceNow) {
                    notCloser++;
                    continue;
                }
                closer++;

                final double reduction = distanceNow - distanceFromHub;
                if (best != null && reduction <= bestReduction) {
                    continue;
                }

                final Seating seating = trySeat(remained, journey);
                if (seating == null) {
                    continue;
                }

                best = journey;
                bestSeating = seating;
                bestReduction = reduction;
                bestDistanceNow = distanceNow;
                bestDistanceFromHub = distanceFromHub;
            }

            log.info("hub test - f/m #{}, t/f #{} - {} -> {}, {} - looking-for-tickets journeys getting closer to destination: {}, not getting closer: {}",
                    mission.getId(), transportFlight.getId(), departureIcao, hubIcao, aircraftTypeIcao, closer, notCloser);

            if (best == null) {
                log.info("hub test - f/m #{}, t/f #{} - {} -> {} - no suitable journey found (no candidate or no seats)",
                        mission.getId(), transportFlight.getId(), departureIcao, hubIcao);
                return null;
            }

            log.info("hub test - f/m #{}, t/f #{} - {} -> {} - j/y #{} selected, g/s {}, cabin {}, located at {}, target {}, distance to target {} nm now, {} nm from the hub",
                    mission.getId(), transportFlight.getId(), departureIcao, hubIcao, best.getId(), best.getGroupSize(), bestSeating.service(),
                    world.cities().byId(best.getLocationCityId()).orElseThrow().getName(),
                    world.cities().byId(best.getToCityId()).orElseThrow().getName(),
                    Math.round(bestDistanceNow), Math.round(bestDistanceFromHub));

            return new HubTestCandidate(best, bestSeating);
        } catch (final Exception e) {
            log.warn("hub test - f/m #{} - failed", mission.getId(), e);
            return null;
        }
    }

    public static void cancelTransportFlightIfExists(World world, FlightMissions.Mission mission) {
        Optional<TransportFlights.Flight> transportFlightO = world.transportFlights().byFlightMissionId(mission.getId());
        if (transportFlightO.isEmpty()) {
            return;
        }

        TransportFlights.Flight transportFlight = transportFlightO.get();
        world.transportFlightControl().unloadJourneysForcefullyFromActiveFlight(transportFlight);
        transportFlight.setStatus(TransportFlights.Status.Cancelled);

        log.warn("f/m #{}, t/f #{}, PAX {} - flight cancelled, all PAX unloaded", mission.getId(), transportFlight.getId(), transportFlight.getPaxOnBoard());
    }

    public static void deboardTransportFlightIfExists(World world, FlightMissions.Mission mission) {
        Optional<TransportFlights.Flight> transportFlightO = world.transportFlights().byFlightMissionId(mission.getId());
        if (transportFlightO.isEmpty()) {
            return;
        }

        TransportFlights.Flight transportFlight = transportFlightO.get();
        world.transportFlightControl().startDeboarding(transportFlight);
        log.warn("f/m #{}, t/f #{} - deboarding started", mission.getId(), transportFlight.getId());
    }
}
