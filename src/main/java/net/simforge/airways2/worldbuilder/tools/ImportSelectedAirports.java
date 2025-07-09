package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.refdata.airports.Airport;
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
            final Airport airport = net.simforge.refdata.airports.Airports.get().findByIcao(requestedIcao).orElseThrow();

            insertIfAbsent(world, airport, airportsCsv);
        }

        world.save();
    }

    private static void insertIfAbsent(final World world, final Airport airport, final Csv airportsCsv) {
        final String icao = airport.getIcao();

        final int row = findByIcao(airportsCsv, icao);
        final String type = row != -1 ? airportsCsv.value(row, 3) : "civil";

        if (!"civil".equals(type)) {
            return;
        }

        final Optional<Airports.Airport> airportByIcao = world.airports().byIcao(icao);
        if (airportByIcao.isEmpty()) {
            world.airports().create(
                    airport.getCoords().getLat(),
                    airport.getCoords().getLon(),
                    airport.getIata(),
                    icao,
                    airport.getName());
            log.info("\tAirport {} created", icao);
        }
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
}
