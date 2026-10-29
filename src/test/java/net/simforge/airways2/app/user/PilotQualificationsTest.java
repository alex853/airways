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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(90 * 24, q.getRecentLandingsWindowHours());
        assertEquals(365, q.getRecentFlightsWindowDays());
    }

    @Test
    void theCurrencyIsCountedForEveryTypeOfTheLogbook() {
        flight(1, gulfstream, 2 * Time.ONE_HOUR, 60);                    // inside the 90 days, day
        final PilotLogbook.Entry night = flight(1, gulfstream, 100 * DAY, 90);   // outside the 90 days, inside the year
        night.setNightTakeoff(true);
        night.setNightLanding(true);
        flight(1, gulfstream, 400 * DAY, 60);                           // outside the year
        flight(1, citation, 120 * DAY, 120);                           // outside the 90 days, inside the year
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
    void typesWithTheSameLastFlightGoByTheTypeCode() {
        // the default order is by the last flight, the types which landed at the same time go alphabetically
        flight(1, citation, 5 * DAY, 30);
        flight(1, gulfstream, 5 * DAY, 120);

        final QualificationsDto q = PilotController.buildQualifications(world, 1, now);

        assertEquals("C25C", q.getTypes().get(0).getAircraftType());
        assertEquals("GA7C", q.getTypes().get(1).getAircraftType());
    }

    @Test
    void aLandingExactlyOnTheBorderOfTheWindowIsRecent() {
        flight(1, gulfstream, 90 * DAY, 60);
        flight(1, gulfstream, 90 * DAY + 60, 60);

        final TypeCurrencyDto ga7c = type(PilotController.buildQualifications(world, 1, now), "GA7C");

        assertEquals(1, ga7c.getRecentLandings());
        assertEquals(2, ga7c.getLandings());
    }

    // ---- the criteria of the currency on a type

    @Test
    void threeLandingsInTheWindowMeanCanCarryPassengers() {
        flight(1, gulfstream, 1 * Time.ONE_HOUR, 60);
        flight(1, gulfstream, 5 * Time.ONE_HOUR, 60);
        flight(1, gulfstream, 20 * Time.ONE_HOUR, 60);
        flight(1, citation, 1 * Time.ONE_HOUR, 60);
        flight(1, citation, 5 * Time.ONE_HOUR, 60);
        flight(1, citation, 100 * DAY, 60); // outside the window

        final QualificationsDto q = PilotController.buildQualifications(world, 1, now);

        assertEquals(3, q.getCriteria().getRecentLandings());
        assertTrue(type(q, "GA7C").isCanCarryPassengers());
        assertFalse(type(q, "C25C").isCanCarryPassengers());
    }

    @Test
    void aNightTakeoffAndANightLandingInTheWindowMeanNightFlying() {
        final PilotLogbook.Entry first = flight(1, gulfstream, 2 * Time.ONE_HOUR, 60);
        first.setNightTakeoff(true);
        // a night takeoff only
        assertFalse(type(PilotController.buildQualifications(world, 1, now), "GA7C").isNightFlying());

        // the night landing is on another flight, both are in the window
        final PilotLogbook.Entry second = flight(1, gulfstream, 4 * Time.ONE_HOUR, 60);
        second.setNightLanding(true);
        final TypeCurrencyDto ga7c = type(PilotController.buildQualifications(world, 1, now), "GA7C");
        assertTrue(ga7c.isNightFlying());
        assertEquals(1, ga7c.getRecentNightTakeoffs());
        assertEquals(1, ga7c.getRecentNightLandings());
    }

    @Test
    void theNightOutsideTheWindowIsNotNightFlying() {
        final PilotLogbook.Entry old = flight(1, gulfstream, 100 * DAY, 60);
        old.setNightTakeoff(true);
        old.setNightLanding(true);

        final TypeCurrencyDto ga7c = type(PilotController.buildQualifications(world, 1, now), "GA7C");

        assertFalse(ga7c.isNightFlying());
        assertEquals(0, ga7c.getRecentNightTakeoffs());
        // the totals still have it
        assertEquals(1, ga7c.getNightTakeoffs());
        assertEquals(1, ga7c.getNightLandings());
    }

    @Test
    void twelveFlightsInTheLastTwelveMonthsMeanTypeCurrent() {
        for (int i = 1; i <= 11; i++) {
            flight(1, gulfstream, i * 10 * DAY, 60);
        }
        assertFalse(type(PilotController.buildQualifications(world, 1, now), "GA7C").isTypeCurrent());

        flight(1, gulfstream, 100 * DAY + 1, 60);
        final QualificationsDto q = PilotController.buildQualifications(world, 1, now);

        assertEquals(12, q.getCriteria().getRecentFlights());
        assertTrue(type(q, "GA7C").isTypeCurrent());
    }

    @Test
    void twelveFlightsAreNotEnoughWithoutTwelveHours() {
        for (int i = 1; i <= 12; i++) {
            flight(1, gulfstream, i * 10 * DAY, 30);   // 12 flights, 6 hours
        }

        final TypeCurrencyDto ga7c = type(PilotController.buildQualifications(world, 1, now), "GA7C");

        assertEquals(12, ga7c.getRecentFlights());
        assertEquals(6.0f, ga7c.getRecentFlightTime(), 0.001f);
        assertFalse(ga7c.isTypeCurrent());
    }

    @Test
    void twelveHoursAreNotEnoughWithoutTwelveFlights() {
        for (int i = 1; i <= 11; i++) {
            flight(1, gulfstream, i * 10 * DAY, 120);   // 11 flights, 22 hours
        }

        final TypeCurrencyDto ga7c = type(PilotController.buildQualifications(world, 1, now), "GA7C");

        assertEquals(22.0f, ga7c.getRecentFlightTime(), 0.001f);
        assertFalse(ga7c.isTypeCurrent());
    }

    @Test
    void twelveFlightsAndTwelveHoursMeanTypeCurrentAndTheHoursOfAnOldFlightAreNotCounted() {
        for (int i = 1; i <= 12; i++) {
            flight(1, gulfstream, i * 10 * DAY, 60);   // 12 flights, 12 hours
        }
        flight(1, gulfstream, 400 * DAY, 300);          // older than a year: only the total has it

        final QualificationsDto q = PilotController.buildQualifications(world, 1, now);
        final TypeCurrencyDto ga7c = type(q, "GA7C");

        assertEquals(12, q.getCriteria().getRecentFlightHours());
        assertEquals(12.0f, ga7c.getRecentFlightTime(), 0.001f);
        assertEquals(17.0f, ga7c.getFlightTime(), 0.001f);
        assertTrue(ga7c.isTypeCurrent());
    }

    // ---- the experience level on a type

    @Test
    void theExperienceLevelNeedsBothTheFlightsAndTheHours() {
        assertEquals(0, PilotController.experienceLevel(0, 0));
        assertEquals(0, PilotController.experienceLevel(24, 500));    // not enough flights
        assertEquals(0, PilotController.experienceLevel(500, 49.9f)); // not enough hours
        assertEquals(1, PilotController.experienceLevel(25, 50));
        assertEquals(1, PilotController.experienceLevel(49, 99.9f));
        assertEquals(2, PilotController.experienceLevel(50, 100));
        assertEquals(1, PilotController.experienceLevel(60, 90));     // the hours of the level 2 are not reached
        assertEquals(3, PilotController.experienceLevel(100, 200));
        assertEquals(3, PilotController.experienceLevel(249, 499));
        assertEquals(4, PilotController.experienceLevel(250, 500));
        assertEquals(4, PilotController.experienceLevel(499, 999));
        assertEquals(5, PilotController.experienceLevel(500, 1000));
        assertEquals(5, PilotController.experienceLevel(5000, 10000));
        // flights of a high level and hours of a low one: the level of the hours
        assertEquals(2, PilotController.experienceLevel(600, 100));
    }

    @Test
    void theExperienceLevelIsCountedOnTheWholeLogbookOfTheType() {
        for (int i = 1; i <= 25; i++) {
            flight(1, gulfstream, i * 18 * DAY, 120);      // 25 flights, 50 hours, some of them older than a year
        }
        flight(1, citation, 1 * DAY, 60);

        final QualificationsDto q = PilotController.buildQualifications(world, 1, now);

        assertEquals(1, type(q, "GA7C").getExperienceLevel());
        assertEquals(0, type(q, "C25C").getExperienceLevel());
        assertEquals(5, q.getExperienceLevels().size());
        assertEquals(25, q.getExperienceLevels().get(0).getFlights());
        assertEquals(1000, q.getExperienceLevels().get(4).getHours());
    }

    @Test
    void aFlightOlderThanAYearDoesNotCountForTheTypeCurrency() {
        for (int i = 1; i <= 11; i++) {
            flight(1, gulfstream, i * 10 * DAY, 60);
        }
        flight(1, gulfstream, 366 * DAY, 60);

        assertFalse(type(PilotController.buildQualifications(world, 1, now), "GA7C").isTypeCurrent());
    }

    @Test
    void theExperienceLevelIsOnTheTypeAndThePageHasNoRates() {
        // 25 flights of two hours: 25 flights and 50 hours are the level 1
        for (int i = 0; i < 25; i++) {
            flight(1, gulfstream, (i + 1) * DAY / 4, 120);
        }
        flight(1, citation, 2 * Time.ONE_HOUR, 60);

        final QualificationsDto q = PilotController.buildQualifications(world, 1, now);

        assertEquals(1, type(q, "GA7C").getExperienceLevel());
        assertEquals(0, type(q, "C25C").getExperienceLevel());
        // the rates (the shares of the levels, what is taken off the pay) are the affair of the operator, the qualifications only say whether a criterion is met
    }

    @Test
    void theTypesComeInTheOrderOfTheLastFlightAndKnowItsTime() {
        // the citation has much more hours, but the gulfstream was flown last
        flight(1, citation, 5 * DAY, 300);
        flight(1, citation, 6 * DAY, 300);
        final PilotLogbook.Entry last = flight(1, gulfstream, 1 * Time.ONE_HOUR, 30);
        flight(1, gulfstream, 7 * DAY, 30);

        final QualificationsDto q = PilotController.buildQualifications(world, 1, now);

        assertEquals("GA7C", q.getTypes().get(0).getAircraftType());
        assertEquals("C25C", q.getTypes().get(1).getAircraftType());
        assertEquals(last.getBlocksOnTime(), q.getTypes().get(0).getLastFlightTime());
        // the time of the last landing of the type, not of the first one
        assertEquals(now - 5 * DAY, q.getTypes().get(1).getLastFlightTime());
        assertTrue(q.getTypes().get(1).getFlightTime() > q.getTypes().get(0).getFlightTime());
    }
}
