package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.PilotController.LogbookDto;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The logbook of a pilot as the user sees it: the flights of the person only, the latest first, with the totals.
 */
class PilotLogbookEndpointTest {
    private World world;
    private Airports.Airport oakland;
    private Airports.Airport sanFrancisco;
    private Aircrafts.Aircraft aircraft;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        oakland = world.airports().create(37.72, -122.22, "OAK", "KOAK", "Oakland");
        sanFrancisco = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", oakland);
    }

    private PilotLogbook.Entry flight(final int personId, final Airports.Airport from, final Airports.Airport to, final int offHoursAfterStart, final int minutes) {
        final int off = Time.START_TIME_EPOCH_SECONDS + offHoursAfterStart * Time.ONE_HOUR;
        return world.pilotLogbook().create(personId, aircraft.getId(), from.getId(), to.getId(), off, off + minutes * 60);
    }

    @Test
    void noFlightsNoEntries() {
        final LogbookDto logbook = PilotController.buildLogbook(world, 1);

        assertEquals(0, logbook.getEntries().size());
        assertEquals(0, logbook.getTotals().getFlights());
        assertEquals(0.0f, logbook.getTotals().getFlightTime());
    }

    @Test
    void theLatestFlightIsFirstAndOnlyTheFlightsOfThePersonAreShown() {
        flight(1, oakland, sanFrancisco, 1, 30);
        flight(1, sanFrancisco, oakland, 5, 36);
        flight(2, oakland, sanFrancisco, 3, 60);

        final LogbookDto logbook = PilotController.buildLogbook(world, 1);

        assertEquals(2, logbook.getEntries().size());
        assertEquals("KSFO", logbook.getEntries().get(0).getFrom());
        assertEquals("KOAK", logbook.getEntries().get(0).getTo());
        assertEquals("BB-CJA", logbook.getEntries().get(0).getRegNo());
        assertEquals("C25C", logbook.getEntries().get(0).getAircraftType());
        assertEquals("KOAK", logbook.getEntries().get(1).getFrom());
    }

    @Test
    void totalsSumUpTheFlightsOfThePerson() {
        flight(1, oakland, sanFrancisco, 1, 30);   // 0.5 h
        flight(1, sanFrancisco, oakland, 5, 36);   // 0.6 h
        flight(2, oakland, sanFrancisco, 3, 60);

        final PilotController.LogbookTotalsDto totals = PilotController.buildLogbook(world, 1).getTotals();

        assertEquals(2, totals.getFlights());
        assertEquals(1.1f, totals.getFlightTime());
        assertEquals(0.0f, totals.getNightTime());
        assertEquals(2, totals.getDayTakeoffs());
        assertEquals(2, totals.getDayLandings());
    }
}
