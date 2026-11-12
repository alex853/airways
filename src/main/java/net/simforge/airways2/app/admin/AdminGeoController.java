package net.simforge.airways2.app.admin;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airport2City;
import net.simforge.airways2.world.datamodel.AirportFacilities;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.worldbuilder.tools.CreateAirport;
import net.simforge.airways2.worldbuilder.tools.CreateCity;
import net.simforge.airways2.worldbuilder.tools.CreateCountry;
import net.simforge.airways2.worldbuilder.tools.ImportCities;
import net.simforge.airways2.worldbuilder.tools.ReuseCity;
import net.simforge.commons.io.Csv;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.Str;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Everything the admin does with the geography of the world: countries, cities, airports and the links between them (docs/city-reuse.md). A country, a city
 * and an airport are created here with a call, there is no need to restart the server with the world builder steps. Every call which changes something
 * (dryRun=false) asks the world cycle to save the world (in 5 minutes after the first request, one save for the changes which come one after another), so a new city or airport is not lost if the server stops before the hourly save. dryRun
 * is true by default everywhere: only the report is made then.
 */
@RestController
@RequestMapping("/admin/geo")
@CrossOrigin
public class AdminGeoController {
    private static final Logger log = LoggerFactory.getLogger(AdminGeoController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    /**
     * Creates a country: /admin/geo/country/create?code=GP&name=Guadeloupe
     */
    @GetMapping(value = "/country/create", produces = "text/plain")
    public String createCountry(@RequestParam("code") String code,
                                @RequestParam("name") String name,
                                @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        return modify(dryRun, world -> CreateCountry.create(world, code, name, dryRun));
    }

    /**
     * Creates a city with its city flow: /admin/geo/city/create?name=Aswan&country=EG&lat=24.08&lon=32.92&population=350500. countryName creates the country
     * too, when the world has no country with the code.
     */
    @GetMapping(value = "/city/create", produces = "text/plain")
    public String createCity(@RequestParam("name") String name,
                             @RequestParam("country") String countryCode,
                             @RequestParam(name = "countryName", required = false) String countryName,
                             @RequestParam("lat") double latitude,
                             @RequestParam("lon") double longitude,
                             @RequestParam("population") int population,
                             @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        return modify(dryRun, world -> CreateCity.create(world, name, countryCode, countryName, latitude, longitude, population, dryRun));
    }

    /**
     * Creates an airport by its ICAO code: /admin/geo/airport/create?icao=TFFR. The data comes from refdata and icaodata.csv; name, iata, lat and lon give the
     * values by hand (needed when the airport is in neither). country is the code of the country (by default the one of icaodata.csv, if the world has it),
     * city is the name of the city to link the airport with.
     */
    @GetMapping(value = "/airport/create", produces = "text/plain")
    public String createAirport(@RequestParam("icao") String icao,
                                @RequestParam(name = "name", required = false) String name,
                                @RequestParam(name = "iata", required = false) String iata,
                                @RequestParam(name = "lat", required = false) Double latitude,
                                @RequestParam(name = "lon", required = false) Double longitude,
                                @RequestParam(name = "country", required = false) String countryCode,
                                @RequestParam(name = "city", required = false) String cityName,
                                @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        return modify(dryRun, world -> CreateAirport.create(world, icao, new CreateAirport.Overrides(name, iata, latitude, longitude), countryCode, cityName, dryRun));
    }

    /**
     * The reuse of a city (see ReuseCity and docs/city-reuse.md): the city with the given name (it should be the only one with it) becomes another city
     * in another place, its id is kept.
     */
    @GetMapping(value = "/city/reuse", produces = "text/plain")
    public String reuseCity(@RequestParam("city") String cityName,
                            @RequestParam("name") String newName,
                            @RequestParam("country") String countryCode,
                            @RequestParam(name = "countryName", required = false) String countryName,
                            @RequestParam("lat") double latitude,
                            @RequestParam("lon") double longitude,
                            @RequestParam("population") int population,
                            @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        return modify(dryRun, world -> {
            final List<Cities.City> found = world.cities().all().filter(c -> c.getName().equalsIgnoreCase(cityName)).toList();
            if (found.size() != 1) {
                throw new IllegalArgumentException("Expected exactly one city named " + cityName + ", found " + found.size());
            }
            return ReuseCity.reuse(world, found.get(0).getId(), newName, countryCode, countryName, latitude, longitude, population, dryRun);
        });
    }

    @GetMapping(value = "/airport/create-links", produces = "text/plain")
    public String createAirport2CityLinks(@RequestParam("icao") String icao,
                                          @RequestParam(name = "maxDistance", defaultValue = "50") int maxDistance,
                                          @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        return modify(dryRun, world -> {
            final List<String> results = new ArrayList<>();

            if (dryRun) {
                results.add("DRY RUN - nothing is changed");
                results.add("");
            }

            final Airports.Airport airport = world.airports().byIcao(icao).orElseThrow(() -> new IllegalArgumentException("Airport " + icao + " not found"));
            world.cities().all()
                    .filter(c -> Geo.distance(c.getCoords(), airport.getCoords()) < maxDistance)
                    .forEach(c -> {
                        Optional<Airport2City.Link> link = world.airport2city().byAirportIdAndCityId(airport.getId(), c.getId());
                        if (link.isPresent()) {
                            results.add(Str.al(c.getName(), 20) + " exists");
                            return;
                        }

                        if (!dryRun) {
                            world.airport2city().create(airport.getId(), c.getId());
                            log.info("airport {} - linked with city {}", airport.getIcao(), c.getName());
                            results.add(Str.al(c.getName(), 20) + " created");
                        } else {
                            results.add(Str.al(c.getName(), 20) + " should be created");
                        }
                    });

            return results;
        });
    }

    /**
     * Removes the link of an airport with a city, for a link which should not be (through the sea or the border). The city is given by name, it has to
     * be one of the cities linked with the airport.
     */
    @GetMapping(value = "/airport/delete-link", produces = "text/plain")
    public String deleteAirport2CityLink(@RequestParam("icao") String icao,
                                         @RequestParam("city") String cityName,
                                         @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        return modify(dryRun, world -> {
            final List<String> results = new ArrayList<>();

            if (dryRun) {
                results.add("DRY RUN - nothing is changed");
                results.add("");
            }

            final Airports.Airport airport = world.airports().byIcao(icao).orElseThrow(() -> new IllegalArgumentException("Airport " + icao + " not found"));
            final List<Cities.City> linked = world.airport2city().allByAirportId(airport.getId())
                    .map(link -> world.cities().byId(link.getCityId()).orElseThrow())
                    .filter(c -> c.getName().equalsIgnoreCase(cityName))
                    .toList();
            if (linked.size() != 1) {
                throw new IllegalArgumentException("Expected exactly one city named " + cityName + " linked with " + icao + ", found " + linked.size());
            }

            final Cities.City city = linked.get(0);
            if (dryRun) {
                results.add(Str.al(city.getName(), 20) + " should be unlinked from " + airport.getIcao());
            } else {
                world.airport2city().delete(airport.getId(), city.getId());
                log.info("airport {} - the link with city {} is deleted", airport.getIcao(), city.getName());
                results.add(Str.al(city.getName(), 20) + " unlinked from " + airport.getIcao());
            }

            return results;
        });
    }

    @GetMapping(value = "/airport/create-business-aviation-terminal", produces = "text/plain")
    public String createAirportBusinessAviationTerminal(@RequestParam("icao") String icao) {
        return modify(false, world -> {
            final Airports.Airport airport = world.airports().byIcao(icao).orElseThrow(() -> new IllegalArgumentException("Airport " + icao + " not found"));
            final boolean exists = world.airportFacilities().hasFacility(airport, AirportFacilities.Type.BusinessAviationTerminal);
            if (!exists) {
                world.airportFacilities().createIfAbsent(airport, AirportFacilities.Type.BusinessAviationTerminal);
                log.info("airport {} - the business aviation terminal is created", airport.getIcao());
            }
            return List.of(exists ? "Exists" : "Created");
        });
    }

    @GetMapping(value = "/airport/missing-cities", produces = "text/plain")
    public String printAirportsWithMissingCities() throws IOException {
        Csv citiesCsv = ImportCities.loadCityPopulationCsv();

        return worldBean.read(world -> {
            List<String> results = new ArrayList<>();

            world.airports().all().forEach(airport -> {
                List<Airport2City.Link> links = world.airport2city().allByAirportId(airport.getId()).toList();
                if (links.isEmpty()) {
                    results.add(Str.al(airport.getIcao(), 10) + "No any city linked");
                }

                List<ImportCities.CityInfo> cities = ImportCities.getCitiesNearAirport(citiesCsv, airport.getCoords(), 50);
                List<ImportCities.CityInfo> bigCities = cities.stream().filter(c -> c.getPopulation() >= 1000000 && !c.getName().startsWith("del ")).toList();

                bigCities.forEach(bigCity -> {
                    Optional<Cities.City> found = world.cities().all().filter(c -> c.getName().equals(bigCity.getName())).findFirst();
                    if (found.isEmpty()) {
                        results.add(Str.al(airport.getIcao(), 10) + "MISSING BIG CITY     " + bigCity.getName() + " (population " + bigCity.getPopulation() + ")");
                    }
                });
            });

            return Strings.join(results, '\n');
        });
    }

    /**
     * Runs the action in the world cycle. A refusal (IllegalArgumentException) is the answer "Not done: ..." and changes nothing. When the call changes
     * the world (dryRun is false) and succeeded, the world is saved at once.
     */
    private String modify(final boolean dryRun, final Function<World, List<String>> action) {
        return worldBean.modifySync(world -> {
            try {
                final List<String> report = action.apply(world);
                if (!dryRun) {
                    worldBean.requestSave();
                }
                return Strings.join(report, '\n');
            } catch (final IllegalArgumentException e) {
                return "Not done: " + e.getMessage();
            }
        });
    }
}
