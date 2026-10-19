package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The night data of a flight: the sim tracker measures it (see NightTimeMeter) and puts it to the flight mission, the flight mission control does not
 * calculate it. At the end of the flight the data go to the logbook of the pilot.
 */
class FlightNightTimeTest {
    private World world;
    private FlightMissionControl control;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Aircrafts.Aircraft aircraft;
    private FlightMissions.Mission mission;
    private Persons.Person person;

    private static int utc(final int hour, final int minute) {
        return (int) LocalDateTime.of(2025, 6, 21, 0, 0).plusHours(hour).plusMinutes(minute).toEpochSecond(ZoneOffset.UTC);
    }

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), utc(22, 0));
        control = world.flightMissionControl();
        airportA = world.airports().create(51.5, -0.1, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(51.5, 0.4, "BBB", "BBBB", "Bravo");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
        mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, utc(22, 0), utc(23, 0));
        mission.setUserId(7);
        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 7,
                Persons.LocationType.Airport, airportA.getId(), 0, "AK", 0);
    }

    private void flyTheWholeFlight(final int blocksOffHour, final int blocksOffMinute, final int minutesInTheFlight) {
        world.setWorldTime(utc(blocksOffHour, blocksOffMinute));
        assertTrue(control.start(mission).started());
        control.blocksOff(mission);
        world.setWorldTime(utc(blocksOffHour, blocksOffMinute) + 10 * 60);
        control.takeoff(mission);
        world.setWorldTime(utc(blocksOffHour, blocksOffMinute) + (minutesInTheFlight - 10) * 60);
        control.landing(mission, airportB);
        world.setWorldTime(utc(blocksOffHour, blocksOffMinute) + minutesInTheFlight * 60);
        control.blocksOn(mission);
        control.finish(mission);
    }

    private PilotLogbook.Entry entry() {
        return world.pilotLogbook().filter(world.pilotLogbook().byPersonId(person.getId())).findFirst().orElseThrow();
    }

    // ---- the night data of the flight mission

    @Test
    public void test__a_new_mission_has_day_and_no_night_time() {
        assertFalse(mission.isNightTakeoff());
        assertFalse(mission.isNightLanding());
        assertEquals(0.0f, mission.getNightMinutes(), 0.0f);
    }

    @Test
    public void test__night_flags_and_night_minutes_are_independent_and_do_not_touch_other_missions() {
        final FlightMissions.Mission another = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, utc(13, 0), utc(14, 0));

        another.setNightTakeoff(true);
        another.setNightMinutes(132.0f);

        assertTrue(another.isNightTakeoff());
        assertFalse(another.isNightLanding());
        assertEquals(132.0f, another.getNightMinutes(), 0.0f);
        assertFalse(mission.isNightTakeoff());
        assertEquals(0.0f, mission.getNightMinutes(), 0.0f);
        assertEquals(FlightMissions.Status.Dispatched, another.getStatus());
        assertEquals(FlightMissions.CharacterMode.NPC, another.getCharacterMode());

        another.setNightLanding(true);
        another.setNightTakeoff(false);
        assertFalse(another.isNightTakeoff());
        assertTrue(another.isNightLanding());
        assertEquals(132.0f, another.getNightMinutes(), 0.0f);
    }

    @Test
    public void test__night_minutes_is_a_half_float__a_minute_up_to_34_hours_and_it_is_not_negative() {
        mission.setNightMinutes(1530.4f); // 25.5 hours
        assertEquals(1530.0f, mission.getNightMinutes(), 0.0f);

        mission.setNightMinutes(1530.6f);
        assertEquals(1531.0f, mission.getNightMinutes(), 0.0f);

        mission.setNightMinutes(12.34f);
        assertEquals(12.34f, mission.getNightMinutes(), 0.01f);

        assertThrows(IllegalArgumentException.class, () -> mission.setNightMinutes(-1.0f));
        assertThrows(IllegalArgumentException.class, () -> mission.setNightMinutes(70000.0f));
        assertEquals(12.34f, mission.getNightMinutes(), 0.01f);
    }

    @Test
    public void test__the_flight_mission_control_does_not_touch_the_night_data() {
        flyTheWholeFlight(22, 0, 60);

        assertFalse(mission.isNightTakeoff());
        assertFalse(mission.isNightLanding());
        assertEquals(0.0f, mission.getNightMinutes(), 0.0f);
    }

    // ---- the logbook gets the night data of the mission

    @Test
    public void test__night_flight_from_blocks_off_to_blocks_on__night_time_equals_flight_time() {
        // blocks off 22:00, blocks on 00:12: 2 hours 12 minutes, 2.2 hours
        mission.setNightTakeoff(true);
        mission.setNightLanding(true);
        mission.setNightMinutes(132.0f);

        flyTheWholeFlight(22, 0, 132);

        final PilotLogbook.Entry entry = entry();
        assertEquals(2.2f, entry.getFlightTime(), 0.001f);
        assertEquals(2.2f, entry.getNightTime(), 0.001f);
        assertEquals(1, entry.getNightTakeoffs());
        assertEquals(0, entry.getDayTakeoffs());
        assertEquals(1, entry.getNightLandings());
        assertEquals(0, entry.getDayLandings());
    }

    @Test
    public void test__night_minutes_go_to_the_logbook_in_tenths_of_hour() {
        mission.setNightMinutes(20.0f); // 0.33 hours

        flyTheWholeFlight(22, 0, 25); // 25 minutes, 0.4 hours

        final PilotLogbook.Entry entry = entry();
        assertEquals(0.4f, entry.getFlightTime(), 0.001f);
        assertEquals(0.3f, entry.getNightTime(), 0.001f);
    }

    @Test
    public void test__every_flight_in_the_logbook_is_ifr_for_now__ifr_time_is_the_whole_flight_time() {
        flyTheWholeFlight(12, 0, 132);

        final PilotLogbook.Entry entry = entry();
        assertTrue(entry.isIfr());
        assertEquals(2.2f, entry.getFlightTime(), 0.001f);
        assertEquals(2.2f, entry.getIfrTime(), 0.001f);
    }

    @Test
    public void test__a_flight_in_the_day_has_no_night_time_and_day_takeoff_and_landing() {
        flyTheWholeFlight(12, 0, 60);

        final PilotLogbook.Entry entry = entry();
        assertEquals(0.0f, entry.getNightTime(), 0.0f);
        assertEquals(1, entry.getDayTakeoffs());
        assertEquals(1, entry.getDayLandings());
    }

    @Test
    public void test__a_flight_from_the_day_into_the_night() {
        mission.setNightLanding(true);
        mission.setNightMinutes(30.0f);

        flyTheWholeFlight(20, 0, 90);

        final PilotLogbook.Entry entry = entry();
        assertEquals(1, entry.getDayTakeoffs());
        assertEquals(1, entry.getNightLandings());
        assertEquals(0.5f, entry.getNightTime(), 0.001f);
        assertEquals(1.5f, entry.getFlightTime(), 0.001f);
    }

    @Test
    public void test__night_time_in_the_logbook_cannot_be_longer_than_the_flight_time() {
        mission.setNightMinutes(5000.0f); // too much on purpose

        flyTheWholeFlight(22, 0, 60);

        final PilotLogbook.Entry entry = entry();
        assertEquals(1.0f, entry.getFlightTime(), 0.001f);
        assertEquals(entry.getFlightTime(), entry.getNightTime(), 0.001f);
    }

    @Test
    public void test__the_logbook_entry_keeps_the_date_and_the_times_of_blocks() {
        flyTheWholeFlight(22, 0, 132);

        final PilotLogbook.Entry entry = entry();
        assertEquals(LocalDate.of(2025, 6, 21), entry.getDateOfFlight());
        assertEquals(utc(22, 0), entry.getBlocksOffTime());
        assertEquals(utc(24, 12), entry.getBlocksOnTime()); // 00:12 of the next day
        assertEquals(132, entry.getDurationMinutes());
    }

    @Test
    public void test__a_flight_longer_than_the_window_of_the_logbook_is_not_written_and_does_not_break_the_finish() {
        // blocks on 26 hours after blocks off: the flight time byte holds up to 25.5 hours
        flyTheWholeFlight(22, 0, 26 * 60);

        assertEquals(FlightMissions.Status.Finished, mission.getStatus());
        assertEquals(0, world.pilotLogbook().all().count());
        assertEquals(Persons.State.Idle, person.getState());
    }
}
