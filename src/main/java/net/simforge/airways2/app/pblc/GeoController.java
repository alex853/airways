package net.simforge.airways2.app.pblc;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.tools.CachedJson;
import net.simforge.airways2.app.tools.ChangeTrackedCache;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Countries, cities and airports of the world. The data changes rarely, so every response is built, serialized to JSON and
 * compressed once, and it is rebuilt only when the storages it depends on have changed (see {@link ChangeTrackedCache}).
 * A request which finds the response up to date does not take the world lock.
 */
@RestController
@RequestMapping("/geo")
@CrossOrigin
public class GeoController {
    private static final long[] WORLD_IS_NOT_READY = {-1};

    @Autowired
    private WorldRunnerBean worldBean;

    @Autowired
    private ObjectMapper objectMapper;

    // the storages the response depends on
    private final ChangeTrackedCache<CachedJson> countriesCache = new ChangeTrackedCache<>(
            "geo countries",
            () -> stamps(world -> new long[]{
                    world.countries().getLastChangedAtMillis()}),
            () -> buildJson("countries", this::buildCountries));

    private final ChangeTrackedCache<CachedJson> citiesCache = new ChangeTrackedCache<>(
            "geo cities",
            () -> stamps(world -> new long[]{
                    world.cities().getLastChangedAtMillis(),
                    world.airport2city().getLastChangedAtMillis(),
                    world.airports().getLastChangedAtMillis()}),
            () -> buildJson("cities", this::buildCities));

    private final ChangeTrackedCache<CachedJson> airportsCache = new ChangeTrackedCache<>(
            "geo airports",
            () -> stamps(world -> new long[]{
                    world.airports().getLastChangedAtMillis(),
                    world.airport2city().getLastChangedAtMillis(),
                    world.cities().getLastChangedAtMillis(),
                    world.countries().getLastChangedAtMillis()}),
            () -> buildJson("airports", this::buildAirports));

    @GetMapping("/countries")
    public ResponseEntity<byte[]> getCountries(@RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) final String acceptEncoding) {
        return countriesCache.get().toResponse(acceptEncoding);
    }

    @GetMapping("/cities")
    public ResponseEntity<byte[]> getCities(@RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) final String acceptEncoding) {
        return citiesCache.get().toResponse(acceptEncoding);
    }

    @GetMapping("/airports")
    public ResponseEntity<byte[]> getAirports(@RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false) final String acceptEncoding) {
        return airportsCache.get().toResponse(acceptEncoding);
    }

    private long[] stamps(final java.util.function.Function<World, long[]> reader) {
        final World world = worldBean.getWorldForChangeStamps();
        return world == null ? WORLD_IS_NOT_READY : reader.apply(world);
    }

    // the data is read under the world lock, the serialization and compression are done outside of it
    private <T> CachedJson buildJson(final String name, final java.util.function.Function<World, List<T>> dataBuilder) {
        try (final Timing.Timer ignored = Timing.label("GeoController - build " + name)) {
            final List<T> data = worldBean.read(dataBuilder::apply);
            return CachedJson.of(objectMapper, data);
        }
    }

    private List<CountryDto> buildCountries(final World world) {
        return world.countries().all()
                .map(c -> new CountryDto(
                        Id.encode(c.getId()),
                        c.getCode(),
                        c.getName()))
                .sorted(Comparator.comparing(CountryDto::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CountryDto::getCode))
                .toList();
    }

    private List<CityDto> buildCities(final World world) {
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
    }

    private List<AirportDto> buildAirports(final World world) {
        final Map<Integer, Integer> countryIdByAirportId = buildCountryIdByAirportId(world);
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
        return result;
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
