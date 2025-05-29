package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.datamodel.Airport2City;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.World;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class ImportMajorAirportsInVicinityOfCities {
    private static final Logger logger = LoggerFactory.getLogger(ImportMajorAirportsInVicinityOfCities.class.getName());

    public static void main(final String[] args) throws IOException {
        final World world = World25.load();
        final Cities cities = world.cities();
        final Airports airports = world.airports();
        final Airport2City airport2city = world.airport2city();

        final String content = IOHelper.readInputStream(
                Objects.requireNonNull(
                        ImportCities.class.getResourceAsStream("/icaodata.csv")));
        final Csv airportsCsv = Csv.fromContent(content);
        for (int i = 0; i < airportsCsv.rowCount(); i++) {
            String icao = airportsCsv.value(i, 0);
            String latStr = airportsCsv.value(i, 1);
            String lonStr = airportsCsv.value(i, 2);
            String type = airportsCsv.value(i, 3);
            String sizeStr = airportsCsv.value(i, 4);
            String name = airportsCsv.value(i, 5);

            if (!"civil".equals(type)) {
                continue;
            }

            if (Integer.parseInt(sizeStr) < 5000) {
                continue;
            }

            // todo ak3 check airport presence by iata

            final Geo.Coords airportCoords = Geo.coords(Double.parseDouble(latStr), Double.parseDouble(lonStr));

            cities.all().stream()
                    .filter(city -> Geo.distance(airportCoords, Geo.coords(city.getLatitude(), city.getLongitude())) < 50)
                    .forEach(city -> {
                        final Airports.Airport airport;
                        final Optional<Airports.Airport> airportByIcao = airports.byIcao(icao);
                        if (airportByIcao.isEmpty()) {
                            airport = airports.create(
                                    airportCoords.getLat(),
                                    airportCoords.getLon(),
                                    null, // todo ak3 iata to be added
                                    icao,
                                    name);
                            logger.info("\tAirport {} created", icao);
                        } else {
                            airport = airportByIcao.get();
                        }

                        final Optional<Airport2City.Link> link = airport2city.byAirportIdAndCityId(airport.getId(), city.getId());
                        if (link.isEmpty()) {
                            airport2city.create(airport.getId(), city.getId());
                        }
                    });
        }

        world.save();
    }
}
