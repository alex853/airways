package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airport2City;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.CityFlows;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.processors.CityFlowHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The reuse of a city: a city which was created in a wrong place (or hastily) becomes another city, instead of being deleted. The record keeps its id, so
 * everything which refers to the id (the flows, the journeys, the people) stays valid, but now means another place. The procedure changes the name, the
 * country, the coordinates and the population, removes all the links with the airports (the airports of the new place are linked after that, by hand or by
 * the usual tools), returns the attraction and the mobility to the defaults and starts the redistribution of the flows of the city again, so the flows
 * of the old neighbours are replaced with the flows of the new ones. The journeys and the people who are in the city at the moment stay in it (the report
 * says how many). See docs/city-reuse.md.
 */
public class ReuseCity {
    private static final Logger log = LoggerFactory.getLogger(ReuseCity.class);

    /**
     * @param countryName the name of the country to create when there is no country with the code in the world yet (an island, an overseas territory); it is
     *                    not used when the country exists, and may be empty then
     * @param dryRun nothing is changed, only the report is made
     * @return the lines of the report: what the city is, what is going to be changed (was changed) and what stays attached to it
     * @throws IllegalArgumentException the parameters are not acceptable
     */
    public static List<String> reuse(final World world,
                                     final int cityId,
                                     final String newName,
                                     final String countryCode,
                                     final String countryName,
                                     final double latitude,
                                     final double longitude,
                                     final int population,
                                     final boolean dryRun) {
        final Cities.City city = world.cities().byId(cityId)
                .orElseThrow(() -> new IllegalArgumentException("City " + cityId + " not found"));
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("The new name should not be empty");
        }
        final String code = countryCode == null ? "" : countryCode.trim().toUpperCase();
        final Optional<Countries.Country> existingCountry = world.countries().byCode(code);
        final String newCountryName = countryName == null ? "" : countryName.trim();
        if (existingCountry.isEmpty() && (code.isEmpty() || newCountryName.isEmpty())) {
            throw new IllegalArgumentException("Country " + countryCode + " not found, give countryName to create it");
        }
        if (!(latitude >= -90 && latitude <= 90) || !(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("Coordinates are out of range: " + latitude + ", " + longitude);
        }
        if (population < 0) {
            throw new IllegalArgumentException("Population should not be negative");
        }
        final String name = newName.trim();
        existingCountry.flatMap(country -> world.cities().byCountryIdAndName(country.getId(), name))
                .filter(other -> other.getId() != cityId)
                .ifPresent(other -> {
                    throw new IllegalArgumentException("City " + name + " exists in " + code + " already, id " + other.getId());
                });

        final List<String> report = new ArrayList<>();
        if (dryRun) {
            report.add("DRY RUN - nothing is changed");
            report.add("");
        }

        final String oldCountry = world.countries().byId(city.getCountryId()).map(Countries.Country::getCode).orElse("?");
        report.add("city " + cityId + ": " + city.getName() + " (" + oldCountry + ", " + city.getLatitude() + " " + city.getLongitude()
                + ", population " + city.getPopulation() + ") -> " + name + " (" + code + ", " + (float) latitude + " " + (float) longitude
                + ", population " + population + ")");

        if (existingCountry.isEmpty()) {
            report.add("country " + code + " does not exist, it will be created with the name " + newCountryName);
        }

        final List<Airport2City.Link> links = world.airport2city().allByCityId(cityId).toList();
        for (final Airport2City.Link link : links) {
            final Airports.Airport airport = world.airports().byId(link.getAirportId()).orElseThrow();
            final long citiesOfAirport = world.airport2city().allByAirportId(airport.getId()).count();
            report.add("unlink airport " + airport.getIcao() + (citiesOfAirport <= 1 ? " (it has no other city after that)" : ""));
        }
        if (links.isEmpty()) {
            report.add("no linked airports");
        }

        final long flowsFrom = world.city2cityFlows().allFromCityId(cityId).filter(City2CityFlows.Flow::isActive).count();
        final long flowsTo = world.city2cityFlows().all().filter(f -> f.getToCityId() == cityId && f.isActive()).count();
        report.add("active c2c flows from the city " + flowsFrom + ", to the city " + flowsTo
                + " (they are recalculated by the redistribution of the flows, the ones which are not reachable become inactive)");

        final Map<Journeys.Status, Integer> journeys = new TreeMap<>();
        world.journeys().filter(world.journeys().byLocationCityIdIn(List.of(cityId)))
                .forEach(j -> journeys.merge(j.getStatus(), j.getGroupSize(), Integer::sum));
        report.add("journeys located in the city, passengers by statuses: " + (journeys.isEmpty() ? "none" : journeys));
        final long people = world.persons().all()
                .filter(p -> p.getLocationType() == Persons.LocationType.City && p.getLocationId() == cityId)
                .count();
        final long pilots = world.persons().all()
                .filter(p -> p.getLocationType() == Persons.LocationType.City && p.getLocationId() == cityId && p.getUserId() != 0)
                .count();
        report.add("people located in the city " + people + ", of them players " + pilots);

        final CityFlows.Flow flow = world.cityFlows().byCityId(cityId).orElse(null);
        if (flow != null) {
            report.add("attraction " + flow.getAttractionFactor() + ", mobility " + flow.getMobilityFactor() + " -> defaults "
                    + CityFlowHelper.DEFAULT_ATTRACTION_FACTOR + ", " + CityFlowHelper.DEFAULT_MOBILITY_FACTOR + "; redistribution of the flows is started again");
        }

        if (dryRun) {
            return report;
        }

        final Countries.Country country = existingCountry.orElseGet(() -> {
            final Countries.Country created = world.countries().create(code, newCountryName);
            log.info("country {} '{}' created", code, newCountryName);
            return created;
        });
        final String oldName = city.getName();
        for (final Airport2City.Link link : links) {
            world.airport2city().delete(link.getAirportId(), cityId);
        }
        city.setName(name);
        city.setCountryId(country.getId());
        city.setCoords(latitude, longitude);
        city.setPopulation(population);
        if (flow != null) {
            flow.setAttractionFactor(CityFlowHelper.DEFAULT_ATTRACTION_FACTOR);
            flow.setMobilityFactor(CityFlowHelper.DEFAULT_MOBILITY_FACTOR);
            flow.setLastRedistributionTime(0);
        }
        log.info("city {} reused - '{}' is now '{}' ({}, {} {}, population {}), {} airport links removed", cityId, oldName, name, code,
                (float) latitude, (float) longitude, population, links.size());
        report.add("");
        report.add("Done");
        return report;
    }
}
