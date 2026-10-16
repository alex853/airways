package net.simforge.airways2.app.pblc;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/geo")
@CrossOrigin
public class GeoController {
    @Autowired
    private WorldRunnerBean worldBean;

    /**
     * Airport id to country id (country of the most populated city the airport is linked to). Airports are created
     * with a world restart, but airport2city links and city populations can change in a running world (admin
     * create-links, f1-tour-fixes), so the map is dropped by {@link #invalidateAirportCountries()} and rebuilt lazily.
     */
    private volatile Map<Integer, Integer> countryIdByAirportId;

    public void invalidateAirportCountries() {
        countryIdByAirportId = null;
    }

    // todo ak0 wrap the response into json and keep that json in memory
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

    // todo ak0 wrap the response into json and keep that json in memory
    @GetMapping("/cities")
    public List<CityDto> getCities() {
        try (final Timing.Timer ignored = Timing.label("GeoController - getCities")) {
            return worldBean.read(world -> {
                final Map<Integer, List<String>> icaosByCityId = icaosByCityId(world);
                return world.cities().all()
                        .map(c -> new CityDto(
                                Id.encode(c.getId()),
                                c.getCountryId(),
                                c.getName(),
                                c.getPopulation(),
                                c.getLatitude(),
                                c.getLongitude(),
                                icaosByCityId.getOrDefault(c.getId(), List.of())))
                        .sorted(Comparator.comparing(CityDto::getName, String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(CityDto::getCountryId))
                        .toList();
            });
        }
    }

    // todo ak0 wrap the response into json and keep that json in memory
    @GetMapping("/airports")
    public List<AirportDto> getAirports() {
        try (final Timing.Timer ignored = Timing.label("GeoController - getAirports")) {
            return worldBean.read(world -> {
                final Map<Integer, Integer> countryIdByAirportId = countryIdByAirportId(world);
                final Map<Integer, String> countryNameById = new HashMap<>();
                return world.airports().all()
                        .map(a -> new AirportDto(
                                Id.encode(a.getId()),
                                a.getLatitude(),
                                a.getLongitude(),
                                a.getIata(),
                                a.getIcao(),
                                a.getName(),
                                countryName(world, countryNameById, countryIdByAirportId.get(a.getId()))))
                        .sorted(Comparator.comparing(AirportDto::getIcao))
                        .toList();
            });
        }
    }

    private Map<Integer, Integer> countryIdByAirportId(final World world) {
        Map<Integer, Integer> map = countryIdByAirportId;
        if (map == null) {
            map = buildCountryIdByAirportId(world);
            countryIdByAirportId = map;
        }
        return map;
    }

    /**
     * Country of the most populated city each airport is linked to; airports without a city are absent.
     * Built in a single pass over airport2city links instead of scanning the links for every airport.
     */
    private static Map<Integer, Integer> buildCountryIdByAirportId(final World world) {
        final Map<Integer, Cities.City> biggestCityByAirportId = new HashMap<>();
        world.airport2city().all().forEach(link -> {
            final Cities.City city = world.cities().byId(link.getCityId()).orElseThrow();
            final Cities.City current = biggestCityByAirportId.get(link.getAirportId());
            if (current == null || city.getPopulation() > current.getPopulation()) {
                biggestCityByAirportId.put(link.getAirportId(), city);
            }
        });

        final Map<Integer, Integer> result = new HashMap<>();
        biggestCityByAirportId.forEach((airportId, city) -> result.put(airportId, city.getCountryId()));
        return Map.copyOf(result);
    }

    private static String countryName(final World world, final Map<Integer, String> cache, final Integer countryId) {
        if (countryId == null) {
            return null;
        }
        return cache.computeIfAbsent(countryId,
                id -> world.countries().byId(id).map(Countries.Country::getName).orElse(null));
    }

    /**
     * ICAO codes of airports linked to each city, built in a single pass over airport2city links.
     */
    private static Map<Integer, List<String>> icaosByCityId(final World world) {
        final Map<Integer, List<String>> result = new HashMap<>();
        world.airport2city().all().forEach(link -> {
            final String icao = world.airports().getIcao(link.getAirportId()).orElseThrow();
            result.computeIfAbsent(link.getCityId(), id -> new ArrayList<>()).add(icao);
        });
        return result;
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
