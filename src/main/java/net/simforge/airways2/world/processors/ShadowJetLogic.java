package net.simforge.airways2.world.processors;

import com.google.common.collect.Sets;
import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.AirportHubs;
import net.simforge.airways2.world.computations.JourneyWakeCurve;
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


    // journeys are taken from a random sample of the candidates
    private static final int CANDIDATES_SAMPLE_SIZE = 500;
    // The pack of journeys of a flight is the seats of the flight and a reserve of random 5..10% of the seats: some of the journeys do not find the flight (they choose another
    // one, or the flight is full by then), the reserve covers it. The reserve journeys wake up at random moments of the curve.
    private static final int WAKE_RESERVE_MIN_PERCENT = 5;
    private static final int WAKE_RESERVE_MAX_PERCENT = 10;
    // the load of a flight is not the same every time: the curve is stretched or squeezed in time by a random factor of 0.9 to 1.1 (plus minus 10%, as the page of the booking says)
    private static final double WAKE_SPREAD = 0.1;

    public static Aircrafts.Aircraft findAvailableAircraftOrCreateNew(
            final World world,
            final AircraftTypes.AircraftType aircraftType,
            final Airports.Airport locationAirport) {
        // an aircraft which has a flight ahead (a flight booked by a pilot, it is dispatched and the aircraft becomes active only at its start) is not free: it is reserved
        final Set<Integer> reservedAircraftIds = aircraftIdsWithFlightsAhead(world);
        final Optional<Aircrafts.Aircraft> existingAircraft = world.aircrafts()
                .byAircraftOperatorIdAndIdleAndParkedAtAirport(World25.ShadowJetOperatorId)
                .filter(a -> a.getAircraftTypeId() == aircraftType.getId())
                .filter(a -> !reservedAircraftIds.contains(a.getId()))
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

    /**
     * The aircraft which have a flight ahead: a flight mission which is planned or dispatched, not started yet (the aircraft becomes active at the start of the flight, so
     * a dispatched flight does not hold it by itself). It is how a booked flight reserves its aircraft.
     */
    public static Set<Integer> aircraftIdsWithFlightsAhead(final World world) {
        return world.flightMissions()
                .filter(world.flightMissions().anyStatus(
                        FlightMissions.Status.PlannedManually, FlightMissions.Status.PlannedViaSchedule, FlightMissions.Status.Dispatched))
                .map(FlightMissions.Mission::getAircraftId)
                .collect(Collectors.toSet());
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

    // The provision of a transport flight (reworked 2026-10-30, see docs/clients-and-flight-modes.md and JourneyWakeCurve): the flight is not filled at once. A pack of journeys is
    // chosen for it (the seats of the flight and a reserve, the journeys are shuffled), and every journey of the pack gets a heartbeat at the minute when the curve reaches its
    // place in the pack, and stays in LookingForTickets. When it wakes up, it looks for tickets by itself (JourneyProcessor.lookingForTickets), finds the flight and books it,
    // the check-in and the boarding go by the usual workflow. The longer the flight is open for the tickets, the more passengers it has. All the flights go this way: the flights
    // of the pilots which are booked ahead, the flights which are started at once, and the NPC flights (they have only a planned time, the real boarding ends when the aircraft
    // moves, so nothing is cut: what is not booked by then is not booked).
    public static void provideTransportFlightIfRequired(World world, FlightMissions.Mission mission) {
        provideTransportFlight(world, mission, false);
    }

    /**
     * The transport flight of a flight which a pilot has booked ahead (ShadowJetBooking). The pack of the journeys is chosen and woken by the curve (JourneyWakeCurve) as for any
     * flight, the journeys book the flight by themselves: if the check-in of the flight is not open yet (the departure is more than two hours away), the transport flight stays
     * scheduled, it opens the check-in by itself, and a journey which has booked it gets a random time of its check-in in the window of the check-in
     * (JourneyProcessor.waitingForCheckin). If the check-in is open already, the journeys check in as they book. The boarding is not started here, the pilot starts it.
     */
    public static void provideTransportFlightForBookedFlight(World world, FlightMissions.Mission mission) {
        provideTransportFlight(world, mission, true);
    }

    // booked: the flight of a pilot is booked ahead, the pilot starts the boarding; otherwise the flight is started now and the boarding starts at once
    private static void provideTransportFlight(World world, FlightMissions.Mission mission, boolean booked) {
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

        // the flight which is booked ahead opens its check-in by itself when the time comes, the other ones open it now
        final boolean checkInOpen = !booked || world.getWorldTime() >= TransportFlightHelper.calcCheckinStartTime(mission);
        if (checkInOpen) {
            world.transportFlightControl().startCheckIn(transportFlight);
        }

        // Direct journeys and journeys of the hub test live in one pool: a random sample of the candidates is taken, then the journeys are taken in the random order.
        final int seats = transportFlight.getTotalTickets().getTotal();

        final HubInfo hub = findHubInfo(world, mission);
        final CandidatePool pool = collectCandidates(world, fromCitiesId, toCitiesId, hub);

        // the first part of the pack is the seats of the flight (the classes are looked at)
        final List<Candidate> selected = new ArrayList<>();
        final Map<Integer, CabinLayout.Service> actualCabinServices = new HashMap<>();
        CabinLayout remained = transportFlight.getRemainedTickets();
        int paxPlanned = 0;
        for (final Candidate candidate : pool.sample()) {
            final Journeys.Journey journey = candidate.journey();
            if (paxPlanned + journey.getGroupSize() > seats) {
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

        // the second part is the reserve: journeys which would not fit, they wake up at random moments of the curve
        final int reservePercent = Tools.random(WAKE_RESERVE_MIN_PERCENT, WAKE_RESERVE_MAX_PERCENT);
        final int reservePax = seats * reservePercent / 100;
        final List<Candidate> reserve = new ArrayList<>();
        int paxReserve = 0;
        for (final Candidate candidate : pool.sample()) {
            if (paxReserve >= reservePax) {
                break;
            }
            if (selected.contains(candidate) || trySeat(transportFlight.getTotalTickets(), candidate.journey()) == null) {
                continue; // the journey which cannot take a seat in the empty flight at all is not woken
            }
            reserve.add(candidate);
            paxReserve += candidate.journey().getGroupSize();
        }

        // the moments of the wake-up: the n-th journey of the pack wakes up when the curve reaches the share of the seats which the journeys before it and it take up
        final double spread = 1.0 + WAKE_SPREAD * (2 * random.nextDouble() - 1);
        final int now = world.getWorldTime();
        int paxBefore = 0;
        final int[] wokenBy = new int[WAKE_REPORT_MINUTES.length]; // how many journeys wake up by each of the report minutes
        for (final Candidate candidate : selected) {
            paxBefore += candidate.journey().getGroupSize();
            wakeUp(candidate.journey(), now, JourneyWakeCurve.minutesToReach(paxBefore / (double) seats / spread), wokenBy);
        }
        for (final Candidate candidate : reserve) {
            wakeUp(candidate.journey(), now, JourneyWakeCurve.minutesToReach(random.nextDouble()), wokenBy);
        }

        log.info("T/f prov - f/m #{}, t/f #{} - {} journeys / {} PAX of {} seats + {} reserve journeys / {} PAX, spread {}, candidates {} (direct {}, hub {}), sample {}, selected (direct {}, hub {}), woken by minute {}, DONE {}: {}",
                mission.getId(), transportFlight.getId(), selected.size(), paxPlanned, seats, reserve.size(), paxReserve, String.format("%.2f", spread),
                pool.candidatesCount(), pool.directCount(), pool.hubCount(), pool.sample().size(),
                selected.stream().filter(c -> c.kind() == CandidateKind.DIRECT).count(),
                selected.stream().filter(c -> c.kind() == CandidateKind.HUB).count(),
                reportMinutes(wokenBy), booked ? "(booked ahead, the pilot starts the boarding)" : "(boarding started)",
                selected.stream().map(c -> "j/y #" + c.journey().getId()).toList());

        if (hub != null) {
            logHubSelection(world, mission, transportFlight, hub, pool, selected, actualCabinServices);
        }

        if (!booked) {
            world.transportFlightControl().startBoarding(transportFlight);
        }
    }

    // the minutes of the report in the log: how many journeys wake up by each of them
    private static final int[] WAKE_REPORT_MINUTES = {1, 5, 30, 60, 150, 330, 1410};

    // the journey stays in LookingForTickets and wakes up in the given minutes from now (at least in a second), the search of tickets finds the flight by itself
    private static void wakeUp(final Journeys.Journey journey, final int now, final double minutes, final int[] wokenBy) {
        journey.setHeartbeatTime(now + Math.max(1, (int) Math.round(minutes * Time.ONE_MINUTE)));
        // The wake-up is not an attempt of the search of the journey: if it does not find the flight, JourneyProcessor.lookingForTickets counts an attempt, so the counter is
        // taken down by one beforehand (not below zero), otherwise the journeys which are woken by the flights would burn all their attempts very fast (decided 2026-10-30).
        journey.setAttemptCounter(Math.max(0, journey.getAttemptCounter() - 1));
        for (int i = 0; i < WAKE_REPORT_MINUTES.length; i++) {
            if (minutes <= WAKE_REPORT_MINUTES[i]) {
                wokenBy[i]++;
            }
        }
    }

    private static String reportMinutes(final int[] wokenBy) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < WAKE_REPORT_MINUTES.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(WAKE_REPORT_MINUTES[i]).append("m:").append(wokenBy[i]);
        }
        return sb.toString();
    }

    private record Seating(CabinLayout remained, CabinLayout.Service service) {
    }

    // returns null if there are no seats for the journey even with one class upgrade/downgrade (CabinLayout.chooseService, the same rule as of the search of the tickets)
    private static Seating trySeat(final CabinLayout remained, final Journeys.Journey journey) {
        final CabinLayout.Service cabinService = remained.chooseService(journey.getGroupSize(), journey.getPreferredCabinService());
        return cabinService == null ? null : new Seating(remained.occupySeats(journey.getGroupSize(), cabinService), cabinService);
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
        if (!AirportHubs.isHub(hubAirport.getIcao())) {
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
