package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Journeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The list of the missions of BusyBirds shows a rough pay while the journey has no price, the price is set when a pilot gets a quote and the list shows
 * the exact one after that.
 */
class BusyBirdsQuoteTest {
    private World world;
    private BusyBirdsMissionControl control;
    private Journeys.Journey journey;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.busyBirdsMissionControl();

        world.aircraftOperators().create("AA", "AAA", "first");
        world.aircraftOperators().create("SJ", "SJT", "second");
        world.aircraftOperators().create("BB", "BBD", "busyBirds");

        final int countryId = world.countries().create("GB", "United Kingdom").getId();
        final int fromCityId = world.cities().create(countryId, "From", 51.28, -0.76, 100000).getId();
        final int toCityId = world.cities().create(countryId, "To", 52.5, 13.4, 100000).getId();
        journey = world.journeys().create(Journeys.Status.LookingForTickets, fromCityId, toCityId, 4, CabinLayout.Service.J);
        journey.setLocationCityId(fromCityId);
    }

    private BusyBirdsMissionControl.Mission theMission() {
        final List<BusyBirdsMissionControl.Mission> missions = control.getMissionsToBook();
        assertEquals(1, missions.size());
        return missions.get(0);
    }

    @Test
    void roughPayIsLowerAndRoundedDownToThousandsOrHundreds() {
        assertEquals(123_000, BusyBirdsMissionControl.roughPay(123_475));
        assertEquals(8_000, BusyBirdsMissionControl.roughPay(8_497));
        assertEquals(1_000, BusyBirdsMissionControl.roughPay(1_000));
        assertEquals(900, BusyBirdsMissionControl.roughPay(999));
        assertEquals(0, BusyBirdsMissionControl.roughPay(99));
    }

    @Test
    void aJourneyWithoutAPriceIsListedWithARoughPay() {
        final BusyBirdsMissionControl.Mission mission = theMission();

        assertFalse(mission.isPayExact());
        assertEquals(0, mission.getPay() % 1000);
        assertTrue(mission.getPay() > 0);
    }

    @Test
    void theQuoteSetsThePriceOnceAndTheListShowsItExactly() {
        final int price = control.quote(journey);

        assertEquals(price, journey.getPrice());
        assertTrue(price > 0);
        assertTrue(theMission().isPayExact());
        assertEquals(price, theMission().getPay());

        // a second quote gives the same price
        assertEquals(price, control.quote(journey));
        assertEquals(price, journey.getPrice());
    }

    @Test
    void thePriceIsTheIncomeByTheFormulaTimesARandomFactorFrom95To105Percent() {
        // factor 1: the income by the formula
        final int income = control.quote(journey, 1.0);
        assertTrue(income > 0);

        journey.setPrice(0);
        assertEquals(Math.round(income * 0.95), control.quote(journey, 0.95));

        journey.setPrice(0);
        assertEquals(Math.round(income * 1.05), control.quote(journey, 1.05));

        // the random factor of the quote itself stays inside the limits
        for (int i = 0; i < 200; i++) {
            journey.setPrice(0);
            final int price = control.quote(journey);
            assertTrue(price >= Math.round(income * 0.95) && price <= Math.round(income * 1.05), "price " + price);
        }
    }

    @Test
    void aRepeatedQuoteDoesNotChangeThePriceEvenWithAnotherFactor() {
        final int first = control.quote(journey, 0.95);

        assertEquals(first, control.quote(journey, 1.05));
        assertEquals(first, journey.getPrice());
    }

    @Test
    void theListShowsARoughPayBelowTheIncomeWhichBecomesExactAfterTheQuote() {
        final int rough = theMission().getPay();
        final int income = control.quote(journey, 1.0);

        assertTrue(rough <= income && income - rough < 1000);
        assertEquals(income, theMission().getPay());
    }

    @Test
    void thePlansAreBuiltWithThePriceOfTheJourney() {
        journey.setPrice(50_000);

        // the income of the mission is the price, not the income by the formula
        assertEquals(50_000, control.quote(journey));
        assertEquals(50_000, theMission().getPay());
    }
}
