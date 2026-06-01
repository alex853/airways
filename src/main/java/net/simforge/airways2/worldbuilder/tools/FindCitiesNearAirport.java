package net.simforge.airways2.worldbuilder.tools;

import net.simforge.commons.io.Csv;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.Str;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

public class FindCitiesNearAirport {
    public static void main(String[] args) throws IOException {
        String icao = "KCLT";

        Geo.Coords airportCoords = null;

        Csv airportsCsv = ImportMajorAirportsInVicinityOfCities.loadAirportCsv();
        for (int row = 0; row < airportsCsv.rowCount(); row++) {
            String icao1 = airportsCsv.value(row, 0);
            if (!icao1.equals(icao)) {
                continue;
            }

            String latStr = airportsCsv.value(row, 1);
            String lonStr = airportsCsv.value(row, 2);
            airportCoords = Geo.coords(Double.parseDouble(latStr), Double.parseDouble(lonStr));
        }

        if (airportCoords == null) {
            System.err.println("Unable to find airport by icao");
            return;
        }

        Csv citiesCsv = ImportCities.loadCityPopulationCsv();

        List<ImportCities.CityInfo> found = ImportCities.getCitiesNearAirport(citiesCsv, airportCoords, 70);

        found.sort(Comparator.comparing(ImportCities.CityInfo::getPopulation).reversed());
        System.out.println("Cities close to " + icao);
        found.forEach(c -> System.out.println(Str.al(c.name, 30) + Str.ar(String.valueOf(c.population), 10) + Str.ar(String.valueOf(c.distance), 5)));
    }
}
