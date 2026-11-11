package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.CityFlows;
import net.simforge.airways2.world.processors.CityFlowHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReuseCityTest {
    private World world;
    private Cities.City city;
    private Airports.Airport shared;
    private Airports.Airport own;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final int us = world.countries().create("US", "United States").getId();
        world.countries().create("IN", "India");
        city = world.cities().create(us, "Coral Springs", 26.27, -80.26, 128_700);
        final Cities.City neighbour = world.cities().create(us, "Fort Lauderdale", 26.14, -80.14, 1_500_000);
        shared = world.airports().create(26.07, -80.15, "FLL", "KFLL", "Fort Lauderdale Hollywood");
        own = world.airports().create(26.3, -80.2, "XXX", "KXXX", "Own");
        world.airport2city().create(shared.getId(), city.getId());
        world.airport2city().create(shared.getId(), neighbour.getId());
        world.airport2city().create(own.getId(), city.getId());
        world.cityFlows().createMissingCityFlows();
        final CityFlows.Flow flow = world.cityFlows().byCityId(city.getId()).orElseThrow();
        flow.setAttractionFactor(5);
        flow.setMobilityFactor(3);
        flow.setLastRedistributionTime(12345);
    }

    @Test
    void dryRunChangesNothingAndReports() {
        final List<String> report = ReuseCity.reuse(world, city.getId(), "Bengaluru", "in", null, 12.97, 77.56, 13_000_000, true);

        assertEquals("DRY RUN - nothing is changed", report.get(0));
        assertTrue(report.stream().anyMatch(line -> line.contains("Coral Springs") && line.contains("Bengaluru")), report.toString());
        assertTrue(report.stream().anyMatch(line -> line.contains("unlink airport KXXX (it has no other city after that)")), report.toString());
        assertTrue(report.stream().anyMatch(line -> line.contains("unlink airport KFLL") && !line.contains("no other city")), report.toString());
        assertEquals("Coral Springs", city.getName());
        assertEquals(128_700, city.getPopulation());
        assertEquals(2, world.airport2city().allByCityId(city.getId()).count());
        assertEquals(12345, world.cityFlows().byCityId(city.getId()).orElseThrow().getLastRedistributionTime());
    }

    @Test
    void theCityBecomesAnotherCityAndKeepsItsId() {
        final int id = city.getId();

        final List<String> report = ReuseCity.reuse(world, id, " Bengaluru ", "IN", null, 12.97, 77.56, 13_000_000, false);

        assertEquals("Done", report.get(report.size() - 1));
        final Cities.City reused = world.cities().byId(id).orElseThrow();
        assertEquals("Bengaluru", reused.getName());
        assertEquals(world.countries().byCode("IN").orElseThrow().getId(), reused.getCountryId());
        assertEquals(12.97f, reused.getLatitude(), 0.001);
        assertEquals(77.56f, reused.getLongitude(), 0.001);
        assertEquals(13_000_000, reused.getPopulation());
        assertEquals(0, world.airport2city().allByCityId(id).count());
        // the shared airport still serves the other city
        assertEquals(1, world.airport2city().allByAirportId(shared.getId()).count());
        assertEquals(0, world.airport2city().allByAirportId(own.getId()).count());

        final CityFlows.Flow flow = world.cityFlows().byCityId(id).orElseThrow();
        assertEquals(CityFlowHelper.DEFAULT_ATTRACTION_FACTOR, flow.getAttractionFactor());
        assertEquals(CityFlowHelper.DEFAULT_MOBILITY_FACTOR, flow.getMobilityFactor());
        assertEquals(0, flow.getLastRedistributionTime());
    }

    @Test
    void aCountryWhichIsNotInTheWorldIsCreatedWhenItsNameIsGiven() {
        final int countries = (int) world.countries().all().count();

        final List<String> dryRun = ReuseCity.reuse(world, city.getId(), "Guadeloupe", "gp", "Guadeloupe", 16.24, -61.53, 384_000, true);
        assertTrue(dryRun.stream().anyMatch(line -> line.contains("country GP does not exist, it will be created with the name Guadeloupe")), dryRun.toString());
        assertTrue(world.countries().byCode("GP").isEmpty());
        assertEquals(countries, world.countries().all().count());

        ReuseCity.reuse(world, city.getId(), "Guadeloupe", "gp", "Guadeloupe", 16.24, -61.53, 384_000, false);
        final int countryId = world.countries().byCode("GP").orElseThrow().getId();
        assertEquals("Guadeloupe", world.countries().byId(countryId).orElseThrow().getName());
        assertEquals(countryId, city.getCountryId());
        assertEquals(countries + 1, world.countries().all().count());

        // the country exists now: the name is not needed
        ReuseCity.reuse(world, city.getId(), "Guadeloupe", "GP", null, 16.24, -61.53, 384_000, false);
        assertEquals(countries + 1, world.countries().all().count());
    }

    @Test
    void aCountryWhichIsNotInTheWorldWithoutAnameIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ReuseCity.reuse(world, city.getId(), "Guadeloupe", "GP", " ", 16.24, -61.53, 1, false));
        assertTrue(world.countries().byCode("GP").isEmpty());
    }

    @Test
    void aNameWhichExistsInTheCountryIsRefused() {
        final int us = world.countries().byCode("US").orElseThrow().getId();
        world.cities().create(us, "Miami", 25.77, -80.19, 3_000_000);

        assertThrows(IllegalArgumentException.class, () -> ReuseCity.reuse(world, city.getId(), "Miami", "US", null, 25.0, -80.0, 1, false));
        assertEquals("Coral Springs", city.getName());
    }

    @Test
    void unacceptableParametersAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ReuseCity.reuse(world, city.getId(), "Bengaluru", "ZZ", null, 12.97, 77.56, 1, false));
        assertThrows(IllegalArgumentException.class, () -> ReuseCity.reuse(world, city.getId(), "Bengaluru", "IN", null, 112.97, 77.56, 1, false));
        assertThrows(IllegalArgumentException.class, () -> ReuseCity.reuse(world, city.getId(), " ", "IN", null, 12.97, 77.56, 1, false));
        assertThrows(IllegalArgumentException.class, () -> ReuseCity.reuse(world, city.getId(), "Bengaluru", "IN", null, 12.97, 77.56, -1, false));
        assertThrows(IllegalArgumentException.class, () -> ReuseCity.reuse(world, 9999, "Bengaluru", "IN", null, 12.97, 77.56, 1, false));
        assertEquals("Coral Springs", city.getName());
    }
}
