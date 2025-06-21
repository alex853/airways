package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/geo")
@CrossOrigin
public class GeoController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/countries")
    public List<CountryDto> getCountries() {
        try (final Timing.Timer ignored = Timing.label("GeoController - getCountries")) {
            return worldBean.read(world -> world.countries().all().stream()
                    .map(c -> new CountryDto(
                            c.getId(),
                            c.getCode(),
                            c.getName()))
                    .toList());
        }
    }

    @GetMapping("/cities")
    public List<CityDto> getCities() {
        try (final Timing.Timer ignored = Timing.label("GeoController - getCities")) {
            return worldBean.read(world -> world.cities().all().stream()
                    .map(c -> new CityDto(
                            c.getId(),
                            c.getCountryId(),
                            c.getName(),
                            c.getPopulation(),
                            c.getLatitude(),
                            c.getLongitude(),
                            world.airport2city().allByCityId(c.getId()).stream()
                                    .map(l -> world.airports().getIcao(l.getAirportId()))
                                    .toList()))
                    .toList());
        }
    }

    @GetMapping("/airports")
    public List<AirportDto> getAirports() {
        try (final Timing.Timer ignored = Timing.label("GeoController - getAirports")) {
            return worldBean.read(world -> world.airports().all().stream()
                    .map(a -> new AirportDto(
                            a.getId(),
                            a.getLatitude(),
                            a.getLongitude(),
                            a.getIata(),
                            a.getIcao(),
                            a.getName()))
                    .toList());
        }
    }

    @GetMapping("/airport/{icao}/details")
    public AirportDetailsDto getAirportDetails(@PathVariable final String icao) {
        try (final Timing.Timer ignored = Timing.label("GeoController - getAirportDetails")) {
            return worldBean.read(world -> {
                try (final Timing.Timer ignored1 = Timing.label("GeoController - getAirportDetails # part1")) {
                    final Airports.Airport airport = world.airports().byIcao(icao).orElseThrow();
                    try (final Timing.Timer ignored2 = Timing.label("GeoController - getAirportDetails # part2")) {
                        final List<String> connectedCities = world.airport2city().allByAirportId(airport.getId()).stream()
                                .map(l -> world.cities().byId(l.getCityId()).orElseThrow())
                                .sorted(Comparator.comparing(Cities.City::getPopulation))
                                .map(Cities.City::getName)
                                .toList();
                        try (final Timing.Timer ignored3 = Timing.label("GeoController - getAirportDetails # part3")) {
                            final Map<String, IcaoToFlights> outboundConnections = world.airport2airportDailyFlightStats()
                                    .allByFromAirportId(airport.getId()).stream()
                                    .map(fs -> new IcaoToFlights(world.airports().getIcao(fs.getToAirportId()), fs.getTotalCount()))
                                    .collect(Collectors.toMap(p -> p.icao, p -> p));
                            final Map<String, IcaoToFlights> inboundConnections = world.airport2airportDailyFlightStats()
                                    .allByToAirportId(airport.getId()).stream()
                                    .map(fs -> new IcaoToFlights(world.airports().getIcao(fs.getFromAirportId()), fs.getTotalCount()))
                                    .collect(Collectors.toMap(p -> p.icao, p -> p));
                            try (final Timing.Timer ignored4 = Timing.label("GeoController - getAirportDetails # part4")) {
                                final Map<String, IcaoToFlights> totalConnections = Stream.concat(
                                        outboundConnections.entrySet().stream(),
                                        inboundConnections.entrySet().stream()
                                ).collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        IcaoToFlights::merge
                                ));
                                final List<IcaoToFlights> top3connections = totalConnections.values().stream()
                                        .sorted(Comparator.comparingInt(IcaoToFlights::getFlights).reversed())
                                        .limit(3)
                                        .toList();
                                try (final Timing.Timer ignored5 = Timing.label("GeoController - getAirportDetails # part5")) {
                                    final Collection<FlightMissions.Mission> aliveFlights = world.flightMissions()
                                            .filter(f -> isFlightAlive(f.getStatus()));
                                    final int flightsOutbound = (int) aliveFlights.stream()
                                            .filter(f -> f.getDepartureAirportId() == airport.getId())
                                            .count();
                                    final int flightsInbound = (int) aliveFlights.stream()
                                            .filter(f -> f.getDestinationAirportId() == airport.getId())
                                            .count();
                                    try (final Timing.Timer ignored6 = Timing.label("GeoController - getAirportDetails # part6")) {
                                        final Collection<Aircrafts.Aircraft> aircraftLocatedAtAirport = world.aircrafts()
                                                .filter(a -> a.getLocationAirportId() == airport.getId());
                                        final int aircraftParked = (int) aircraftLocatedAtAirport.stream()
                                                .filter(a -> a.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport
                                                        && a.getOperationalStatus() == Aircrafts.OperationalStatus.Idle)
                                                .count();
                                        final int aircraftActive = (int) aircraftLocatedAtAirport.stream()
                                                .filter(a -> a.getOperationalStatus() == Aircrafts.OperationalStatus.Active)
                                                .count();
                                        return new AirportDetailsDto(
                                                connectedCities,
                                                top3connections,
                                                flightsOutbound,
                                                flightsInbound,
                                                aircraftParked,
                                                aircraftActive
                                        );
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    private boolean isFlightAlive(final FlightMissions.Status status) {
        return status == FlightMissions.Status.Departure
                || status == FlightMissions.Status.Flying
                || status == FlightMissions.Status.Arrival;
    }

    @Data
    @AllArgsConstructor
    private static class CountryDto {
        private int id;
        private String code;
        private String name;
    }

    @Data
    @AllArgsConstructor
    private static class CityDto {
        private int id;
        private int countryId;
        private String name;
        private int population;
        private float latitude;
        private float longitude;
        private List<String> airports;
    }

    @Data
    @AllArgsConstructor
    private static class AirportDto {
        private int id;
        private float latitude;
        private float longitude;
        private String iata;
        private String icao;
        private String name;
    }

    @Data
    @AllArgsConstructor
    private static class AirportDetailsDto {
        private List<String> connectedCities;
        private List<IcaoToFlights> top3connections;
        private int flightsOutbound;
        private int flightsInbound;
        private int aircraftParked;
        private int aircraftActive;
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
