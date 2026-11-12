package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Creates a city of the world with the given data, together with its city flow (the redistribution of the flows starts by itself). The airports are linked
 * after that (see CreateAirport and the create-links of AdminGeoController). For a city which is made out of an existing one see ReuseCity. See
 * docs/city-reuse.md.
 */
public class CreateCity {
    private static final Logger log = LoggerFactory.getLogger(CreateCity.class);

    public static final int MAX_POPULATION = 100_000_000;

    /**
     * @param countryName the name of the country to create when there is no country with the code in the world yet; not used when the country exists
     * @return the lines of the report, the last one is "Done" when the city was created
     * @throws IllegalArgumentException the parameters are not acceptable
     */
    public static List<String> create(final World world,
                                      final String name,
                                      final String countryCode,
                                      final String countryName,
                                      final double latitude,
                                      final double longitude,
                                      final int population,
                                      final boolean dryRun) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The name should not be empty");
        }
        final String cityName = name.trim();
        final String code = countryCode == null ? "" : countryCode.trim().toUpperCase();
        final Optional<Countries.Country> existingCountry = world.countries().byCode(code);
        final String newCountryName = countryName == null ? "" : countryName.trim();
        if (existingCountry.isEmpty() && (code.isEmpty() || newCountryName.isEmpty())) {
            throw new IllegalArgumentException("Country " + countryCode + " not found, give countryName to create it");
        }
        if (!(latitude >= -90 && latitude <= 90) || !(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("Coordinates are out of range: " + latitude + ", " + longitude);
        }
        if (population < 0 || population > MAX_POPULATION) {
            throw new IllegalArgumentException("Population should be from 0 to " + MAX_POPULATION);
        }
        existingCountry.flatMap(country -> world.cities().byCountryIdAndName(country.getId(), cityName)).ifPresent(other -> {
            throw new IllegalArgumentException("City " + cityName + " exists in " + code + " already, id " + other.getId());
        });

        final List<String> report = new ArrayList<>();
        if (dryRun) {
            report.add("DRY RUN - nothing is changed");
            report.add("");
        }
        if (existingCountry.isEmpty()) {
            report.add("country " + code + " does not exist, it will be created with the name " + newCountryName);
        }
        report.add("city " + cityName + " (" + code + ", " + (float) latitude + " " + (float) longitude + ", population " + population + ")"
                + (dryRun ? " should be created" : " created"));

        if (!dryRun) {
            final Countries.Country country = existingCountry.orElseGet(() -> {
                final Countries.Country created = world.countries().create(code, newCountryName);
                log.info("country {} '{}' created, id {}", code, newCountryName, created.getId());
                return created;
            });
            final Cities.City city = world.cities().create(country.getId(), cityName, latitude, longitude, population);
            world.cityFlows().createMissingCityFlows();
            log.info("city {} '{}' created ({}, {} {}, population {})", city.getId(), cityName, code, (float) latitude, (float) longitude, population);
            report.add("city id " + city.getId());
            report.add("");
            report.add("Done");
        }
        return report;
    }
}
