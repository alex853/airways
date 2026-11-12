package net.simforge.airways2.worldbuilder.tools;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The creation of a country, a city and an airport by the admin: the dry run changes nothing, the refusals change nothing.
 */
class CreateGeoTest {
    private World world;
    private Cities.City existing;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final int eg = world.countries().create("EG", "Egypt").getId();
        existing = world.cities().create(eg, "Hurghada", 27.23, 33.83, 290_200);
        world.cityFlows().createMissingCityFlows();
    }

    @Test
    void aCountryIsCreatedWithTheNormalizedCode() {
        final List<String> dryRun = CreateCountry.create(world, "gp", "Guadeloupe", true);
        assertEquals("DRY RUN - nothing is changed", dryRun.get(0));
        assertTrue(world.countries().byCode("GP").isEmpty());

        final List<String> report = CreateCountry.create(world, " gp ", " Guadeloupe ", false);
        assertEquals("Done", report.get(report.size() - 1));
        assertEquals("Guadeloupe", world.countries().byCode("GP").orElseThrow().getName());
    }

    @Test
    void aCountryWithAnExistingCodeOrNameOrABadCodeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> CreateCountry.create(world, "EG", "Another", false));
        assertThrows(IllegalArgumentException.class, () -> CreateCountry.create(world, "EX", "egypt", false));
        assertThrows(IllegalArgumentException.class, () -> CreateCountry.create(world, "E", "Short", false));
        assertThrows(IllegalArgumentException.class, () -> CreateCountry.create(world, "EGY", "Long", false));
        assertThrows(IllegalArgumentException.class, () -> CreateCountry.create(world, "XX", " ", false));
        assertEquals(1, world.countries().all().count());
    }

    @Test
    void aCityIsCreatedWithItsCityFlow() {
        final List<String> dryRun = CreateCity.create(world, "Aswan", "eg", null, 24.08, 32.92, 350_500, true);
        assertEquals("DRY RUN - nothing is changed", dryRun.get(0));
        assertTrue(world.cities().byCountryIdAndName(existing.getCountryId(), "Aswan").isEmpty());

        final List<String> report = CreateCity.create(world, " Aswan ", "eg", null, 24.08, 32.92, 350_500, false);

        assertEquals("Done", report.get(report.size() - 1));
        final Cities.City aswan = world.cities().byCountryIdAndName(existing.getCountryId(), "Aswan").orElseThrow();
        assertEquals(350_500, aswan.getPopulation());
        assertEquals(24.08f, aswan.getLatitude(), 0.001);
        assertEquals(32.92f, aswan.getLongitude(), 0.001);
        assertTrue(world.cityFlows().byCityId(aswan.getId()).isPresent(), "the city flow is created with the city");
    }

    @Test
    void aCityCreatesTheCountryWhenItsNameIsGiven() {
        CreateCity.create(world, "Guadeloupe", "gp", "Guadeloupe", 16.24, -61.53, 384_000, false);

        final int countryId = world.countries().byCode("GP").orElseThrow().getId();
        assertEquals(countryId, world.cities().byCountryIdAndName(countryId, "Guadeloupe").orElseThrow().getCountryId());
        assertThrows(IllegalArgumentException.class, () -> CreateCity.create(world, "Martinique", "MQ", null, 14.6, -61.0, 360_000, false));
        assertTrue(world.countries().byCode("MQ").isEmpty());
    }

    @Test
    void aCityWithABadParameterOrAnExistingNameIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> CreateCity.create(world, "Hurghada", "EG", null, 27.0, 33.0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> CreateCity.create(world, "Luxor", "EG", null, 91.0, 33.0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> CreateCity.create(world, "Luxor", "EG", null, 25.0, 33.0, -1, false));
        assertThrows(IllegalArgumentException.class, () -> CreateCity.create(world, "Luxor", "EG", null, 25.0, 33.0, 100_000_001, false));
        assertThrows(IllegalArgumentException.class, () -> CreateCity.create(world, " ", "EG", null, 25.0, 33.0, 1, false));
        assertEquals(1, world.cities().all().count());
    }

    private static final Function<String, Optional<CreateAirport.AirportData>> LIBRARY = icao -> switch (icao) {
        case "HESN" -> Optional.of(new CreateAirport.AirportData(23.9644, 32.82, "ASW", "Aswan International", "civil", "Egypt"));
        case "HEMI" -> Optional.of(new CreateAirport.AirportData(31.0, 28.0, "", "Military Field", "military", "Egypt"));
        default -> Optional.empty();
    };

    @Test
    void anAirportIsCreatedFromTheDataWithTheCountryAndTheLink() {
        final List<String> dryRun = CreateAirport.create(world, "hesn", CreateAirport.Overrides.NONE, null, "Hurghada", true, LIBRARY);
        assertEquals("DRY RUN - nothing is changed", dryRun.get(0));
        assertTrue(world.airports().byIcao("HESN").isEmpty());

        final List<String> report = CreateAirport.create(world, "hesn", CreateAirport.Overrides.NONE, null, "hurghada", false, LIBRARY);

        assertEquals("Done", report.get(report.size() - 1));
        final Airports.Airport airport = world.airports().byIcao("HESN").orElseThrow();
        assertEquals("Aswan International", airport.getName());
        assertEquals("ASW", airport.getIata());
        assertEquals(world.countries().byCode("EG").orElseThrow().getId(), airport.getCountryId(), "the country of icaodata is found by its name");
        assertTrue(world.airport2city().byAirportIdAndCityId(airport.getId(), existing.getId()).isPresent());
    }

    @Test
    void withTheNameAndTheCoordinatesGivenTheLibraryIsNotAskedAtAll() {
        final Function<String, Optional<CreateAirport.AirportData>> neverAsked = icao -> {
            throw new AssertionError("the lookup must not be called");
        };
        final CreateAirport.Overrides overrides = new CreateAirport.Overrides("Carrasco", "MVD", -34.8378, -56.0303);

        CreateAirport.create(world, "SUMU", overrides, "EG", null, false, neverAsked);

        assertEquals("Carrasco", world.airports().byIcao("SUMU").orElseThrow().getName());
    }

    @Test
    void theValuesGivenByHandWinAndFillTheGaps() {
        final CreateAirport.Overrides overrides = new CreateAirport.Overrides("My Field", "mmm", 10.5, 20.5);

        CreateAirport.create(world, "ZZZZ", overrides, "eg", null, false, LIBRARY);

        final Airports.Airport airport = world.airports().byIcao("ZZZZ").orElseThrow();
        assertEquals("My Field", airport.getName());
        assertEquals("MMM", airport.getIata());
        assertEquals(10.5f, airport.getLatitude(), 0.001);
        assertEquals(world.countries().byCode("EG").orElseThrow().getId(), airport.getCountryId());
        assertTrue(world.airport2city().allByAirportId(airport.getId()).findAny().isEmpty());
    }

    @Test
    void theRealLookupKnowsTheAirportsOfTheLibraryAndOfIcaodata() {
        final CreateAirport.AirportData tffr = CreateAirport.lookup("TFFR").orElseThrow();
        assertEquals("civil", tffr.type());
        assertEquals("Guadeloupe", tffr.country());
        assertEquals(16.26, tffr.latitude(), 0.01);
        assertEquals(-61.53, tffr.longitude(), 0.01);
        assertTrue(CreateAirport.lookup("QQQQ").isEmpty());
    }

    @Test
    void anAirportWhichCanNotBeCreatedIsRefused() {
        // not civil, unknown without hand-given data, exists, a bad code, an unknown country, an unknown or an ambiguous city
        assertThrows(IllegalArgumentException.class, () -> CreateAirport.create(world, "HEMI", CreateAirport.Overrides.NONE, null, null, false, LIBRARY));
        assertThrows(IllegalArgumentException.class, () -> CreateAirport.create(world, "ZZZZ", CreateAirport.Overrides.NONE, null, null, false, LIBRARY));
        assertThrows(IllegalArgumentException.class, () -> CreateAirport.create(world, "HES", CreateAirport.Overrides.NONE, null, null, false, LIBRARY));
        assertThrows(IllegalArgumentException.class, () -> CreateAirport.create(world, "HESN", CreateAirport.Overrides.NONE, "XX", null, false, LIBRARY));
        assertThrows(IllegalArgumentException.class, () -> CreateAirport.create(world, "HESN", CreateAirport.Overrides.NONE, null, "Nowhere", false, LIBRARY));
        world.cities().create(existing.getCountryId(), "Twin", 1, 1, 1);
        world.cities().create(existing.getCountryId(), "twin", 2, 2, 1);
        assertThrows(IllegalArgumentException.class, () -> CreateAirport.create(world, "HESN", CreateAirport.Overrides.NONE, null, "Twin", false, LIBRARY));

        CreateAirport.create(world, "HESN", CreateAirport.Overrides.NONE, null, null, false, LIBRARY);
        assertThrows(IllegalArgumentException.class, () -> CreateAirport.create(world, "HESN", CreateAirport.Overrides.NONE, null, null, false, LIBRARY));
        assertFalse(world.airports().byIcao("HEMI").isPresent());
        assertEquals(1, world.airports().all().count());
    }
}
