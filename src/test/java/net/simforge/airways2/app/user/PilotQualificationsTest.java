package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.PilotController.QualificationsDto;
import net.simforge.airways2.app.user.PilotController.TypeCurrencyDto;
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
 * The currency of a pilot on the types of aircraft: the numbers by the logbook, the recent ones by the time of the landing (blocks on).
 */
class PilotQualificationsTest {
    private static final int DAY = 24 * Time.ONE_HOUR;

    private World world;
    private int now;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Aircrafts.Aircraft gulfstream;
    private Aircrafts.Aircraft citation;

    @BeforeEach
    void beforeEach() {
        now = Time.START_TIME_EPOCH_SECONDS + 500 * DAY;
        world = World.create(new InMemoryStorageStrategy(), now);
        airportA = world.airports().create(37.72, -122.22, "OAK", "KOAK", "Oakland");
        airportB = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
        gulfstream = world.aircrafts().create(world.aircraftTypes().create("GA7C", "GL7"), "BB-GLA", airportA);
        citation = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", airportA);
    }

    // a flight which ended (blocks on) this long ago and was this long
    private PilotLogbook.Entry flight(final int personId, final Aircrafts.Aircraft aircraft, final int endedSecondsAgo, final int minutes) {
        final int on = now - endedSecondsAgo;
        return world.pilotLogbook().create(personId, aircraft.getId(), airportA.getId(), airportB.getId(), on - minutes * 60, on);
    }

    private static TypeCurrencyDto type(final QualificationsDto q, final String icao) {
        return q.getTypes().stream().filter(t -> t.getAircraftType().equals(icao)).findFirst().orElseThrow();
    }

    @Test
    void noFlightsNoTypes() {
        final QualificationsDto q = PilotController.buildQualifications(world, 1, now);

        assertEquals(0, q.getTypes().size());
        assertEquals(0, q.getTotals().getFlights());
        assertEquals(24, q.getRecentLandingsWindowHours());
        assertEquals(365, q.getRecentFlightsWindowDays());
    }

    @Test
    void theCurrencyIsCountedForEveryTypeOfTheLogbook() {
        flight(1, gulfstream, 2 * Time.ONE_HOUR, 60);                    // inside the 24 hours, day
        final PilotLogbook.Entry night = flight(1, gulfstream, 30 * Time.ONE_HOUR, 90);   // outside the 24 hours, inside the year
        night.setNightTakeoff(true);
        night.setNightLanding(true);
        flight(1, gulfstream, 400 * DAY, 60);                           // outside the year
        flight(1, citation, 10 * DAY, 120);
        flight(2, gulfstream, 1 * Time.ONE_HOUR, 60);                   // another pilot

        final QualificationsDto q = PilotController.buildQualifications(world, 1, now);

        final TypeCurrencyDto ga7c = type(q, "GA7C");
        assertEquals(3, ga7c.getFlights());
        assertEquals(2, ga7c.getRecentFlights());
        assertEquals(3, ga7c.getLandings());
        assertEquals(1, ga7c.getRecentLandings());
        assertEquals(1, ga7c.getNightTakeoffs());
        assertEquals(1, ga7c.getNightLandings());
        assertEquals(3.5f, ga7c.getFlightTime(), 0.001f);

        final TypeCurrencyDto c25c = type(q, "C25C");
        assertEquals(1, c25c.getFlights());
        assertEquals(1, c25c.getRecentFlights());
        assertEquals(1, c25c.getLandings());
        assertEquals(0, c25c.getRecentLandings());
        assertEquals(0, c25c.getNightTakeoffs());
        assertEquals(2.0f, c25c.getFlightTime(), 0.001f);

        // the totals are the totals of the whole logbook of the pilot
        assertEquals(4, q.getTotals().getFlights());
        assertEquals(5.5f, q.getTotals().getFlightTime(), 0.001f);
    }

    @Test
    void theTypeWithMoreHoursIsFirst() {
        flight(1, citation, 5 * DAY, 30);
        flight(1, gulfstream, 5 * DAY, 120);

        final QualificationsDto q = PilotController.buildQualifications(world, 1, now);

        assertEquals("GA7C", q.getTypes().get(0).getAircraftType());
        assertEquals("C25C", q.getTypes().get(1).getAircraftType());
    }

    @Test
    void aLandingExactlyOnTheBorderOfTheWindowIsRecent() {
        flight(1, gulfstream, 24 * Time.ONE_HOUR, 60);
        flight(1, gulfstream, 24 * Time.ONE_HOUR + 60, 60);

        final TypeCurrencyDto ga7c = type(PilotController.buildQualifications(world, 1, now), "GA7C");

        assertEquals(1, ga7c.getRecentLandings());
        assertEquals(2, ga7c.getLandings());
    }
}
