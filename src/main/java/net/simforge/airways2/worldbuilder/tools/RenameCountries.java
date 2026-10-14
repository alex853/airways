package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.worldbuilder.World25;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class RenameCountries {
    private static final Logger log = LoggerFactory.getLogger(RenameCountries.class);

    /**
     * Renames existing countries of the world, it is idempotent.
     *
     * @param renames items are {countryCode, newName}
     */
    public static void renameCountries(final List<String[]> renames) throws IOException {
        final World world = World25.load();

        for (final String[] rename : renames) {
            final String code = rename[0];
            final String newName = rename[1];

            final Optional<Countries.Country> country = world.countries().byCode(code);
            if (country.isEmpty()) {
                log.warn("Rename country {} to '{}' - country not found, skipped", code, newName);
                continue;
            }

            if (newName.equals(country.get().getName())) {
                log.info("Rename country {} to '{}' - already has the name, skipped", code, newName);
                continue;
            }

            final String oldName = country.get().getName();
            country.get().setName(newName);
            log.info("Rename country {} from '{}' to '{}' - DONE", code, oldName, newName);
        }

        world.save();
    }
}
