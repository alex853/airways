package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.worldbuilder.World25;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class RenameAirports {
    private static final Logger log = LoggerFactory.getLogger(RenameAirports.class);

    /**
     * Renames existing airports of the world, it is idempotent.
     *
     * @param renames items are {icao, newName}
     */
    public static void renameAirports(final List<String[]> renames) throws IOException {
        final World world = World25.load();

        for (final String[] rename : renames) {
            final String icao = rename[0];
            final String newName = rename[1];

            final Optional<Airports.Airport> airport = world.airports().byIcao(icao);
            if (airport.isEmpty()) {
                log.warn("Rename airport {} to '{}' - airport not found, skipped", icao, newName);
                continue;
            }

            if (newName.equals(airport.get().getName())) {
                log.info("Rename airport {} to '{}' - already has the name, skipped", icao, newName);
                continue;
            }

            final String oldName = airport.get().getName();
            airport.get().setName(newName);
            log.info("Rename airport {} from '{}' to '{}' - DONE", icao, oldName, newName);
        }

        world.save();
    }
}
