package net.simforge.airways2.app.admin;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AirportFacilities;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.CityFlows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the world editor shows about a city and an airport, and the removal of the business aviation terminal.
 */
class WorldEditorControllerTest {
    private World world;
    private Cities.City city;
    private Airports.Airport airport;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        city = world.cities().create(world.countries().create("TT", "Testland").getId(), "Alpha City", 10, 20, 1_000_000);
        airport = world.airports().create(10.1, 20.1, "AAA", "AAAA", "Alpha");
        world.airport2city().create(airport.getId(), city.getId());
        world.cityFlows().createMissingCityFlows();
    }

    @Test
    void theCityShowsItsCountryFlowAndAirports() {
        final WorldEditorController.CityInfoDto info = WorldEditorController.toCityInfo(world, city);

        assertEquals("Alpha City", info.getName());
        assertEquals("TT", info.getCountryCode());
        assertEquals("Testland", info.getCountry());
        assertEquals(1_000_000, info.getPopulation());
        assertEquals(1, info.getAttraction());
        assertEquals(1, info.getAirports().size());
        assertEquals("AAAA", info.getAirports().get(0).getIcao());
        assertFalse(info.getAirports().get(0).isBusinessTerminal());
    }

    @Test
    void aCityWithoutFlowHasNoAttraction() {
        final Cities.City other = world.cities().create(city.getCountryId(), "Beta City", 11, 21, 5);

        final WorldEditorController.CityInfoDto info = WorldEditorController.toCityInfo(world, other);

        assertNull(info.getAttraction());
        assertTrue(info.getAirports().isEmpty());
    }

    @Test
    void theAirportShowsItsCitiesAndFacilities() {
        world.airportFacilities().createIfAbsent(airport, AirportFacilities.Type.BusinessAviationTerminal);

        final WorldEditorController.AirportInfoDto info = WorldEditorController.toAirportInfo(world, airport);

        assertEquals("AAAA", info.getIcao());
        assertTrue(info.isBusinessTerminal());
        assertEquals(1, info.getCities().size());
        assertEquals("Alpha City", info.getCities().get(0).getName());
        assertEquals(1, info.getFacilities().size());
        assertEquals("BusinessAviationTerminal", info.getFacilities().get(0).getType());
        assertNull(info.getFacilities().get(0).getOperator());
    }

    @Test
    void theBusinessTerminalIsRemovedAndCreatedAgain() {
        world.airportFacilities().createIfAbsent(airport, AirportFacilities.Type.BusinessAviationTerminal);

        assertTrue(world.airportFacilities().delete(airport, AirportFacilities.Type.BusinessAviationTerminal));
        assertFalse(world.airportFacilities().hasFacility(airport, AirportFacilities.Type.BusinessAviationTerminal));
        assertFalse(WorldEditorController.toAirportInfo(world, airport).isBusinessTerminal());
        // nothing to remove the second time
        assertFalse(world.airportFacilities().delete(airport, AirportFacilities.Type.BusinessAviationTerminal));

        world.airportFacilities().createIfAbsent(airport, AirportFacilities.Type.BusinessAviationTerminal);
        assertTrue(world.airportFacilities().hasFacility(airport, AirportFacilities.Type.BusinessAviationTerminal));
    }

    @Test
    void anAirportIsLinkedByTheCodeAndTheLinkIsRemoved() {
        final Airports.Airport other = world.airports().create(10.5, 20.5, "BBB", "BBBB", "Beta");

        // the code is trimmed and the case does not matter
        final WorldEditorController.CityInfoDto linked = WorldEditorController.linkAirport(world, city, " bbbb ");

        assertEquals(java.util.List.of("AAAA", "BBBB"), linked.getAirports().stream().map(WorldEditorController.LinkedAirportDto::getIcao).toList());
        assertTrue(world.airport2city().byAirportIdAndCityId(other.getId(), city.getId()).isPresent());

        final WorldEditorController.CityInfoDto unlinked = WorldEditorController.unlinkAirport(world, city, "BBBB");

        assertEquals(java.util.List.of("AAAA"), unlinked.getAirports().stream().map(WorldEditorController.LinkedAirportDto::getIcao).toList());
        assertTrue(world.airport2city().byAirportIdAndCityId(other.getId(), city.getId()).isEmpty());
    }

    @Test
    void aLinkWhichExistsAlreadyAnUnknownAirportAndAMissingLinkAreRefused() {
        world.airports().create(10.5, 20.5, "BBB", "BBBB", "Beta");

        final org.springframework.web.server.ResponseStatusException exists = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class, () -> WorldEditorController.linkAirport(world, city, "AAAA"));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, exists.getStatus());

        final org.springframework.web.server.ResponseStatusException unknown = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class, () -> WorldEditorController.linkAirport(world, city, "EGL"));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, unknown.getStatus());

        final org.springframework.web.server.ResponseStatusException notLinked = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class, () -> WorldEditorController.unlinkAirport(world, city, "BBBB"));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, notLinked.getStatus());
    }

    @Test
    void theMobilityOfTheCityCanBeChangedAndTheFlowsAreCountedAgain() {
        final CityFlows.Flow flow = world.cityFlows().byCityId(city.getId()).orElseThrow();
        flow.setLastRedistributionTime(12345);

        final WorldEditorController.CityInfoDto info = WorldEditorController.applyMobility(world, city, 2.5f);

        assertEquals(2.5f, info.getMobility());
        assertEquals(0, info.getLastRedistributionTime());
        assertEquals(65.5f, WorldEditorController.applyMobility(world, city, 65.5f).getMobility());
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class, () -> WorldEditorController.applyMobility(world, city, 66f)).getStatus());
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class, () -> WorldEditorController.applyMobility(world, city, -1f)).getStatus());
    }

    @Test
    void theFlowCanBeChanged() {
        final CityFlows.Flow flow = world.cityFlows().byCityId(city.getId()).orElseThrow();
        flow.setAttractionFactor(4.5f);

        assertEquals(4.5f, WorldEditorController.toCityInfo(world, city).getAttraction());
    }
}
