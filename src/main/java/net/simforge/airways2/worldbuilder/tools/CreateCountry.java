package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Countries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a country of the world (an island, an overseas territory, a state which is not in the world yet). See AdminGeoController and docs/city-reuse.md.
 */
public class CreateCountry {
    private static final Logger log = LoggerFactory.getLogger(CreateCountry.class);

    // the countryId of a city and of an airport is stored in one byte
    private static final int MAX_COUNTRIES = 255;

    /**
     * @param code two letters, as in the city csv (ISO 3166-1 alpha-2)
     * @param name the proper English name (see city-airport-playbook.md, the names)
     * @return the lines of the report, the last one is "Done" when the country was created
     * @throws IllegalArgumentException the parameters are not acceptable
     */
    public static List<String> create(final World world, final String code, final String name, final boolean dryRun) {
        final String normalizedCode = code == null ? "" : code.trim().toUpperCase();
        if (!normalizedCode.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("The code should be two letters: " + code);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The name should not be empty");
        }
        final String normalizedName = name.trim();
        world.countries().byCode(normalizedCode).ifPresent(existing -> {
            throw new IllegalArgumentException("Country " + normalizedCode + " exists already: " + existing.getName());
        });
        world.countries().all().filter(c -> c.getName().equalsIgnoreCase(normalizedName)).findFirst().ifPresent(existing -> {
            throw new IllegalArgumentException("Country named " + normalizedName + " exists already with the code " + existing.getCode());
        });
        if (world.countries().all().count() >= MAX_COUNTRIES) {
            throw new IllegalArgumentException("There are " + MAX_COUNTRIES + " countries already, it is the most the one-byte countryId allows");
        }

        final List<String> report = new ArrayList<>();
        if (dryRun) {
            report.add("DRY RUN - nothing is changed");
            report.add("");
        }
        report.add("country " + normalizedCode + " '" + normalizedName + "'" + (dryRun ? " should be created" : " created"));

        if (!dryRun) {
            final Countries.Country country = world.countries().create(normalizedCode, normalizedName);
            log.info("country {} '{}' created, id {}", normalizedCode, normalizedName, country.getId());
            report.add("");
            report.add("Done");
        }
        return report;
    }
}
