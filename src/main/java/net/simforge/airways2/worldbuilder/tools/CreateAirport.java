package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Creates an airport of the world by its ICAO code. The data is taken the way ImportSelectedAirports takes it: the coordinates, the IATA code and the name from
 * the refdata library (the row of icaodata.csv when the library does not have the airport), the type from icaodata.csv (only a civil airport is created);
 * every value can be given by hand. The country of the airport is the given one, or the one of icaodata.csv if the world has a country with that name. The
 * airport can be linked with a city at once. See AdminGeoController and docs/city-reuse.md.
 */
public class CreateAirport {
    private static final Logger log = LoggerFactory.getLogger(CreateAirport.class);

    /**
     * What is known about the airport before the hand-given values are applied.
     *
     * @param type    the type of icaodata.csv, null when it has no row for the airport
     * @param country the country name of icaodata.csv, null when it has no row for the airport
     */
    public record AirportData(double latitude, double longitude, String iata, String name, String type, String country) {
    }

    /** The values which are given by hand, null is 'not given'. */
    public record Overrides(String name, String iata, Double latitude, Double longitude) {
        public static final Overrides NONE = new Overrides(null, null, null, null);
    }

    public static List<String> create(final World world,
                                      final String icao,
                                      final Overrides overrides,
                                      final String countryCode,
                                      final String linkCityName,
                                      final boolean dryRun) {
        return create(world, icao, overrides, countryCode, linkCityName, dryRun, CreateAirport::lookup);
    }

    /**
     * @param countryCode   the code of the country of the airport, null to take the country of icaodata.csv
     * @param linkCityName  the name of the city to link the airport with, null for no link (the exactly one city with the name should be in the world)
     * @param lookup        where the data of the airport comes from (a test replaces it)
     * @return the lines of the report, the last one is "Done" when the airport was created
     * @throws IllegalArgumentException the parameters are not acceptable
     */
    static List<String> create(final World world,
                               final String icao,
                               final Overrides overrides,
                               final String countryCode,
                               final String linkCityName,
                               final boolean dryRun,
                               final Function<String, Optional<AirportData>> lookup) {
        final String code = icao == null ? "" : icao.trim().toUpperCase();
        if (!code.matches("[A-Z0-9]{4}")) {
            throw new IllegalArgumentException("ICAO should be four letters or digits: " + icao);
        }
        world.airports().byIcao(code).ifPresent(existing -> {
            throw new IllegalArgumentException("Airport " + code + " exists already: " + existing.getName());
        });

        final Overrides given = overrides == null ? Overrides.NONE : overrides;
        // with the name and the coordinates given by hand the library and icaodata.csv are not asked at all
        final boolean everythingIsGiven = given.name() != null && !given.name().isBlank() && given.latitude() != null && given.longitude() != null;
        final Optional<AirportData> found = everythingIsGiven ? Optional.empty() : lookup.apply(code);
        if (found.isPresent() && found.get().type() != null && !"civil".equals(found.get().type())) {
            throw new IllegalArgumentException("Airport " + code + " is not civil (" + found.get().type() + "), it is not created");
        }
        final Double latitude = given.latitude() != null ? given.latitude() : found.map(AirportData::latitude).orElse(null);
        final Double longitude = given.longitude() != null ? given.longitude() : found.map(AirportData::longitude).orElse(null);
        String name = given.name() != null ? given.name().trim() : found.map(AirportData::name).orElse(null);
        if (latitude == null || longitude == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Airport " + code + " is not found in refdata and icaodata.csv, give name, lat and lon");
        }
        if (!(latitude >= -90 && latitude <= 90) || !(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("Coordinates are out of range: " + latitude + ", " + longitude);
        }
        final String iataRaw = given.iata() != null ? given.iata().trim().toUpperCase() : found.map(AirportData::iata).orElse("");
        final String iata = iataRaw == null || iataRaw.length() > 3 ? "" : iataRaw;

        final Optional<Countries.Country> country = resolveCountry(world, countryCode, found.map(AirportData::country).orElse(null));

        Cities.City linkCity = null;
        if (linkCityName != null && !linkCityName.isBlank()) {
            final List<Cities.City> cities = world.cities().all().filter(c -> c.getName().equalsIgnoreCase(linkCityName.trim())).toList();
            if (cities.size() != 1) {
                throw new IllegalArgumentException("Expected exactly one city named " + linkCityName + ", found " + cities.size());
            }
            linkCity = cities.get(0);
        }

        final List<String> report = new ArrayList<>();
        if (dryRun) {
            report.add("DRY RUN - nothing is changed");
            report.add("");
        }
        report.add("airport " + code + (iata.isEmpty() ? "" : " (" + iata + ")") + " '" + name + "' " + (float) latitude.doubleValue() + " " + (float) longitude.doubleValue()
                + (dryRun ? " should be created" : " created"));
        report.add(country.map(c -> "country " + c.getCode() + " " + c.getName()).orElse("country is unknown, set it in the world editor"));
        if (linkCity != null) {
            report.add("linked with city " + linkCity.getName() + (dryRun ? " (should be)" : ""));
        }
        final Geo.Coords coords = Geo.coords(latitude, longitude);
        final List<String> near = world.cities().all()
                .filter(c -> Geo.distance(c.getCoords(), coords) < 50)
                .map(c -> c.getName() + " " + Math.round(Geo.distance(c.getCoords(), coords)) + " nm")
                .toList();
        report.add("cities of the world within 50 nm (create-links links them): " + (near.isEmpty() ? "none" : String.join(", ", near)));

        if (!dryRun) {
            final Airports.Airport airport = world.airports().create(latitude, longitude, iata, code, name);
            country.ifPresent(c -> airport.setCountryId(c.getId()));
            if (linkCity != null) {
                world.airport2city().create(airport.getId(), linkCity.getId());
            }
            log.info("airport {} '{}' created, id {}, country {}, linked with {}", code, name, airport.getId(), country.map(Countries.Country::getCode).orElse("unknown"),
                    linkCity != null ? linkCity.getName() : "no city");
            report.add("");
            report.add("Done");
        }
        return report;
    }

    // the given code must be a country of the world; with no code the country name of icaodata.csv is looked for among the countries of the world
    private static Optional<Countries.Country> resolveCountry(final World world, final String countryCode, final String icaodataCountry) {
        if (countryCode != null && !countryCode.isBlank()) {
            final String code = countryCode.trim().toUpperCase();
            return Optional.of(world.countries().byCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("Country " + code + " not found, create it first")));
        }
        if (icaodataCountry == null) {
            return Optional.empty();
        }
        return world.countries().all().filter(c -> c.getName().equalsIgnoreCase(icaodataCountry.trim())).findFirst();
    }

    /** The data of the library (coordinates, IATA, name) and of icaodata.csv (type, country, and the coordinates and the name when the library has no airport). */
    static Optional<AirportData> lookup(final String icao) {
        String type = null;
        String country = null;
        Double rowLatitude = null;
        Double rowLongitude = null;
        String rowName = null;
        try {
            final String content = IOHelper.readInputStream(Objects.requireNonNull(CreateAirport.class.getResourceAsStream("/icaodata.csv")));
            final Csv airportsCsv = Csv.fromContent(content);
            for (int i = 0; i < airportsCsv.rowCount(); i++) {
                if (icao.equals(airportsCsv.value(i, 0))) {
                    rowLatitude = Double.parseDouble(airportsCsv.value(i, 1));
                    rowLongitude = Double.parseDouble(airportsCsv.value(i, 2));
                    type = airportsCsv.value(i, 3);
                    rowName = airportsCsv.value(i, 5);
                    country = airportsCsv.value(i, 8);
                    break;
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("unable to read icaodata.csv", e);
        }

        final Optional<net.simforge.refdata.airports.Airport> fromLibrary = net.simforge.refdata.airports.Airports.get().findByIcao(icao);
        if (fromLibrary.isPresent()) {
            final net.simforge.refdata.airports.Airport airport = fromLibrary.get();
            return Optional.of(new AirportData(airport.getCoords().getLat(), airport.getCoords().getLon(), airport.getIata(), airport.getName(), type, country));
        }
        if (rowLatitude != null) {
            return Optional.of(new AirportData(rowLatitude, rowLongitude, "", rowName, type, country));
        }
        return Optional.empty();
    }
}
