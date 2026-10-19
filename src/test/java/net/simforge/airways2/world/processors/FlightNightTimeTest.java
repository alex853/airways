package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Day or night of the takeoff and of the landing and the night time of the flight: the flight accumulates the seconds which were at night
 * while the position reports come, the last piece goes at the landing. The place is London, midsummer: the night is from 20:51 to 03:13 UTC.
 */
class FlightNightTimeTest {
    private static final double LAT = 51.5;
    private static final double LON = -0.1;

    private World world;
    private FlightMissionControl control;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Aircrafts.Aircraft aircraft;
    private FlightMissions.Mission mission;

    private static int utc(final int hour, final int minute) {
        return (int) LocalDateTime.of(2025, 6, 21, 0, 0).plusHours(hour).plusMinutes(minute).toEpochSecond(ZoneOffset.UTC); // the hour can be over 23: the next day
    }

    private void startFlightAt(final int takeoffTime) {
        world = World.create(new InMemoryStorageStrategy(), takeoffTime);
        control = world.flightMissionControl();
        airportA = world.airports().create(LAT, LON, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(LAT, LON + 0.5, "BBB", "BBBB", "Bravo");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
        mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, takeoffTime, takeoffTime + 3600);

        assertTrue(control.start(mission).started());
        control.blocksOff(mission);
        control.takeoff(mission);
    }

    private void positionReportAfter(final int seconds) {
        world.setWorldTime(world.getWorldTime() + seconds);
        control.accountNightTime(mission, LAT, LON);
        aircraft.setLastUpdated(world.getWorldTime()); // the tracker updates the aircraft right after
    }

    private void landAfter(final int seconds) {
        world.setWorldTime(world.getWorldTime() + seconds);
        control.landing(mission, airportB);
    }

    @Test
    public void test__a_flight_at_night__night_takeoff_night_landing_and_all_the_flight_time_is_night() {
        startFlightAt(utc(22, 0));
        assertTrue(mission.isNightTakeoff());

        positionReportAfter(600);
        landAfter(600);

        assertTrue(mission.isNightLanding());
        assertEquals(1200, mission.getNightSeconds());
    }

    @Test
    public void test__a_flight_at_day__no_night_at_all() {
        startFlightAt(utc(12, 0));

        positionReportAfter(600);
        landAfter(600);

        assertFalse(mission.isNightTakeoff());
        assertFalse(mission.isNightLanding());
        assertEquals(0, mission.getNightSeconds());
    }

    @Test
    public void test__flight_into_the_morning__night_takeoff_day_landing_and_only_the_night_part_is_counted() {
        startFlightAt(utc(2, 40)); // the night ends at about 03:13
        assertTrue(mission.isNightTakeoff());

        for (int i = 0; i < 40; i++) { // a report every minute, till 03:20
            positionReportAfter(60);
        }
        landAfter(60); // 03:21

        assertFalse(mission.isNightLanding());
        // from 02:40 to 03:13 it is about 33 minutes
        assertTrue(Math.abs(mission.getNightSeconds() - 33 * 60) <= 4 * 60, "night seconds " + mission.getNightSeconds());
    }

    @Test
    public void test__flight_into_the_evening__day_takeoff_night_landing() {
        startFlightAt(utc(20, 30)); // the night begins at about 20:51
        assertFalse(mission.isNightTakeoff());

        for (int i = 0; i < 40; i++) {
            positionReportAfter(60);
        }
        landAfter(60); // 21:11

        assertTrue(mission.isNightLanding());
        // from 20:51 to 21:11 it is about 20 minutes
        assertTrue(Math.abs(mission.getNightSeconds() - 20 * 60) <= 4 * 60, "night seconds " + mission.getNightSeconds());
    }

    @Test
    public void test__the_piece_from_the_last_report_to_the_landing_is_counted_too() {
        startFlightAt(utc(23, 0));

        landAfter(1800); // no reports at all

        assertEquals(900, mission.getNightSeconds()); // 30 minutes, but a piece is limited by 15 minutes
        assertTrue(mission.isNightLanding());
    }

    @Test
    public void test__a_gap_longer_than_15_minutes_is_counted_as_15_minutes() {
        startFlightAt(utc(23, 0));

        positionReportAfter(3600); // the tracker was offline

        assertEquals(FlightMissionControl.MAX_NIGHT_INTERVAL_SECONDS, mission.getNightSeconds());
    }

    @Test
    public void test__reports_of_a_mission_which_is_not_flying_change_nothing() {
        startFlightAt(utc(23, 0));
        positionReportAfter(600);
        landAfter(60);
        final int nightSeconds = mission.getNightSeconds();

        world.setWorldTime(world.getWorldTime() + 600);
        control.accountNightTime(mission, LAT, LON);

        assertEquals(nightSeconds, mission.getNightSeconds());
    }

    @Test
    public void test__report_at_the_same_second_as_the_previous_update_adds_nothing() {
        startFlightAt(utc(23, 0));

        control.accountNightTime(mission, LAT, LON);

        assertEquals(0, mission.getNightSeconds());
    }

    @Test
    public void test__the_position_of_the_report_decides__the_same_time_is_day_far_to_the_west() {
        startFlightAt(utc(23, 0));
        world.setWorldTime(world.getWorldTime() + 600);

        // 23:10 UTC is the afternoon at 100 degrees to the west
        control.accountNightTime(mission, LAT, -100.0);

        assertEquals(0, mission.getNightSeconds());
    }

    @Test
    public void test__night_time_starts_at_blocks_off_the_taxi_before_the_takeoff_is_night_time() {
        world = World.create(new InMemoryStorageStrategy(), utc(22, 0));
        control = world.flightMissionControl();
        airportA = world.airports().create(LAT, LON, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(LAT, LON + 0.5, "BBB", "BBBB", "Bravo");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
        mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, utc(22, 0), utc(23, 0));
        assertTrue(control.start(mission).started());

        control.blocksOff(mission); // 22:00
        world.setWorldTime(utc(22, 7));
        control.takeoff(mission); // 7 minutes of the taxi

        assertEquals(7 * 60, mission.getNightSeconds());
    }

    @Test
    public void test__night_time_goes_on_after_the_landing_till_blocks_on() {
        startFlightAt(utc(22, 0));
        positionReportAfter(600);
        landAfter(600); // 22:20
        assertEquals(1200, mission.getNightSeconds());

        world.setWorldTime(utc(22, 26));
        control.blocksOn(mission); // 6 minutes of the taxi

        assertEquals(1200 + 6 * 60, mission.getNightSeconds());
    }

    @Test
    public void test__night_flight_from_blocks_off_to_blocks_on_night_time_equals_flight_time() {
        world = World.create(new InMemoryStorageStrategy(), utc(22, 0));
        control = world.flightMissionControl();
        airportA = world.airports().create(LAT, LON, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(LAT, LON + 0.5, "BBB", "BBBB", "Bravo");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
        mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, utc(22, 0), utc(23, 0));
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 7,
                Persons.LocationType.Airport, airportA.getId(), 0, "AK", 0);
        mission.setUserId(7);
        assertTrue(control.start(mission).started());

        control.blocksOff(mission); // 22:00
        world.setWorldTime(utc(22, 10));
        control.takeoff(mission); // 22:10
        for (int i = 0; i < 100; i++) { // a report every minute
            positionReportAfter(60);
        }
        world.setWorldTime(utc(23, 50));
        control.landing(mission, airportB); // 23:50
        world.setWorldTime(utc(24, 12)); // blocks on at 00:12 of the next day, 2 hours 12 minutes after blocks off
        control.blocksOn(mission);
        control.finish(mission);

        final PilotLogbook.Entry entry = world.pilotLogbook().filter(world.pilotLogbook().byPersonId(person.getId())).findFirst().orElseThrow();
        assertEquals(22, entry.getFlightTimeTenths()); // 2.2 hours
        assertEquals(22, entry.getNightTimeTenths()); // all of it is at night, the taxi included
        assertEquals(1, entry.getNightTakeoffs());
        assertEquals(1, entry.getNightLandings());
    }

    @Test
    public void test__the_taxi_in_the_day_is_not_night_time() {
        startFlightAt(utc(12, 0));
        landAfter(1200);
        world.setWorldTime(utc(12, 26));
        control.blocksOn(mission);

        assertEquals(0, mission.getNightSeconds());
    }

    @Test
    public void test__taxi_after_the_landing_is_counted_by_the_landing_airport() {
        startFlightAt(utc(12, 0));
        landAfter(1200);
        // the mission was planned to another airport, but the landing was at B: the day or night of the taxi is by the place of the landing
        world.setWorldTime(utc(12, 26));
        control.blocksOn(mission);

        assertFalse(mission.isNightLanding());
        assertEquals(0, mission.getNightSeconds());
    }

    @Test
    public void test__night_time_goes_to_the_logbook_of_the_pilot_in_tenths_of_hour() {
        startFlightAt(utc(22, 0));
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 7,
                Persons.LocationType.Airport, airportA.getId(), 0, "AK", 0);
        mission.setUserId(7);

        positionReportAfter(600);
        landAfter(600); // 22:20
        world.setWorldTime(world.getWorldTime() + 300);
        control.blocksOn(mission); // 22:25
        control.finish(mission);

        final PilotLogbook.Entry entry = world.pilotLogbook().filter(world.pilotLogbook().byPersonId(person.getId())).findFirst().orElseThrow();
        assertEquals(1, entry.getNightTakeoffs());
        assertEquals(0, entry.getDayTakeoffs());
        assertEquals(1, entry.getNightLandings());
        assertEquals(0, entry.getDayLandings());
        assertEquals(4, entry.getFlightTimeTenths()); // 25 minutes
        assertEquals(4, entry.getNightTimeTenths()); // the same 25 minutes
    }

    @Test
    public void test__night_time_in_the_logbook_cannot_be_longer_than_the_flight_time() {
        startFlightAt(utc(22, 0));
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 7,
                Persons.LocationType.Airport, airportA.getId(), 0, "AK", 0);
        mission.setUserId(7);
        mission.addNightSeconds(60000); // too much on purpose

        landAfter(60);
        control.blocksOn(mission);
        control.finish(mission);

        final PilotLogbook.Entry entry = world.pilotLogbook().all().findFirst().orElseThrow();
        assertTrue(entry.getNightTimeTenths() <= entry.getFlightTimeTenths());
        assertEquals(entry.getFlightTimeTenths(), entry.getNightTimeTenths());
    }

    @Test
    public void test__night_flags_and_seconds_of_the_mission_default_to_day_and_zero_and_are_independent() {
        startFlightAt(utc(12, 0));
        final FlightMissions.Mission another = world.flightMissions().createDispatchedMission(
                aircraft, airportA, airportB, utc(13, 0), utc(14, 0));

        another.setNightTakeoff(true);
        another.addNightSeconds(100);
        another.addNightSeconds(200);

        assertTrue(another.isNightTakeoff());
        assertFalse(another.isNightLanding());
        assertEquals(300, another.getNightSeconds());
        assertFalse(mission.isNightTakeoff());
        assertEquals(0, mission.getNightSeconds());
        assertEquals(FlightMissions.Status.Dispatched, another.getStatus());
        assertEquals(FlightMissions.CharacterMode.NPC, another.getCharacterMode());

        another.addNightSeconds(70000); // more than 16 bits, and it is fine
        assertEquals(300 + 70000, another.getNightSeconds());

        another.addNightSeconds(20_000_000);
        assertEquals(16_777_215, another.getNightSeconds()); // 24 bits
    }

}
