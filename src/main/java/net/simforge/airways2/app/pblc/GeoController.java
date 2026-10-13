package net.simforge.airways2.app.pblc;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/geo")
@CrossOrigin
public class GeoController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/countries")
    public List<CountryDto> getCountries() {
        try (final Timing.Timer ignored = Timing.label("GeoController - getCountries")) {
            return worldBean.read(world -> world.countries().all()
                    .map(c -> new CountryDto(
                            Id.encode(c.getId()),
                            c.getCode(),
                            c.getName()))
                    .sorted(Comparator.comparing(CountryDto::getName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(CountryDto::getCode))
                    .toList());
        }
    }

    @GetMapping("/cities")
    public List<CityDto> getCities() {
        try (final Timing.Timer ignored = Timing.label("GeoController - getCities")) {
            return worldBean.read(world -> world.cities().all()
                    .map(c -> new CityDto(
                            Id.encode(c.getId()),
                            c.getCountryId(),
                            c.getName(),
                            c.getPopulation(),
                            c.getLatitude(),
                            c.getLongitude(),
                            world.airport2city().allByCityId(c.getId())
                                    .map(l -> world.airports().getIcao(l.getAirportId()).orElseThrow())
                                    .toList()))
                    .sorted(Comparator.comparing(CityDto::getName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(CityDto::getCountryId))
                    .toList());
        }
    }

    @GetMapping("/airports")
    public List<AirportDto> getAirports() {
        try (final Timing.Timer ignored = Timing.label("GeoController - getAirports")) {
            return worldBean.read(world -> world.airports().all()
                    .map(a -> new AirportDto(
                            Id.encode(a.getId()),
                            a.getLatitude(),
                            a.getLongitude(),
                            a.getIata(),
                            a.getIcao(),
                            a.getName(),
                            countryName(world, a)))
                    .sorted(Comparator.comparing(AirportDto::getIcao))
                    .toList());
        }
    }

    /**
     * Country of the most populated city the airport is linked to, null if the airport has no city.
     */
    private static String countryName(final World world, final Airports.Airport airport) {
        return world.airport2city().allByAirportId(airport.getId())
                .map(l -> world.cities().byId(l.getCityId()).orElseThrow())
                .max(Comparator.comparingInt(Cities.City::getPopulation))
                .flatMap(c -> world.countries().byId(c.getCountryId()))
                .map(Countries.Country::getName)
                .orElse(null);
    }

    @Data
    @AllArgsConstructor
    public static class CountryDto {
        private String id;
        private String code;
        private String name;
    }

    @Data
    @AllArgsConstructor
    public static class CityDto {
        private String id;
        private int countryId;
        private String name;
        private int population;
        private float latitude;
        private float longitude;
        private List<String> airports;
    }

    @Data
    @AllArgsConstructor
    public static class AirportDto {
        private String id;
        private float latitude;
        private float longitude;
        private String iata;
        private String icao;
        private String name;
        private String country;
    }
}
