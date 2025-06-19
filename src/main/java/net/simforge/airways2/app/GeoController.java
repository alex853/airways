package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/geo")
@CrossOrigin
public class GeoController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/countries")
    public List<CountryDto> getCountries() {
        return worldBean.read(world -> world.countries().all().stream()
                .map(c -> new CountryDto(
                        c.getId(),
                        c.getCode(),
                        c.getName()))
                .toList());
    }

    @GetMapping("/cities")
    public List<CityDto> getCities() {
        return worldBean.read(world -> world.cities().all().stream()
                .map(c -> new CityDto(
                        c.getId(),
                        c.getCountryId(),
                        c.getName(),
                        c.getPopulation(),
                        c.getLatitude(),
                        c.getLongitude()))
                .toList());
    }

    @GetMapping("/airports")
    public List<AirportDto> getAirports() {
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

    @GetMapping("/airport/{icao}/details")
    public AirportDetailsDto getAirportDetails(@PathVariable final String icao) {
        return worldBean.read(world -> {
            final Airports.Airport airport = world.airports().byIcao(icao).orElseThrow();
            final List<String> connectedCities = world.airport2city().allByAirportId(airport.getId()).stream()
                    .map(l -> world.cities().byId(l.getCityId()).orElseThrow())
                    .sorted((c1, c2) -> c2.getPopulation() - c1.getPopulation())
                    .map(Cities.City::getName)
                    .toList();
            return new AirportDetailsDto(
                    connectedCities,
                    new ArrayList<>(),
                    0,
                    0,
                    0,
                    0
            );
        });
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
    }
}
