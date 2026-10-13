package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.worldbuilder.World25;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class RenameCities {
    private static final Logger log = LoggerFactory.getLogger(RenameCities.class);

    /**
     * Renames existing cities of the world, it is idempotent.
     *
     * @param renames items are {countryCode, oldName, newName}
     */
    public static void renameCities(final List<String[]> renames) throws IOException {
        final World world = World25.load();

        for (final String[] rename : renames) {
            final String countryCode = rename[0];
            final String oldName = rename[1];
            final String newName = rename[2];

            final Optional<Countries.Country> country = world.countries().byCode(countryCode);
            if (country.isEmpty()) {
                log.warn("Rename city '{}' to '{}' - country {} not found, skipped", oldName, newName, countryCode);
                continue;
            }
            final int countryId = country.get().getId();

            if (world.cities().byCountryIdAndName(countryId, newName).isPresent()) {
                log.info("Rename city '{}' to '{}' - city with the new name already exists, skipped", oldName, newName);
                continue;
            }

            final Optional<Cities.City> city = world.cities().byCountryIdAndName(countryId, oldName);
            if (city.isEmpty()) {
                log.info("Rename city '{}' to '{}' - city with the old name not found, skipped", oldName, newName);
                continue;
            }

            city.get().setName(newName);
            log.info("Rename city '{}' to '{}' - DONE", oldName, newName);
        }

        world.save();
    }
}
