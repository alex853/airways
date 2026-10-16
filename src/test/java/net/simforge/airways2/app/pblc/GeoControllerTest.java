package net.simforge.airways2.app.pblc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.WorldAccess;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeoControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    private World world;
    private Countries.Country country;
    private Cities.City city;
    private Airports.Airport airport;
    private GeoController controller;
    private final AtomicInteger worldReads = new AtomicInteger();

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        country = world.countries().create("TT", "Testland");
        city = world.cities().create(country.getId(), "Alpha City", 10, 20, 1_000_000);
        airport = world.airports().create(10, 20, "AAA", "AAAA", "Alpha");
        world.airport2city().create(airport.getId(), city.getId());

        final WorldRunnerBean worldBean = mock(WorldRunnerBean.class);
        when(worldBean.getWorldForChangeStamps()).thenReturn(world);
        when(worldBean.read(any())).thenAnswer(invocation -> {
            worldReads.incrementAndGet();
            return ((WorldAccess.Action<?>) invocation.getArgument(0)).invoke(world);
        });

        controller = new GeoController();
        ReflectionTestUtils.setField(controller, "worldBean", worldBean);
        ReflectionTestUtils.setField(controller, "objectMapper", objectMapper);
    }

    private JsonNode json(final ResponseEntity<byte[]> response) throws IOException {
        return objectMapper.readTree(response.getBody());
    }

    @Test
    public void test__countries() throws IOException {
        final JsonNode countries = json(controller.getCountries(null));

        assertEquals(1, countries.size());
        assertEquals("TT", countries.get(0).get("code").asText());
        assertEquals("Testland", countries.get(0).get("name").asText());
    }

    @Test
    public void test__cities_with_their_airports() throws IOException {
        final JsonNode cities = json(controller.getCities(null));

        assertEquals(1, cities.size());
        assertEquals("Alpha City", cities.get(0).get("name").asText());
        assertEquals(1_000_000, cities.get(0).get("population").asInt());
        assertEquals(country.getId(), cities.get(0).get("countryId").asInt());
        assertEquals("AAAA", cities.get(0).get("airports").get(0).asText());
    }

    @Test
    public void test__airports_with_the_country_of_the_most_populated_city() throws IOException {
        final Countries.Country other = world.countries().create("OO", "Otherland");
        final Cities.City biggerCity = world.cities().create(other.getId(), "Big City", 11, 21, 5_000_000);
        world.airport2city().create(airport.getId(), biggerCity.getId());

        final JsonNode airports = json(controller.getAirports(null));

        assertEquals(1, airports.size());
        assertEquals("AAAA", airports.get(0).get("icao").asText());
        assertEquals("Otherland", airports.get(0).get("country").asText());
    }

    @Test
    public void test__airport_without_a_city_has_no_country() throws IOException {
        world.airports().create(1, 2, "BBB", "BBBB", "Bravo");

        final JsonNode airports = json(controller.getAirports(null));

        assertEquals("Testland", airports.get(0).get("country").asText());
        assertTrue(airports.get(1).get("country").isNull());
    }

    @Test
    public void test__unchanged_world_returns_the_same_response_without_touching_the_world() {
        final byte[] first = controller.getCities(null).getBody();
        final int readsAfterTheFirstCall = worldReads.get();

        final byte[] second = controller.getCities(null).getBody();
        final byte[] third = controller.getCities("gzip").getBody();
        final byte[] fourth = controller.getCities("gzip").getBody();

        assertSame(first, second);
        assertSame(third, fourth);
        assertEquals(readsAfterTheFirstCall, worldReads.get(), "the world lock must not be taken for an up to date response");
    }

    @Test
    public void test__changed_city_rebuilds_the_cities() throws IOException {
        final byte[] before = controller.getCities(null).getBody();

        city.setPopulation(7_000_000);

        final byte[] after = controller.getCities(null).getBody();
        assertNotSame(before, after);
        assertEquals(7_000_000, objectMapper.readTree(after).get(0).get("population").asInt());
    }

    @Test
    public void test__changed_city_does_not_rebuild_the_countries() {
        final byte[] before = controller.getCountries(null).getBody();

        city.setPopulation(7_000_000);

        assertSame(before, controller.getCountries(null).getBody());
    }

    @Test
    public void test__renamed_country_rebuilds_the_countries_and_the_airports() throws IOException {
        final byte[] countriesBefore = controller.getCountries(null).getBody();
        final byte[] airportsBefore = controller.getAirports(null).getBody();

        country.setName("Renamed");

        assertEquals("Renamed", objectMapper.readTree(controller.getCountries(null).getBody()).get(0).get("name").asText());
        assertEquals("Renamed", objectMapper.readTree(controller.getAirports(null).getBody()).get(0).get("country").asText());
        assertNotSame(countriesBefore, controller.getCountries(null).getBody());
        assertNotSame(airportsBefore, controller.getAirports(null).getBody());
    }

    @Test
    public void test__new_link_rebuilds_the_cities_and_the_airports() throws IOException {
        final byte[] citiesBefore = controller.getCities(null).getBody();
        final byte[] airportsBefore = controller.getAirports(null).getBody();
        final Airports.Airport second = world.airports().create(1, 2, "BBB", "BBBB", "Bravo");

        world.airport2city().create(second.getId(), city.getId());

        final JsonNode cities = objectMapper.readTree(controller.getCities(null).getBody());
        assertEquals(2, cities.get(0).get("airports").size());
        assertNotSame(citiesBefore, controller.getCities(null).getBody());
        assertEquals("Testland", objectMapper.readTree(controller.getAirports(null).getBody()).get(1).get("country").asText());
        assertNotSame(airportsBefore, controller.getAirports(null).getBody());
    }

    @Test
    public void test__renamed_airport_rebuilds_the_airports_and_the_cities() throws IOException {
        controller.getCities(null);
        controller.getAirports(null);

        airport.setName("Renamed airport");

        assertEquals("Renamed airport", objectMapper.readTree(controller.getAirports(null).getBody()).get(0).get("name").asText());
    }

    @Test
    public void test__gzip_response_is_the_same_json() throws IOException {
        final ResponseEntity<byte[]> plain = controller.getAirports(null);
        final ResponseEntity<byte[]> gzip = controller.getAirports("gzip, deflate");

        assertEquals("gzip", gzip.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING));
        try (final GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzip.getBody()))) {
            assertTrue(java.util.Arrays.equals(plain.getBody(), in.readAllBytes()));
        }
    }
}
