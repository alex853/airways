package net.simforge.airways2.world.datamodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A link of an airport with a city which should not be (through the sea or the border) can be removed, the other links stay.
 */
class Airport2CityTest {
    @Test
    void deleteRemovesOnlyTheLinkItIsGiven() {
        final Airport2City links = new Airport2City();
        links.create(1, 10);
        links.create(2, 10);
        links.create(1, 11);

        assertTrue(links.delete(2, 10));

        assertTrue(links.byAirportIdAndCityId(2, 10).isEmpty());
        assertTrue(links.byAirportIdAndCityId(1, 10).isPresent());
        assertTrue(links.byAirportIdAndCityId(1, 11).isPresent());
        assertEquals(2, links.all().count());
    }

    @Test
    void deleteOfAMissingLinkChangesNothing() {
        final Airport2City links = new Airport2City();
        links.create(1, 10);

        assertFalse(links.delete(2, 10));

        assertEquals(1, links.all().count());
    }

    @Test
    void aDeletedLinkCanBeCreatedAgain() {
        final Airport2City links = new Airport2City();
        links.create(1, 10);
        links.delete(1, 10);

        links.create(1, 10);

        assertTrue(links.byAirportIdAndCityId(1, 10).isPresent());
        assertEquals(1, links.allByAirportId(1).count());
    }
}
