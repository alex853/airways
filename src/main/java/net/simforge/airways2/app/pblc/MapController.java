package net.simforge.airways2.app.pblc;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.dto.AircraftInfoDto;
import net.simforge.airways2.app.dto.FlightMinDto;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.FlightMissionToTimeline;
import net.simforge.airways2.world.datamodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/map")
@CrossOrigin
public class MapController {
    private static final int RECENT_FLIGHTS_LIMIT = 10;
    private static final int INFO_TOP_CONNECTIONS = 3;
    private static final int FULL_INFO_TOP_CONNECTIONS = 10;
    private static final int AIRPORT_UPCOMING_WINDOW = 4 * Time.ONE_HOUR;
    private static final int AIRPORT_RECENT_WINDOW = 12 * Time.ONE_HOUR;
    private static final int AIRPORT_RECENT_FLIGHTS_LIMIT = 20;

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/aircraft/flying")
    public List<AircraftMapDto> getFlyingAircraft() {
        // todo ak1 separate thread to build this data outside of web-request and provide caching of this big dataset
        return worldBean.read(world -> world.aircrafts()
                .filter(world.aircrafts().byLocationStatus(Aircrafts.LocationStatus.Flying))
                .map(AircraftMapDto::from)
                .sorted(Comparator.comparing(AircraftMapDto::getAcReg))
                .toList());
    }

    @GetMapping("/aircraft/info")
    public AircraftInfoDto getAircraftInfo(@RequestParam("id") String aircraftId) {
        return worldBean.read(world -> AircraftInfoDto.from(world,
                world.aircrafts()
                        .byId(Id.decode(aircraftId))
                        .orElseThrow(),
                true));
    }

    @GetMapping("/aircraft/full-info")
    public AircraftFullInfoDto getAircraftFullInfo(@RequestParam("id") String aircraftId) {
        return worldBean.read(world -> {
            final Aircrafts.Aircraft aircraft = world.aircrafts()
                    .byId(Id.decode(aircraftId))
                    .orElseThrow();
            final List<FlightMinDto> recentFlights = world.flightMissions().allForAircraft(aircraft)
                    .filter(fm -> fm.getStatus() == FlightMissions.Status.Finished)
                    .sorted(FlightMissions.sortByDepartureTimeFromFutureToPast)
                    .limit(RECENT_FLIGHTS_LIMIT)
                    .map(fm -> FlightMinDto.from(world, fm))
                    .toList();
            final AircraftInfoDto info = AircraftInfoDto.from(world, aircraft, true);

            // names and countries of all the airports mentioned in the response, by ICAO
            final AirportRefs refs = new AirportRefs(world);
            final Map<String, AirportRefDto> airports = new LinkedHashMap<>();
            Stream.concat(
                            Stream.of(info.getLcApt(), info.getDep(), info.getDest()),
                            recentFlights.stream().flatMap(f -> Stream.of(f.getDep(), f.getDest())))
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(icao -> world.airports().byIcao(icao)
                            .ifPresent(a -> airports.put(icao, refs.of(a))));

            return new AircraftFullInfoDto(info, recentFlights, airports);
        });
    }

    @GetMapping("/airport/info")
    public AirportInfoDto getAirportInfo(@RequestParam("icao") final String icao) {
        try (final Timing.Timer ignored = Timing.label("MapController - getAirportInfo")) {
            return worldBean.read(world -> buildAirportInfo(world, world.airports().byIcao(icao).orElseThrow(), INFO_TOP_CONNECTIONS));
        }
    }

    @GetMapping("/airport/full-info")
    public AirportFullInfoDto getAirportFullInfo(@RequestParam("icao") final String icao) {
        try (final Timing.Timer ignored = Timing.label("MapController - getAirportFullInfo")) {
            return worldBean.read(world -> {
                final Airports.Airport airport = world.airports().byIcao(icao).orElseThrow();

                final List<ParkedAircraftDto> parkedAircraft = world.aircrafts()
                        .filter(world.aircrafts().byLocationAirportId(airport.getId()))
                        .filter(a -> a.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport)
                        .map(a -> new ParkedAircraftDto(
                                Id.encode(a.getId()),
                                a.getRegNo(),
                                world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                                a.getLastUpdated() > 0 ? Time.toLdt(a.getLastUpdated()).toString() : null))
                        .sorted(Comparator.comparing(ParkedAircraftDto::getReg))
                        .toList();

                final List<AirportFacilityDto> facilities = world.airportFacilities().by(airport)
                        .map(f -> new AirportFacilityDto(
                                Id.encode(f.getAircraftOperatorId()),
                                world.aircraftOperators().byId(f.getAircraftOperatorId())
                                        .map(AircraftOperators.AircraftOperator::getName)
                                        .orElse(null),
                                f.getType()))
                        .toList();

                return new AirportFullInfoDto(
                        buildAirportInfo(world, airport, FULL_INFO_TOP_CONNECTIONS),
                        parkedAircraft,
                        buildAirportFlights(world, airport),
                        facilities);
            });
        }
    }

    private static List<AirportFlightDto> buildAirportFlights(final World world, final Airports.Airport airport) {
        final int now = world.getWorldTime();
        final AirportRefs refs = new AirportRefs(world);

        final List<AirportFlightDto> active = new ArrayList<>();
        final List<AirportFlightDto> upcoming = new ArrayList<>();
        final Map<String, List<FlightRow>> recentByDirection = new HashMap<>();

        world.flightMissions().filter(world.flightMissions().anyStatus(
                        FlightMissions.Status.Dispatched,
                        FlightMissions.Status.Preflight,
                        FlightMissions.Status.Departure,
                        FlightMissions.Status.Flying,
                        FlightMissions.Status.Arrival,
                        FlightMissions.Status.Postflight,
                        FlightMissions.Status.Finished))
                .forEach(fm -> {
                    if (fm.getDepartureAirportId() == airport.getId()) {
                        classifyAirportFlight(world, refs, fm, "out", now, active, upcoming, recentByDirection);
                    }
                    if (fm.getDestinationAirportId() == airport.getId()) {
                        classifyAirportFlight(world, refs, fm, "in", now, active, upcoming, recentByDirection);
                    }
                });

        final List<AirportFlightDto> result = new ArrayList<>(active);
        result.addAll(upcoming);
        recentByDirection.values().forEach(rows -> rows.stream()
                .sorted(Comparator.comparingInt(FlightRow::time).reversed())
                .limit(AIRPORT_RECENT_FLIGHTS_LIMIT)
                .forEach(r -> result.add(r.dto())));
        return result;
    }

    private static void classifyAirportFlight(final World world,
                                              final AirportRefs refs,
                                              final FlightMissions.Mission fm,
                                              final String direction,
                                              final int now,
                                              final List<AirportFlightDto> active,
                                              final List<AirportFlightDto> upcoming,
                                              final Map<String, List<FlightRow>> recentByDirection) {
        final boolean outbound = "out".equals(direction);
        // the time of the flight at the airport we are looking at
        final int plannedTime = outbound ? fm.getPlannedDepartureWorldTime() : fm.getPlannedArrivalWorldTime();
        final int actualTime = outbound ? fm.getActualDepartureWorldTime() : fm.getActualArrivalWorldTime();

        switch (fm.getStatus()) {
            case Departure, Flying, Arrival -> active.add(toAirportFlight(world, refs, fm, direction, "Flying"));
            case Dispatched, Preflight -> {
                if (plannedTime <= now + AIRPORT_UPCOMING_WINDOW) {
                    upcoming.add(toAirportFlight(world, refs, fm, direction, "Scheduled"));
                }
            }
            case Postflight, Finished -> {
                final int time = actualTime != 0 ? actualTime : plannedTime;
                if (time >= now - AIRPORT_RECENT_WINDOW) {
                    recentByDirection
                            .computeIfAbsent(direction, d -> new ArrayList<>())
                            .add(new FlightRow(time, toAirportFlight(world, refs, fm, direction, "Arrived")));
                }
            }
            default -> {
            }
        }
    }

    private static AirportFlightDto toAirportFlight(final World world,
                                                    final AirportRefs refs,
                                                    final FlightMissions.Mission fm,
                                                    final String direction,
                                                    final String status) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(fm.getAircraftId()).orElseThrow();
        final int otherAirportId = "out".equals(direction) ? fm.getDestinationAirportId() : fm.getDepartureAirportId();

        final LocalDateTime eta = "Flying".equals(status)
                ? FlightMissionToTimeline.byMission(fm).getBlocksOn().getEstimatedTime()
                : null;

        return new AirportFlightDto(
                Id.encode(fm.getId()),
                direction,
                status,
                new AircraftRefDto(
                        Id.encode(aircraft.getId()),
                        aircraft.getRegNo(),
                        world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao()),
                refs.of(world.airports().byId(otherAirportId).orElseThrow()),
                ldtOrNull(fm.getPlannedDepartureWorldTime()),
                ldtOrNull(fm.getActualDepartureWorldTime()),
                ldtOrNull(fm.getPlannedArrivalWorldTime()),
                eta != null ? eta.toString() : null,
                ldtOrNull(fm.getActualArrivalWorldTime()));
    }

    private static String ldtOrNull(final int worldTime) {
        return worldTime != 0 ? Time.toLdt(worldTime).toString() : null;
    }

    private static AirportInfoDto buildAirportInfo(final World world, final Airports.Airport airport, final int topConnectionsLimit) {
        final AirportRefs refs = new AirportRefs(world);

        final List<CityRefDto> cities = world.airport2city().allByAirportId(airport.getId())
                .map(l -> world.cities().byId(l.getCityId()).orElseThrow())
                .sorted(Comparator.comparing(Cities.City::getPopulation).reversed())
                .map(c -> new CityRefDto(Id.encode(c.getId()), c.getName(), c.getPopulation()))
                .toList();

        final Map<String, IcaoToFlights> outboundConnections = world.airport2airportDailyFlightStats()
                .allByFromAirportId(airport.getId())
                .map(fs -> new IcaoToFlights(world.airports().getIcao(fs.getToAirportId()).orElseThrow(), fs.getTotalCount()))
                .collect(Collectors.toMap(p -> p.icao, p -> p));
        final Map<String, IcaoToFlights> inboundConnections = world.airport2airportDailyFlightStats()
                .allByToAirportId(airport.getId())
                .map(fs -> new IcaoToFlights(world.airports().getIcao(fs.getFromAirportId()).orElseThrow(), fs.getTotalCount()))
                .collect(Collectors.toMap(p -> p.icao, p -> p));
        final Map<String, IcaoToFlights> totalConnections = Stream.concat(
                outboundConnections.entrySet().stream(),
                inboundConnections.entrySet().stream()
        ).collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                IcaoToFlights::merge
        ));
        final List<TopConnectionDto> topConnections = totalConnections.values().stream()
                .sorted(Comparator.comparingInt(IcaoToFlights::getFlights).reversed())
                .limit(topConnectionsLimit)
                .map(c -> {
                    final AirportRefDto ref = refs.of(world.airports().byIcao(c.getIcao()).orElseThrow());
                    return new TopConnectionDto(ref.getIcao(), ref.getName(), ref.getCountry(), c.getFlights());
                })
                .toList();

        final Collection<FlightMissions.Mission> aliveFlights = world.flightMissions().filter(
                        world.flightMissions().anyStatus(
                                FlightMissions.Status.Departure,
                                FlightMissions.Status.Flying,
                                FlightMissions.Status.Arrival))
                .toList();
        final int flightsOutbound = (int) aliveFlights.stream()
                .filter(f -> f.getDepartureAirportId() == airport.getId())
                .count();
        final int flightsInbound = (int) aliveFlights.stream()
                .filter(f -> f.getDestinationAirportId() == airport.getId())
                .count();

        final Collection<Aircrafts.Aircraft> aircraftLocatedAtAirport = world.aircrafts()
                .filter(world.aircrafts().byLocationAirportId(airport.getId()))
                .toList();
        final int aircraftParked = (int) aircraftLocatedAtAirport.stream()
                .filter(a -> a.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport)
                .count();
        final int aircraftActive = (int) aircraftLocatedAtAirport.stream()
                .filter(a -> a.getOperationalStatus() == Aircrafts.OperationalStatus.Active)
                .count();

        final AirportRefDto self = refs.of(airport);
        return new AirportInfoDto(
                self.getIcao(),
                self.getName(),
                self.getCountry(),
                airport.getLatitude(),
                airport.getLongitude(),
                cities,
                topConnections,
                flightsOutbound,
                flightsInbound,
                aircraftParked,
                aircraftActive);
    }

    @Data
    @AllArgsConstructor
    public static class AircraftMapDto {
        private String id;
        private String acReg;
        private float lat;
        private float lon;
        private int hdg;

        public static AircraftMapDto from(final Aircrafts.Aircraft a) {
            return new AircraftMapDto(
                    Id.encode(a.getId()),
                    a.getRegNo(),
                    a.getLocationLatitude(),
                    a.getLocationLongitude(),
                    a.getLocationHeading());
        }
    }

    @Data
    @AllArgsConstructor
    public static class AircraftFullInfoDto {
        private final AircraftInfoDto info;
        private final List<FlightMinDto> recentFlights;
        /** airports referenced by info and recentFlights, by ICAO */
        private final Map<String, AirportRefDto> airports;
    }

    @Data
    @AllArgsConstructor
    public static class AirportInfoDto {
        private String icao;
        private String name;
        /** country of the most populated city the airport is linked to, null if the airport has no city */
        private String country;
        private float lat;
        private float lon;
        /** the most populated first */
        private List<CityRefDto> cities;
        private List<TopConnectionDto> topConnections;
        private int flightsOutbound;
        private int flightsInbound;
        private int aircraftParked;
        private int aircraftActive;
    }

    @Data
    @AllArgsConstructor
    public static class AirportFullInfoDto {
        private final AirportInfoDto info;
        private final List<ParkedAircraftDto> parkedAircraft;
        private final List<AirportFlightDto> flights;
        private final List<AirportFacilityDto> facilities;
    }

    @Data
    @AllArgsConstructor
    public static class AirportFlightDto {
        private String id;
        /** out - departs from the airport, in - arrives to the airport */
        private String direction;
        /** Scheduled, Flying, Arrived */
        private String status;
        private AircraftRefDto aircraft;
        /** the other end of the flight - destination for out, origin for in */
        private AirportRefDto airport;
        private String std;
        private String atd;
        private String sta;
        private String eta;
        private String ata;
    }

    @Data
    @AllArgsConstructor
    public static class AircraftRefDto {
        private String id;
        private String reg;
        private String type;
    }

    private record FlightRow(int time, AirportFlightDto dto) {
    }

    @Data
    @AllArgsConstructor
    public static class ParkedAircraftDto {
        private String id;
        private String reg;
        private String type;
        private String since;
    }

    @Data
    @AllArgsConstructor
    public static class AirportFacilityDto {
        private String operatorId;
        private String operatorName;
        private AirportFacilities.Type type;
    }

    @Data
    @AllArgsConstructor
    public static class CityRefDto {
        private String id;
        private String name;
        private int population;
    }

    @Data
    @AllArgsConstructor
    public static class AirportRefDto {
        private String icao;
        private String name;
        private String country;
    }

    @Data
    @AllArgsConstructor
    public static class TopConnectionDto {
        private String icao;
        private String name;
        private String country;
        /** weekly flights, both directions */
        private int flights;
    }

    /**
     * Builds airport references with country names, the country of an airport is calculated once per request.
     */
    private static class AirportRefs {
        private final World world;
        private final Map<Integer, AirportRefDto> cache = new HashMap<>();

        AirportRefs(final World world) {
            this.world = world;
        }

        AirportRefDto of(final Airports.Airport airport) {
            return cache.computeIfAbsent(airport.getId(), id -> new AirportRefDto(
                    airport.getIcao(),
                    airport.getName(),
                    // country of the most populated city the airport is linked to
                    world.airport2city().allByAirportId(airport.getId())
                            .map(l -> world.cities().byId(l.getCityId()).orElseThrow())
                            .max(Comparator.comparingInt(Cities.City::getPopulation))
                            .flatMap(c -> world.countries().byId(c.getCountryId()))
                            .map(Countries.Country::getName)
                            .orElse(null)));
        }
    }

    @Data
    @AllArgsConstructor
    private static class IcaoToFlights {
        private String icao;
        private int flights;

        public static IcaoToFlights merge(final IcaoToFlights a, final IcaoToFlights b) {
            return new IcaoToFlights(a.icao, a.flights + b.flights);
        }
    }
}
