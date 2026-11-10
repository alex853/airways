package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.ShadowJetPricing;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.commons.misc.Geo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The estimate of a travel by airline: the route is found over the frequencies of the flights between the airports by the steps of the search of a journey (a flight to a hub which
 * brings the pilot closer, a direct flight, two flights with a stopover), the price is the ShadowJet economy ticket of every flight, the time is one to two days a flight.
 * Nothing is changed in the world.
 * <p>
 * The cities and the airports of the test (about 1 degree is 60 nautical miles): the cities A (0, 0), H (3, 0, the hub in the middle), B (6, 0), X (3, 2, not a hub, off the line),
 * C (0.5, 0, too close to A) and D (-3, 0, a hub on the other side of A), every city has one airport with the code of its letter.
 */
class AirlineTravelEstimateTest {
    private World world;
    private Cities.City cityA;
    private Cities.City cityH;
    private Cities.City cityB;
    private Cities.City cityX;
    private Cities.City cityC;
    private Cities.City cityD;
    private Airports.Airport airportA;
    private Airports.Airport airportH;
    private Airports.Airport airportB;
    private Airports.Airport airportX;
    private Airports.Airport airportD;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final int countryId = world.countries().create("US", "United States").getId();
        cityA = world.cities().create(countryId, "Aville", 0, 0, 500_000);
        cityH = world.cities().create(countryId, "Hville", 3, 0, 900_000);
        cityB = world.cities().create(countryId, "Bville", 6, 0, 700_000);
        cityX = world.cities().create(countryId, "Xville", 3, 2, 300_000);
        cityC = world.cities().create(countryId, "Cville", 0.5, 0, 100_000);
        cityD = world.cities().create(countryId, "Dville", -3, 0, 800_000);
        airportA = airport(cityA, "AAA", "KAAA");
        airportH = airport(cityH, "HHH", "KHHH");
        airportB = airport(cityB, "BBB", "KBBB");
        airportX = airport(cityX, "XXX", "KXXX");
        airport(cityC, "CCC", "KCCC");
        airportD = airport(cityD, "DDD", "KDDD");
        airportH.setHub(true);
        airportD.setHub(true);
    }

    private Airports.Airport airport(final Cities.City city, final String iata, final String icao) {
        final Airports.Airport airport = world.airports().create(city.getCoords().getLat(), city.getCoords().getLon(), iata, icao, city.getName());
        world.airport2city().create(airport.getId(), city.getId());
        return airport;
    }

    // n flights in the days of the stats from an airport to another
    private void flights(final Airports.Airport from, final Airports.Airport to, final int n) {
        for (int i = 0; i < n; i++) {
            world.airport2airportDailyFlightStats().incrementTodayCount(from.getId(), to.getId());
        }
    }

    private AirlineTravelEstimate.Result estimate(final Cities.City from, final Cities.City to) {
        return new AirlineTravelEstimate(world).estimate(from.getId(), to.getId());
    }

    private int ticket(final Airports.Airport from, final Airports.Airport to) {
        return ShadowJetPricing.price(Geo.distance(from.getCoords(), to.getCoords()), 1, CabinLayout.Service.Y);
    }

    @Test
    void aDirectFlightIsOneLegWithTheTicketOfTheDistanceAndOneToTwoDays() {
        flights(airportA, airportB, 14);

        final AirlineTravelEstimate.Result result = estimate(cityA, cityB);

        assertTrue(result.found(), result.problem());
        assertEquals(1, result.legs().size());
        final AirlineTravelEstimate.Leg leg = result.legs().get(0);
        assertEquals("KAAA", leg.fromIcao());
        assertEquals("KBBB", leg.toIcao());
        assertEquals(14, leg.flightsPerWeek());
        assertEquals(ticket(airportA, airportB), leg.price());
        assertEquals(leg.price(), result.price());
        assertEquals(1, result.minDays());
        assertEquals(2, result.maxDays());
    }

    @Test
    void theMostFrequentOfTheDirectFlightsIsTaken() {
        final Airports.Airport secondB = airport(cityB, "BB2", "KBB2");
        flights(airportA, airportB, 3);
        flights(airportA, secondB, 9);

        final AirlineTravelEstimate.Result result = estimate(cityA, cityB);

        assertEquals("KBB2", result.legs().get(0).toIcao());
    }

    @Test
    void aHubWhichIsCloserIsTheFirstLegAndTheSearchGoesOnFromItsCity() {
        flights(airportA, airportH, 20);
        flights(airportH, airportB, 20);

        final AirlineTravelEstimate.Result result = estimate(cityA, cityB);

        assertTrue(result.found(), result.problem());
        assertEquals(List.of("KAAA", "KHHH"), List.of(result.legs().get(0).fromIcao(), result.legs().get(0).toIcao()));
        assertEquals(List.of("KHHH", "KBBB"), List.of(result.legs().get(1).fromIcao(), result.legs().get(1).toIcao()));
        assertEquals(ticket(airportA, airportH) + ticket(airportH, airportB), result.price());
        // a flight is one to two days: two flights are two to four
        assertEquals(2, result.minDays());
        assertEquals(4, result.maxDays());
    }

    @Test
    void aHubWhichIsFartherFromTheDestinationIsNotTaken() {
        // Dville is a hub, but it is on the other side of Aville: the flight to it does not bring the pilot closer, so the direct flight is taken
        flights(airportA, airportD, 30);
        flights(airportA, airportB, 5);

        final AirlineTravelEstimate.Result result = estimate(cityA, cityB);

        assertTrue(result.found(), result.problem());
        assertEquals(1, result.legs().size());
        assertEquals("KBBB", result.legs().get(0).toIcao());
    }

    @Test
    void aFlightToAnAirportWhichIsNotAHubIsNotAHubLegButItCanBeTheFirstOfTwoFlights() {
        // Aville is not a hub: from Hville the route is a stopover in Aville (two flights), not a hub leg
        flights(airportH, airportA, 20);
        flights(airportA, airportB, 5);

        final AirlineTravelEstimate.Result result = estimate(cityH, cityB);

        assertTrue(result.found(), result.problem());
        assertEquals(2, result.legs().size());
        assertEquals(List.of("KHHH", "KAAA", "KBBB"), List.of(result.legs().get(0).fromIcao(), result.legs().get(0).toIcao(), result.legs().get(1).toIcao()));
    }

    @Test
    void twoFlightsWithAStopoverWhichIsNotAHub() {
        flights(airportA, airportX, 4);
        flights(airportX, airportB, 7);

        final AirlineTravelEstimate.Result result = estimate(cityA, cityB);

        assertTrue(result.found(), result.problem());
        assertEquals(2, result.legs().size());
        assertEquals("KXXX", result.legs().get(0).toIcao());
        assertEquals("KBBB", result.legs().get(1).toIcao());
        assertEquals(2, result.minDays());
        assertEquals(4, result.maxDays());
    }

    @Test
    void noFlightsMeansNoRouteAndTheProblemSaysFromWhere() {
        final AirlineTravelEstimate.Result result = estimate(cityA, cityB);

        assertFalse(result.found());
        assertTrue(result.legs().isEmpty());
        assertEquals(0, result.price());
        assertEquals("No flights from Aville towards Bville yet", result.problem());
    }

    @Test
    void aPlaceCloserThanOneHundredMilesIsRefused() {
        flights(airportA, airportB, 5);

        final AirlineTravelEstimate.Result result = estimate(cityA, cityC);

        assertFalse(result.found());
        assertEquals("Too close, from 100 miles", result.problem());
    }

    @Test
    void theSameCityAndAMissingCityAreRefused() {
        assertEquals("The pilot is in Aville already", estimate(cityA, cityA).problem());
        assertEquals("There is no such city", new AirlineTravelEstimate(world).estimate(cityA.getId(), 9999).problem());
    }

    @Test
    void anAirportWhichIsOutOfTheGameIsNotUsed() {
        flights(airportA, airportB, 14);
        airportB.setExcluded(true);

        final AirlineTravelEstimate.Result result = estimate(cityA, cityB);

        assertFalse(result.found());
        assertEquals("There are no airports in Bville", result.problem());
    }

    // ---- the variants: the route of the search and the other ways which the flights allow, the most frequent first (the rarest flight of the route is its frequency)

    private List<String> titles(final List<AirlineTravelEstimate.Result> variants) {
        return variants.stream().map(AirlineTravelEstimate.Result::title).toList();
    }

    @Test
    void theVariantsAreSortedByTheFrequencyAndNotByTheStepsOfTheSearch() {
        flights(airportA, airportH, 20);
        flights(airportH, airportB, 20);
        // the direct flight is rare, the stopover in a city which is not a hub is in between
        flights(airportA, airportB, 2);
        flights(airportA, airportX, 4);
        flights(airportX, airportB, 7);

        final List<AirlineTravelEstimate.Result> variants = new AirlineTravelEstimate(world).variants(cityA.getId(), cityB.getId());

        // the frequency of a route is its rarest flight: 20, then 4 (the rarer of 4 and 7), then 2
        assertEquals(List.of("Through KHHH", "Stopover in KXXX", "Direct flight"), titles(variants));
        assertEquals(List.of("KAAA", "KHHH", "KBBB"), List.of(variants.get(0).legs().get(0).fromIcao(), variants.get(0).legs().get(0).toIcao(), variants.get(0).legs().get(1).toIcao()));
        assertEquals(2, variants.get(2).legs().get(0).flightsPerWeek());
        assertEquals(1, variants.get(2).minDays());
        assertEquals(2, variants.get(0).minDays());
        assertEquals(ticket(airportA, airportB), variants.get(2).price());
    }

    @Test
    void aFrequentDirectFlightIsBeforeTheRouteThroughTheHub() {
        flights(airportA, airportH, 6);
        flights(airportH, airportB, 6);
        flights(airportA, airportB, 30);

        final List<AirlineTravelEstimate.Result> variants = new AirlineTravelEstimate(world).variants(cityA.getId(), cityB.getId());

        // the search itself goes to the hub first, but the direct flight is more frequent, so it is the first of the list
        assertEquals(List.of("Direct flight", "Through KHHH"), titles(variants));
    }

    @Test
    void ofTheSameFrequencyTheShorterRouteIsFirstThenTheCheaperOne() {
        flights(airportA, airportH, 10);
        flights(airportH, airportB, 10);
        flights(airportA, airportB, 10);

        final List<AirlineTravelEstimate.Result> variants = new AirlineTravelEstimate(world).variants(cityA.getId(), cityB.getId());

        assertEquals(List.of("Direct flight", "Through KHHH"), titles(variants));
    }

    @Test
    void aRouteWhichIsInTheListAlreadyIsNotShownTwice() {
        flights(airportA, airportB, 14);

        final List<AirlineTravelEstimate.Result> variants = new AirlineTravelEstimate(world).variants(cityA.getId(), cityB.getId());

        assertEquals(List.of("Direct flight"), titles(variants));
    }

    @Test
    void whenThereIsNoRouteTheVariantsAreOneResultWithTheProblem() {
        final List<AirlineTravelEstimate.Result> variants = new AirlineTravelEstimate(world).variants(cityA.getId(), cityB.getId());

        assertEquals(1, variants.size());
        assertFalse(variants.get(0).found());
        assertEquals("No flights from Aville towards Bville yet", variants.get(0).problem());
    }

    @Test
    void theEstimateChangesNothingInTheWorld() {
        flights(airportA, airportH, 20);
        flights(airportH, airportB, 20);
        final long journeys = world.journeys().all().count();
        final long persons = world.persons().all().count();
        final long flightMissions = world.flightMissions().all().count();

        estimate(cityA, cityB);

        assertEquals(journeys, world.journeys().all().count());
        assertEquals(persons, world.persons().all().count());
        assertEquals(flightMissions, world.flightMissions().all().count());
    }
}
