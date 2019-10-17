/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder;

import net.simforge.airways.model.geo.City;
import net.simforge.airways.model.geo.Country;
import net.simforge.commons.io.Csv;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImportCityPopulation {
    public static void main(String[] args) throws IOException {
        Csv csv = Csv.load(new File("./data/city-population.csv"));

        List<Filter> filters = toFilters(args);

        for (int row = 0; row < csv.rowCount(); row++) {
            Country country = new Country();
            country.setName(csv.value(row, "CountryName"));
            country.setCode(csv.value(row, "CountryCode"));

            City city = new City();
            city.setName(csv.value(row, "CityName"));
            city.setPopulation(Integer.valueOf(csv.value(row, "CityPopulation")));
            city.setLatitude(Double.valueOf(csv.value(row, "CityLatitude")));
            city.setLongitude(Double.valueOf(csv.value(row, "CityLongitude")));

            if (!check(filters, country, city)) {
                continue;
            }

            // put it into database
            System.out.println(country + "\t\t\t" + city);
        }
    }

    private static List<Filter> toFilters(String[] args) {
        List<Filter> filters = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("country-code:")) {
                filters.add(new CountryCodeFilter(arg.substring("country-code:".length())));
            } else if (arg.startsWith("min-population:")) {
                filters.add(new MinPopulationFilter(Integer.valueOf(arg.substring("min-population:".length()))));
            }
        }

        return filters;
    }

    private static boolean check(List<Filter> filters, Country country, City city) {
        for (Filter filter : filters) {
            if (!filter.check(country, city)) {
                return false;
            }
        }
        return true;
    }

    private interface Filter {
        boolean check(Country country, City city);
    }

    private static class CountryCodeFilter implements Filter {
        private String countryCode;

        public CountryCodeFilter(String countryCode) {
            this.countryCode = countryCode;
        }

        @Override
        public boolean check(Country country, City city) {
            return country.getCode().equalsIgnoreCase(countryCode);
        }
    }

    private static class MinPopulationFilter implements Filter {
        private int minPopulation;

        public MinPopulationFilter(int minPopulation) {
            this.minPopulation = minPopulation;
        }

        @Override
        public boolean check(Country country, City city) {
            return city.getPopulation() >= minPopulation;
        }
    }
}
