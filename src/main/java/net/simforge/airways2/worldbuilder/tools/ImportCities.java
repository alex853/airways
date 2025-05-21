package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.world.World;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ImportCities {
    private static final Logger logger = LoggerFactory.getLogger(ImportCities.class.getName());

    public static void main(final String[] args) throws IOException {
        final World world = World25.load();
        final Countries countries = world.countries();
        final Cities cities = world.cities();

        final String content = IOHelper.readInputStream(
                Objects.requireNonNull(
                        ImportCities.class.getResourceAsStream("/city-population.csv")));
        final Csv csv = Csv.fromContent(content);

        final List<Filter> filters = toFilters(args);

        for (int row = 0; row < csv.rowCount(); row++) {
            if (!check(filters, csv, row)) {
                continue;
            }

            final String countryName = csv.value(row, "CountryName");
            final String countryCode = csv.value(row, "CountryCode");

            final String cityName = csv.value(row, "CityName");
            final int cityPopulation = Integer.parseInt(csv.value(row, "CityPopulation"));
            final double cityLatitude = Double.parseDouble(csv.value(row, "CityLatitude"));
            final double cityLongitude = Double.parseDouble(csv.value(row, "CityLongitude"));

            logger.info("Processing {}, {} -> {}, {}", countryName, countryCode, cityName, cityPopulation);

            final Optional<Countries.Country> existingCountry = countries.byCode(countryCode);
            final int countryId = existingCountry.map(Countries.Country::getId)
                    .orElseGet(() -> countries.create(countryCode, countryName).getId());

            final Optional<Cities.City> existingCity = cities.byCountryIdAndName(countryId, cityName);
            if (existingCity.isPresent()) {
                continue;
            }

            cities.create(countryId,
                    cityName,
                    cityLatitude,
                    cityLongitude,
                    cityPopulation);
            logger.info("\tCity {} created", cityName);
        }

        world.save();
    }

    private static List<Filter> toFilters(final String[] args) {
        final List<Filter> filters = new ArrayList<>();

        for (final String arg : args) {
            if (arg.startsWith("country-code:")) {
                filters.add(new CountryCodeFilter(arg.substring("country-code:".length())));
            } else if (arg.startsWith("min-population:")) {
                filters.add(new MinPopulationFilter(Integer.parseInt(arg.substring("min-population:".length()))));
            }
        }

        return filters;
    }

    private static boolean check(final List<Filter> filters, final Csv csv, final int row) {
        for (final Filter filter : filters) {
            if (!filter.check(csv, row)) {
                return false;
            }
        }
        return true;
    }

    private interface Filter {
        boolean check(Csv csv, int row);
    }

    private static class CountryCodeFilter implements Filter {
        private final String countryCode;

        public CountryCodeFilter(final String countryCode) {
            this.countryCode = countryCode;
        }

        @Override
        public boolean check(final Csv csv, final int row) {
            return countryCode.equalsIgnoreCase(csv.value(row, "CountryCode"));
        }
    }

    private static class MinPopulationFilter implements Filter {
        private final int minPopulation;

        public MinPopulationFilter(final int minPopulation) {
            this.minPopulation = minPopulation;
        }

        @Override
        public boolean check(final Csv csv, final int row) {
            return minPopulation <= Integer.parseInt(csv.value(row, "CityPopulation"));
        }
    }
}
