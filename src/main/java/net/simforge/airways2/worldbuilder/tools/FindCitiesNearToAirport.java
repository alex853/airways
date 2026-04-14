package net.simforge.airways2.worldbuilder.tools;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.commons.io.Csv;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.Str;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FindCitiesNearToAirport {
    public static void main(String[] args) throws IOException {
        String icao = "EDDS";
        int maxDistance = 65;

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

        List<CityInfo> found = new ArrayList<>();
        for (int row = 0; row < citiesCsv.rowCount(); row++) {
            final String countryName = citiesCsv.value(row, "CountryName");
            final String countryCode = citiesCsv.value(row, "CountryCode");

            final String cityName = citiesCsv.value(row, "CityName");
            final int cityPopulation = Integer.parseInt(citiesCsv.value(row, "CityPopulation"));
            final double cityLatitude = Double.parseDouble(citiesCsv.value(row, "CityLatitude"));
            final double cityLongitude = Double.parseDouble(citiesCsv.value(row, "CityLongitude"));

            Geo.Coords cityCoords = Geo.coords(cityLatitude, cityLongitude);

            double distance = Geo.distance(airportCoords, cityCoords);
            if (distance < maxDistance) {
                found.add(new CityInfo(cityName, cityPopulation, (int) distance));
            }
        }

        found.sort(Comparator.comparing(CityInfo::getPopulation).reversed());
        found.forEach(c -> System.out.println(Str.al(c.name, 30) + Str.ar(String.valueOf(c.population), 10) + Str.ar(String.valueOf(c.distance), 5)));
    }

    @Data
    @AllArgsConstructor
    private static class CityInfo {
        String name;
        int population;
        int distance;
    }
}
