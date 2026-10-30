package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.tools.CabinLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The price of a journey is in the reserve of its record: three bytes, zero for a journey without a price, the other fields stay as they are.
 */
class JourneyPriceTest {
    private static Journeys.Journey journey(final Journeys journeys) {
        return journeys.create(Journeys.Status.LookingForTickets, 3, 4, 5, CabinLayout.Service.J);
    }

    @Test
    void aNewJourneyHasNoPrice() {
        assertEquals(0, journey(new Journeys()).getPrice1());
    }

    @Test
    void thePriceIsKeptAndDoesNotTouchTheOtherFields() {
        final Journeys journeys = new Journeys();
        final Journeys.Journey journey = journey(journeys);
        journey.setLocationCityId(9);
        journey.setTransportFlight1Id(123_456);
        journey.setTransportFlight2Id(654_321);

        journey.setPrice1(247_000);

        assertEquals(247_000, journey.getPrice1());
        assertEquals(3, journey.getFromCityId());
        assertEquals(4, journey.getToCityId());
        assertEquals(5, journey.getGroupSize());
        assertEquals(9, journey.getLocationCityId());
        assertEquals(123_456, journey.getTransportFlight1Id());
        assertEquals(654_321, journey.getTransportFlight2Id());
    }

    @Test
    void thePriceFitsThreeBytesAndNotMore() {
        final Journeys.Journey journey = journey(new Journeys());

        journey.setPrice1(Journeys.MAX_PRICE);
        assertEquals(16_777_215, journey.getPrice1());

        assertThrows(IllegalArgumentException.class, () -> journey.setPrice1(Journeys.MAX_PRICE + 1));
        assertThrows(IllegalArgumentException.class, () -> journey.setPrice1(-1));
        assertEquals(16_777_215, journey.getPrice1());
    }

    @Test
    void thePriceSurvivesSaveAndLoad(@TempDir final Path dir) throws IOException {
        final Journeys journeys = new Journeys();
        final Journeys.Journey journey = journey(journeys);
        journey.setPrice1(12_345);

        journeys.save(dir);
        final Journeys loaded = new Journeys();
        loaded.loadIfExists(dir);

        assertEquals(12_345, loaded.byId(journey.getId()).orElseThrow().getPrice1());
    }

    @Test
    void price2IsTheSeparateFieldOfTheSecondFlight() {
        final Journeys.Journey journey = journey(new Journeys());
        assertEquals(0, journey.getPrice2());

        journey.setPrice1(1_000);
        journey.setPrice2(2_500);

        assertEquals(1_000, journey.getPrice1());
        assertEquals(2_500, journey.getPrice2());
        journey.setPrice1(0);
        assertEquals(2_500, journey.getPrice2());
        journey.setPrice2(Journeys.MAX_PRICE);
        assertEquals(16_777_215, journey.getPrice2());
        assertEquals(0, journey.getPrice1());
        assertThrows(IllegalArgumentException.class, () -> journey.setPrice2(Journeys.MAX_PRICE + 1));
        assertThrows(IllegalArgumentException.class, () -> journey.setPrice2(-1));
        assertEquals(16_777_215, journey.getPrice2());
    }

    @Test
    void price2SurvivesSaveAndLoadAndDoesNotTouchTheOtherFields(@TempDir final Path dir) throws IOException {
        final Journeys journeys = new Journeys();
        final Journeys.Journey journey = journey(journeys);
        journey.setTransportFlight1Id(111);
        journey.setTransportFlight2Id(222);
        journey.setPrice1(12_345);
        journey.setPrice2(54_321);

        journeys.save(dir);
        final Journeys loaded = new Journeys();
        loaded.loadIfExists(dir);

        final Journeys.Journey back = loaded.byId(journey.getId()).orElseThrow();
        assertEquals(12_345, back.getPrice1());
        assertEquals(54_321, back.getPrice2());
        assertEquals(111, back.getTransportFlight1Id());
        assertEquals(222, back.getTransportFlight2Id());
    }
}
