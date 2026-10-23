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

import static net.simforge.airways2.storage.Storage.Condition.and;


public class ShadowJetLogic {
    private static final Logger log = LoggerFactory.getLogger(ShadowJetLogic.class);
    private static final Random random = new Random();

    // the most active airports are shown in the log regularly, the stats are the flights of the last 8 days
    private static final int ACTIVE_AIRPORTS_LOG_PERIOD = Time.ONE_HOUR;
    private static final int ACTIVE_AIRPORTS_LOG_LIMIT = 25;
    private static int activeAirportsLoggedAt = Integer.MIN_VALUE;

    // todo ak1 hubs are hardcoded for the hub test, later should be calculated from flight stats, OR additional flag on Airport entity
    private static final Set<String> HUB_ICAOS = Set.of("EGLL", "EGKK", "EDDF", "EDDM", "EHAM", "KJFK", "KEWR", "KLGA", "KMIA", "KATL", "KLAX", "KSFO", "LFPG", "LSZH", "LOWW", "LGAV", "OMDB", "OMAA", "RJAA", "WSSS", "YSSY");

    // the flight is filled up to a random part of its seats
    private static final int MIN_FILL_PERCENT = 70;
    private static final int MAX_FILL_PERCENT = 100;
    // journeys are taken from a random sample of the candidates
    private static final int CANDIDATES_SAMPLE_SIZE = 500;
    // The selected journeys are booked at once, but they check-in at different times: a part checks-in at once (the first
    // group), the others get a random check-in time inside the interval of their group. The flight gets the planned fill
    // only if it stays at the gate long enough, all the check-ins happen during CHECKIN_GROUPS_MAX_MINUTES.
    // percent of the selected journeys, check-in interval start and end in minutes since the provision of the flight
    private record CheckInGroup(int percent, int fromMinutes, int toMinutes) {
    }

    private static final List<CheckInGroup> CHECKIN_GROUPS = List.of(
            new CheckInGroup(30, 0, 0), // checked-in at once
            new CheckInGroup(30, 0, 5),
            new CheckInGroup(20, 5, 10),
            new CheckInGroup(20, 10, 20));

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

    /**
     * Airports with the most flights (departures and arrivals) of the last 8 days, ICAO to the count, the busiest first.
     */
    public static Map<String, Integer> mostActiveAirports(final World world, final int limit) {
        final Map<Integer, Integer> flightsByAirportId = new HashMap<>();
        world.airport2airportDailyFlightStats().all().forEach(stats -> {
            final int count = stats.getTotalCount();
            flightsByAirportId.merge(stats.getFromAirportId(), count, Integer::sum);
            flightsByAirportId.merge(stats.getToAirportId(), count, Integer::sum);
        });

        final Map<String, Integer> result = new LinkedHashMap<>();
        flightsByAirportId.entrySet().stream()
                .map(e -> Map.entry(world.airports().getIcao(e.getKey()).orElse("#" + e.getKey()), e.getValue()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private static void logMostActiveAirportsRegularly(final World world) {
        final int now = world.getWorldTime();
        if (activeAirportsLoggedAt != Integer.MIN_VALUE && now >= activeAirportsLoggedAt && now - activeAirportsLoggedAt < ACTIVE_AIRPORTS_LOG_PERIOD) {
            return;
        }
        activeAirportsLoggedAt = now;

        try {
            log.info("Stats - most active airports by flights of the last 8 days: {}", mostActiveAirports(world, ACTIVE_AIRPORTS_LOG_LIMIT));
        } catch (final RuntimeException e) {
            log.warn("Stats - unable to calculate most active airports", e);
        }
    }

    public static void provideTransportFlightIfRequired(World world, FlightMissions.Mission mission) {
        logMostActiveAirportsRegularly(world);

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

        final TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(mission);
        world.c2cFlowControl().updateSuccessRate(transportFlight, 0.1f);

        world.transportFlightControl().startCheckIn(transportFlight);

        // The flight is filled up to a random part of its seats. Direct journeys and journeys of the hub test live in one
        // pool: a random sample of the candidates is taken, then the journeys are seated in the random order.
        final int fillPercent = Tools.random(MIN_FILL_PERCENT, MAX_FILL_PERCENT);
        final int targetPax = transportFlight.getTotalTickets().getTotal() * fillPercent / 100;

        final HubInfo hub = findHubInfo(world, mission);
        final CandidatePool pool = collectCandidates(world, fromCitiesId, toCitiesId, hub);

        final List<Candidate> selected = new ArrayList<>();
        final Map<Integer, CabinLayout.Service> actualCabinServices = new HashMap<>();
        CabinLayout remained = transportFlight.getRemainedTickets();
        int paxPlanned = 0;
        for (final Candidate candidate : pool.sample()) {
            final Journeys.Journey journey = candidate.journey();
            if (paxPlanned + journey.getGroupSize() > targetPax) {
                continue;
            }

            final Seating seating = trySeat(remained, journey);
            if (seating == null) {
                continue;
            }

            actualCabinServices.put(journey.getId(), seating.service());
            selected.add(candidate);
            remained = seating.remained();
            paxPlanned += journey.getGroupSize();
        }
        log.info("T/f prov - f/m #{}, t/f #{} - Selected fill {}%, target PAX {}, planned PAX {}, Remained seats {}, candidates {} (direct {}, hub {}), sample {}, selected {} (direct {}, hub {}): {}",
                mission.getId(), transportFlight.getId(), fillPercent, targetPax, paxPlanned, remained,
                pool.candidatesCount(), pool.directCount(), pool.hubCount(), pool.sample().size(),
                selected.size(), selected.stream().filter(c -> c.kind() == CandidateKind.DIRECT).count(),
                selected.stream().filter(c -> c.kind() == CandidateKind.HUB).count(),
                selected.stream().map(c -> "j/y #" + c.journey().getId()).toList());

        if (hub != null) {
            logHubSelection(world, mission, transportFlight, hub, pool, selected, actualCabinServices);
        }

        // all the selected journeys are booked, but they check-in at different times, see CHECKIN_GROUPS
        Collections.shuffle(selected, random);

        int journeyCheckedIn = 0;
        int paxCheckedIn = 0;
        int journeyDelayed = 0;
        int paxDelayed = 0;
        int percentFrom = 0;
        for (final CheckInGroup group : CHECKIN_GROUPS) {
            final int percentTo = percentFrom + group.percent();
            // the borders are rounded the same way for the neighbour groups, so all the selected journeys get to a group
            final int firstIndex = (int) Math.round(selected.size() * percentFrom / 100.0);
            final int lastIndex = (int) Math.round(selected.size() * percentTo / 100.0);
            percentFrom = percentTo;

            for (int i = firstIndex; i < lastIndex; i++) {
                final Journeys.Journey journey = selected.get(i).journey();
                final boolean hubJourney = selected.get(i).kind() == CandidateKind.HUB;

                world.journeyControl().bookDirectFlightJourneyNoChecks(journey, actualCabinServices.get(journey.getId()), transportFlight);
                world.journeyControl().waitForCheckin(journey);

                if (group.toMinutes() == 0) {
                    world.journeyControl().checkin(journey);
                    journeyCheckedIn++;
                    paxCheckedIn += journey.getGroupSize();

                    log.info("T/f prov - f/m #{}, t/f #{} - j/y #{} ({}) booked to the flight and checked-in", mission.getId(), transportFlight.getId(), journey.getId(), hubJourney ? "hub test" : "direct");
                } else {
                    journey.setHeartbeatTime(world.getWorldTime() + Tools.random(group.fromMinutes() * Time.ONE_MINUTE, group.toMinutes() * Time.ONE_MINUTE));
                    journeyDelayed++;
                    paxDelayed += journey.getGroupSize();

                    log.info("T/f prov - f/m #{}, t/f #{} - j/y #{} ({}) booked to the flight, check-in later at {}", mission.getId(), transportFlight.getId(), journey.getId(), Time.toLdt(journey.getHeartbeatTime()), hubJourney ? "hub test" : "direct");
                }
            }
        }

        world.transportFlightControl().startBoarding(transportFlight);
        log.info("T/f prov - f/m #{}, t/f #{} - {} journeys / {} PAX booked and checked-in, {} journeys / {} PAX booked and will check-in later, boarding started, DONE",
                mission.getId(), transportFlight.getId(), journeyCheckedIn, paxCheckedIn, journeyDelayed, paxDelayed);
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

    private enum CandidateKind {
        DIRECT, HUB
    }

    private record Candidate(Journeys.Journey journey, CandidateKind kind, double distanceNow, double distanceFromHub) {
    }

    private record HubInfo(Airports.Airport airport, String icao, String departureIcao, String aircraftTypeIcao) {
    }

    private record CandidatePool(List<Candidate> sample, int candidatesCount, int directCount, int hubCount,
                                 int closerCount, int notCloserCount) {
    }

    // not null if the flight goes to a hub, then the journeys which getting closer to their destinations are candidates too
    private static HubInfo findHubInfo(final World world, final FlightMissions.Mission mission) {
        final Airports.Airport hubAirport = world.airports().byId(mission.getDestinationAirportId()).orElseThrow();
        if (!HUB_ICAOS.contains(hubAirport.getIcao())) {
            return null;
        }

        final String departureIcao = world.airports().byId(mission.getDepartureAirportId()).orElseThrow().getIcao();
        final String aircraftTypeIcao = world.aircrafts().byId(mission.getAircraftId())
                .flatMap(a -> world.aircraftTypes().byId(a.getAircraftTypeId()))
                .map(AircraftTypes.AircraftType::getIcao)
                .orElse("?");
        return new HubInfo(hubAirport, hubAirport.getIcao(), departureIcao, aircraftTypeIcao);
    }

    /**
     * Looking-for-tickets journeys which are located at the departure cities and are able to take the flight:
     * direct journeys (their destination is served by the arrival airport) and, if the flight goes to a hub, journeys which
     * have no direct flight here but get closer to their destination via the hub ('journeys via hubs' test).
     * Both kinds are in one pool; the first CANDIDATES_SAMPLE_SIZE candidates are taken and returned in a random order (the counters count only the scanned journeys).
     */
    private static CandidatePool collectCandidates(final World world,
                                                   final Set<Integer> fromCitiesId,
                                                   final Set<Integer> toCitiesId,
                                                   final HubInfo hub) {
        final List<Candidate> sample = new ArrayList<>();
        final Map<Integer, Geo.Coords> cityCoordsCache = new HashMap<>();
        int direct = 0;
        int hubCount = 0;
        int closer = 0;
        int notCloser = 0;

        final Iterator<Journeys.Journey> it = world.journeys()
                .filter(and(world.journeys().byStatus(Journeys.Status.LookingForTickets), world.journeys().byLocationCityIdIn(fromCitiesId)))
                .iterator();
        while (it.hasNext()) {
            final Journeys.Journey journey = it.next();
            final int locationCityId = journey.getLocationCityId();

            final Candidate candidate;
            if (toCitiesId.contains(journey.getToCityId())) {
                candidate = new Candidate(journey, CandidateKind.DIRECT, 0, 0);
                direct++;
            } else if (hub != null) {
                final double distanceNow;
                final double distanceFromHub;
                try {
                    final Geo.Coords locationCoords = cityCoordsCache.computeIfAbsent(locationCityId,
                            id -> world.cities().byId(id).orElseThrow().getCoords());
                    final Geo.Coords targetCoords = cityCoordsCache.computeIfAbsent(journey.getToCityId(),
                            id -> world.cities().byId(id).orElseThrow().getCoords());
                    distanceNow = Geo.distance(locationCoords, targetCoords);
                    distanceFromHub = Geo.distance(hub.airport().getCoords(), targetCoords);
                } catch (final RuntimeException e) {
                    log.warn("hub test - j/y #{} - failed to calculate distances", journey.getId(), e);
                    continue;
                }

                if (distanceFromHub >= distanceNow) {
                    notCloser++;
                    continue;
                }
                closer++;
                candidate = new Candidate(journey, CandidateKind.HUB, distanceNow, distanceFromHub);
                hubCount++;
            } else {
                continue;
            }

            // the first CANDIDATES_SAMPLE_SIZE candidates are enough, they are shuffled below
            sample.add(candidate);
            if (sample.size() >= CANDIDATES_SAMPLE_SIZE) {
                break;
            }
        }

        Collections.shuffle(sample, random);
        return new CandidatePool(sample, sample.size(), direct, hubCount, closer, notCloser);
    }

    // hub test statistics, the lines start with 'hub test - ', see docs/journeys-hub-mode.md
    private static void logHubSelection(final World world,
                                        final FlightMissions.Mission mission,
                                        final TransportFlights.Flight transportFlight,
                                        final HubInfo hub,
                                        final CandidatePool pool,
                                        final List<Candidate> selected,
                                        final Map<Integer, CabinLayout.Service> actualCabinServices) {
        log.info("hub test - f/m #{}, t/f #{} - {} -> {}, {} - looking-for-tickets journeys getting closer to destination: {}, not getting closer: {}",
                mission.getId(), transportFlight.getId(), hub.departureIcao(), hub.icao(), hub.aircraftTypeIcao(), pool.closerCount(), pool.notCloserCount());

        final List<Candidate> hubSelected = selected.stream().filter(c -> c.kind() == CandidateKind.HUB).toList();
        if (hubSelected.isEmpty()) {
            log.info("hub test - f/m #{}, t/f #{} - {} -> {} - no suitable journey found (no candidate or no seats)",
                    mission.getId(), transportFlight.getId(), hub.departureIcao(), hub.icao());
            return;
        }

        for (final Candidate candidate : hubSelected) {
            final Journeys.Journey journey = candidate.journey();
            log.info("hub test - f/m #{}, t/f #{} - {} -> {} - j/y #{} selected, g/s {}, cabin {}, located at {}, target {}, distance to target {} nm now, {} nm from the hub",
                    mission.getId(), transportFlight.getId(), hub.departureIcao(), hub.icao(), journey.getId(), journey.getGroupSize(),
                    actualCabinServices.get(journey.getId()),
                    world.cities().byId(journey.getLocationCityId()).orElseThrow().getName(),
                    world.cities().byId(journey.getToCityId()).orElseThrow().getName(),
                    Math.round(candidate.distanceNow()), Math.round(candidate.distanceFromHub()));
        }
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
