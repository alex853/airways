package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.tools.CabinLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bit "the journey of a pilot" (a person travels by airline) is bit 6 of the status byte: it is off for a new journey and does not touch the status, the bit "returning back"
 * and the other fields.
 */
class JourneyPilotBitTest {
    private static Journeys.Journey journey(final Journeys journeys) {
        return journeys.create(Journeys.Status.LookingForTickets, 3, 4, 1, CabinLayout.Service.Y);
    }

    @Test
    void aNewJourneyIsNotTheJourneyOfAPilot() {
        assertFalse(journey(new Journeys()).isPilotJourney());
    }

    @Test
    void theBitIsKeptAndDoesNotTouchTheStatusAndTheOtherBits() {
        final Journeys journeys = new Journeys();
        final Journeys.Journey journey = journey(journeys);
        journey.setReturningBack(true);

        journey.setPilotJourney(true);

        assertTrue(journey.isPilotJourney());
        assertTrue(journey.isReturningBack());
        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertEquals(3, journey.getFromCityId());
        assertEquals(4, journey.getToCityId());
        assertEquals(1, journey.getGroupSize());

        journey.setPilotJourney(false);
        assertFalse(journey.isPilotJourney());
        assertTrue(journey.isReturningBack());
    }

    @Test
    void theStatusChangesDoNotClearTheBit() {
        final Journeys journeys = new Journeys();
        final Journeys.Journey journey = journey(journeys);
        journey.setPilotJourney(true);

        for (final Journeys.Status status : Journeys.Status.values()) {
            journey.setStatus(status);
            assertEquals(status, journey.getStatus(), status.toString());
            assertTrue(journey.isPilotJourney(), status.toString());
        }
    }

    @Test
    void theBitDoesNotChangeWhatTheScansByStatusFind() {
        final Journeys journeys = new Journeys();
        final Journeys.Journey plain = journey(journeys);
        final Journeys.Journey pilot = journey(journeys);
        pilot.setPilotJourney(true);

        assertEquals(2, journeys.all().filter(j -> j.getStatus() == Journeys.Status.LookingForTickets).count());
        assertFalse(plain.isPilotJourney());
    }
}
