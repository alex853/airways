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
import java.util.Arrays;
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

        Arrays.stream(args)
                .forEach(requestedIcao -> insertIfAbsent(world, requestedIcao, airportsCsv));

        world.save();
    }

    private static void insertIfAbsent(final World world, final String icao, final Csv airportsCsv) {
        final int rowInCsv = findByIcaoInCsv(airportsCsv, icao);
        final String type = rowInCsv != -1 ? airportsCsv.value(rowInCsv, 3) : "civil";

        if (!"civil".equals(type)) {
            return;
        }

        final Optional<Airports.Airport> airportByIcao = world.airports().byIcao(icao);
        if (airportByIcao.isEmpty()) {
            final Airport airport = net.simforge.refdata.airports.Airports.get().findByIcao(icao).orElseThrow();

            world.airports().create(
                    airport.getCoords().getLat(),
                    airport.getCoords().getLon(),
                    airport.getIata(),
                    icao,
                    airport.getName());
            log.info("\tAirport {} created", icao);
        }
    }

    private static int findByIcaoInCsv(final Csv airportsCsv, final String requestedIcao) {
        for (int i = 0; i < airportsCsv.rowCount(); i++) {
            final String icao = airportsCsv.value(i, 0);
            if (requestedIcao.equals(icao)) {
                return i;
            }
        }
        return -1;
    }
}
