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
import net.simforge.airways2.world.datamodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/map")
@CrossOrigin
public class MapController {
    private static final int RECENT_FLIGHTS_LIMIT = 10;
    private static final int INFO_TOP_CONNECTIONS = 3;
    private static final int FULL_INFO_TOP_CONNECTIONS = 10;

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/aircraft/flying")
    public List<AircraftMapDto> getFlyingAircraft() {
        // todo ak2 separate thread to build this data outside of web-request and provide caching of this big dataset
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
            return new AircraftFullInfoDto(
                    AircraftInfoDto.from(world, aircraft, true),
                    recentFlights);
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
                                f.getType()))
                        .toList();

                return new AirportFullInfoDto(
                        buildAirportInfo(world, airport, FULL_INFO_TOP_CONNECTIONS),
                        parkedAircraft,
                        facilities);
            });
        }
    }

    private static AirportInfoDto buildAirportInfo(final World world, final Airports.Airport airport, final int topConnectionsLimit) {
        final List<String> connectedCities = world.airport2city().allByAirportId(airport.getId())
                .map(l -> world.cities().byId(l.getCityId()).orElseThrow())
                .sorted(Comparator.comparing(Cities.City::getPopulation).reversed())
                .map(Cities.City::getName)
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
        final List<IcaoToFlights> topConnections = totalConnections.values().stream()
                .sorted(Comparator.comparingInt(IcaoToFlights::getFlights).reversed())
                .limit(topConnectionsLimit)
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
                .filter(a -> a.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport
                        && a.getOperationalStatus() == Aircrafts.OperationalStatus.Idle)
                .count();
        final int aircraftActive = (int) aircraftLocatedAtAirport.stream()
                .filter(a -> a.getOperationalStatus() == Aircrafts.OperationalStatus.Active)
                .count();

        return new AirportInfoDto(
                connectedCities,
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
    }

    @Data
    @AllArgsConstructor
    public static class AirportInfoDto {
        private List<String> connectedCities;
        private List<IcaoToFlights> topConnections;
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
        private final List<AirportFacilityDto> facilities;
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
        private AirportFacilities.Type type;
    }

    @Data
    @AllArgsConstructor
    public static class IcaoToFlights {
        private String icao;
        private int flights;

        public static IcaoToFlights merge(final IcaoToFlights a, final IcaoToFlights b) {
            return new IcaoToFlights(a.icao, a.flights + b.flights);
        }
    }
}
