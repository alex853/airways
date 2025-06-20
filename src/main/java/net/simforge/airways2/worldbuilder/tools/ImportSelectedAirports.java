package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class ImportSelectedAirports {
    private static final Logger log = LoggerFactory.getLogger(ImportSelectedAirports.class);

    public static void main(final String[] args) throws IOException {
        final World world = World25.load();

        final String content = IOHelper.readInputStream(
                Objects.requireNonNull(
                        ImportSelectedAirports.class.getResourceAsStream("/icaodata.csv")));
        final Csv airportsCsv = Csv.fromContent(content);

        for (final String requestedIcao : args) {
            final int row = findByIcao(airportsCsv, requestedIcao);
            if (row == -1) {
                continue;
            }

            insertIfAbsent(world, airportsCsv, row);
        }

        world.save();
    }

    private static int findByIcao(final Csv airportsCsv, final String requestedIcao) {
        for (int i = 0; i < airportsCsv.rowCount(); i++) {
            final String icao = airportsCsv.value(i, 0);
            if (requestedIcao.equals(icao)) {
                return i;
            }
        }
        return -1;
    }

    private static void insertIfAbsent(final World world, final Csv airportsCsv, final int row) {
        final String icao = airportsCsv.value(row, 0);
        final String latStr = airportsCsv.value(row, 1);
        final String lonStr = airportsCsv.value(row, 2);
        final String type = airportsCsv.value(row, 3);
        final String name = airportsCsv.value(row, 5);

        if (!"civil".equals(type)) {
            return;
        }

        final Optional<Airports.Airport> airportByIcao = world.airports().byIcao(icao);
        if (airportByIcao.isEmpty()) {
            world.airports().create(
                    Double.parseDouble(latStr),
                    Double.parseDouble(lonStr),
                    null, // todo ak3 iata to be added
                    icao,
                    name);
            log.info("\tAirport {} created", icao);
        }
    }
}
