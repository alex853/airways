package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PilotLogbookTest {
    private static final int START = Time.START_TIME_EPOCH_SECONDS;

    private World world;
    private PilotLogbook logbook;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), START);
        logbook = world.pilotLogbook();
    }

    private PilotLogbook.Entry entry(final int personId, final int blocksOff, final int blocksOn) {
        return logbook.create(personId, 77, 10, 20, blocksOff, blocksOn);
    }

    @Test
    public void test__new_world_has_an_empty_logbook() {
        assertEquals(0, logbook.all().count());
        assertTrue(logbook.byId(1).isEmpty());
    }

    @Test
    public void test__entry_keeps_all_the_fields() {
        final PilotLogbook.Entry entry = logbook.create(5, 77, 10, 20,
                START + 100 * Time.ONE_MINUTE, START + 190 * Time.ONE_MINUTE);

        final PilotLogbook.Entry loaded = logbook.byId(entry.getId()).orElseThrow();
        assertEquals(5, loaded.getPersonId());
        assertEquals(77, loaded.getAircraftId());
        assertEquals(10, loaded.getFromAirportId());
        assertEquals(20, loaded.getToAirportId());
        assertEquals(START + 100 * Time.ONE_MINUTE, loaded.getBlocksOffTime());
        assertEquals(START + 190 * Time.ONE_MINUTE, loaded.getBlocksOnTime());
        assertEquals(90, loaded.getDurationMinutes());
        assertEquals(1, loaded.getDayTakeoffs());
        assertEquals(0, loaded.getNightTakeoffs());
        assertEquals(1, loaded.getDayLandings());
        assertEquals(0, loaded.getNightLandings());
    }

    @Test
    public void test__time_has_an_accuracy_of_a_minute_the_seconds_are_dropped() {
        final PilotLogbook.Entry entry = entry(1, START + 61 * 60 + 59, START + 125 * 60 + 5);

        assertEquals(START + 61 * 60, entry.getBlocksOffTime());
        assertEquals(START + 125 * 60, entry.getBlocksOnTime());
        assertEquals(64, entry.getDurationMinutes());
    }

    @Test
    public void test__takeoff_and_landing_are_day_by_default_and_each_has_its_own_day_or_night() {
        final PilotLogbook.Entry entry = logbook.create(1, 77, 10, 20, START, START + 60);
        assertEquals(1, entry.getDayTakeoffs());
        assertEquals(0, entry.getNightTakeoffs());
        assertEquals(1, entry.getDayLandings());
        assertEquals(0, entry.getNightLandings());

        entry.setNightTakeoff(true);
        assertEquals(0, entry.getDayTakeoffs());
        assertEquals(1, entry.getNightTakeoffs());
        assertEquals(1, entry.getDayLandings());
        assertEquals(0, entry.getNightLandings());

        entry.setNightLanding(true);
        entry.setNightTakeoff(false);
        assertEquals(1, entry.getDayTakeoffs());
        assertEquals(0, entry.getNightTakeoffs());
        assertEquals(0, entry.getDayLandings());
        assertEquals(1, entry.getNightLandings());
    }

    @Test
    public void test__flight_time_is_the_difference_of_the_blocks_in_tenths_of_hour_rounded_half_up() {
        // 7 h 33 min is 453 min, 75.5 tenths, 7.6 hours
        assertEquals(7.6f, entry(1, START, START + 453 * 60).getFlightTime(), 0.001f);
        // 7 h 32 min is 452 min, 75.33 tenths, 7.5 hours
        assertEquals(7.5f, entry(1, START, START + 452 * 60).getFlightTime(), 0.001f);
        assertEquals(0.0f, entry(1, START, START).getFlightTime(), 0.001f);
        assertEquals(0.0f, entry(1, START, START + 2 * 60).getFlightTime(), 0.001f); // 0.03 hours
        assertEquals(0.1f, entry(1, START, START + 3 * 60).getFlightTime(), 0.001f); // 0.05 hours, half up
        assertEquals(1.5f, entry(1, START, START + 90 * 60).getFlightTime(), 0.001f); // 1.5 hours
        assertEquals(2.3f, entry(1, START, START + 138 * 60).getFlightTime(), 0.001f); // 2.3 hours
    }

    @Test
    public void test__flight_time_is_a_byte_it_holds_up_to_25_hours_5_tenths() {
        assertEquals(25.5f, entry(1, START, START + 1530 * 60).getFlightTime(), 0.001f); // 25.5 hours
        assertEquals(25.5f, entry(1, START, START + 2000 * 60).getFlightTime(), 0.001f); // longer is cut
    }

    @Test
    public void test__flight_time_conversion_of_minutes() {
        assertEquals(0.0f, PilotLogbook.flightTimeHours(0), 0.001f);
        assertEquals(0.0f, PilotLogbook.flightTimeHours(2), 0.001f);
        assertEquals(0.1f, PilotLogbook.flightTimeHours(3), 0.001f);
        assertEquals(0.1f, PilotLogbook.flightTimeHours(6), 0.001f);
        assertEquals(0.1f, PilotLogbook.flightTimeHours(8), 0.001f);
        assertEquals(0.2f, PilotLogbook.flightTimeHours(9), 0.001f);
        assertEquals(1.0f, PilotLogbook.flightTimeHours(60), 0.001f);
        assertEquals(25.5f, PilotLogbook.flightTimeHours(100000), 0.001f);
        assertThrows(IllegalArgumentException.class, () -> PilotLogbook.flightTimeHours(-1));
    }

    @Test
    public void test__night_and_ifr_time_are_zero_until_they_are_calculated_and_cannot_exceed_the_flight_time() {
        final PilotLogbook.Entry entry = entry(1, START, START + 138 * 60); // 2.3 hours
        assertEquals(0.0f, entry.getNightTime(), 0.001f);
        assertEquals(0.0f, entry.getIfrTime(), 0.001f);

        entry.setNightTime(0.8f);
        assertEquals(0.8f, entry.getNightTime(), 0.001f);
        entry.setIfr(true);
        assertEquals(2.3f, entry.getIfrTime(), 0.001f);
        assertEquals(2.3f, entry.getFlightTime(), 0.001f);

        assertThrows(IllegalArgumentException.class, () -> entry.setNightTime(2.4f));
        assertThrows(IllegalArgumentException.class, () -> entry.setNightTime(-0.1f));
        assertEquals(0.8f, entry.getNightTime(), 0.001f);
    }

    @Test
    public void test__times_are_three_bytes_after_the_counters_byte(@TempDir final Path dir) throws IOException {
        final PilotLogbook.Entry entry = entry(1, START, START + 138 * 60);
        entry.setNightTime(0.8f);
        // the header (1), the person (3), the aircraft (3), the airports (2 + 2), the two times (3 + 3) and the counters byte (1)
        final int offset = 1 + 3 + 3 + 2 + 2 + 3 + 3 + 1;

        logbook.save(dir);

        final byte[] data = Files.readAllBytes(dir.resolve("pilot-logbook"));
        assertEquals(32, data.length);
        assertEquals(23, data[offset]);
        assertEquals(8, data[offset + 1]);
        assertEquals(0, data[offset + 2]); // VFR: no IFR time
    }

    @Test
    public void test__ifr_time_is_the_whole_flight_time_when_the_flight_is_ifr_and_zero_when_it_is_vfr() {
        final PilotLogbook.Entry entry = entry(1, START, START + 453 * 60); // 7.6 hours
        assertEquals(0.0f, entry.getIfrTime(), 0.001f);

        entry.setIfr(true);
        assertEquals(7.6f, entry.getIfrTime(), 0.001f);
        assertEquals(7.6f, entry.getFlightTime(), 0.001f);

        entry.setIfr(false);
        assertEquals(0.0f, entry.getIfrTime(), 0.001f);
        assertFalse(entry.isIfr());
    }

    @Test
    public void test__flight_rules_flag_is_vfr_by_default_and_does_not_touch_day_and_night(@TempDir final Path dir) throws IOException {
        final PilotLogbook.Entry entry = entry(1, START, START + 3600);
        assertFalse(entry.isIfr());

        entry.setNightLanding(true);
        entry.setIfr(true);
        assertTrue(entry.isIfr());
        assertEquals(1, entry.getNightLandings());
        assertEquals(1, entry.getDayTakeoffs());

        entry.setIfr(false);
        assertFalse(entry.isIfr());
        assertEquals(1, entry.getNightLandings());
    }

    @Test
    public void test__flight_kind_byte__ifr_is_bit_0_night_takeoff_and_night_landing_are_the_top_bits_and_the_middle_is_free(@TempDir final Path dir) throws IOException {
        final PilotLogbook.Entry entry = logbook.create(1, 77, 10, 20, START, START + 60);
        // the byte follows the header (1), the person (3), the aircraft (3), the airports (2 + 2) and the two times (3 + 3)
        final int offset = 1 + 3 + 3 + 2 + 2 + 3 + 3;

        logbook.save(dir);
        assertEquals((byte) 0x00, Files.readAllBytes(dir.resolve("pilot-logbook"))[offset]); // VFR, day takeoff, day landing

        entry.setIfr(true);
        logbook.save(dir);
        assertEquals((byte) 0x01, Files.readAllBytes(dir.resolve("pilot-logbook"))[offset]);

        entry.setNightTakeoff(true);
        logbook.save(dir);
        assertEquals((byte) 0x41, Files.readAllBytes(dir.resolve("pilot-logbook"))[offset]);

        entry.setNightLanding(true);
        logbook.save(dir);
        assertEquals((byte) 0xC1, Files.readAllBytes(dir.resolve("pilot-logbook"))[offset]);

        entry.setIfr(false);
        logbook.save(dir);
        assertEquals((byte) 0xC0, Files.readAllBytes(dir.resolve("pilot-logbook"))[offset]); // bits 1-5 stay free
    }

    @Test
    public void test__three_bytes_of_minutes_are_about_31_years() {
        // 2^24 minutes
        assertTrue((1 << 24) / (365.0 * 24 * 60) > 31 && (1 << 24) / (365.0 * 24 * 60) < 32);

        // the world time is int seconds, it ends in 2038, that is earlier than the minutes do
        final PilotLogbook.Entry entry = entry(1, Integer.MAX_VALUE - 600, Integer.MAX_VALUE);
        assertTrue(entry.getBlocksOnTime() <= Integer.MAX_VALUE && entry.getBlocksOnTime() > Integer.MAX_VALUE - 60);
    }

    @Test
    public void test__invalid_values_are_refused() {
        assertThrows(IllegalArgumentException.class, () -> entry(1, START - 60, START + 60)); // before the start of the world
        assertThrows(IllegalArgumentException.class, () -> entry(1, START + 120, START + 60)); // blocks on is before blocks off
        assertThrows(IllegalArgumentException.class, () -> entry(0, START, START + 60)); // no person
        assertThrows(IllegalArgumentException.class, () -> logbook.create(1, 0, 10, 20, START, START + 60));
        assertThrows(IllegalArgumentException.class, () -> logbook.create(1, 77, 0, 20, START, START + 60));
        assertThrows(IllegalArgumentException.class, () -> logbook.create(1, 77, 10, 65536, START, START + 60));
        assertEquals(0, logbook.all().count());
    }

    @Test
    public void test__zero_duration_is_allowed() {
        assertEquals(0, entry(1, START + 600, START + 600).getDurationMinutes());
    }

    @Test
    public void test__entries_of_a_person_are_found_by_person_id() {
        entry(1, START, START + 60);
        entry(2, START, START + 60);
        entry(1, START + 3600, START + 7200);

        assertEquals(2, logbook.filter(logbook.byPersonId(1)).count());
        assertEquals(1, logbook.filter(logbook.byPersonId(2)).count());
        assertEquals(0, logbook.filter(logbook.byPersonId(3)).count());
    }

    @Test
    public void test__record_is_32_bytes_and_the_logbook_survives_save_and_load(@TempDir final Path dir) throws IOException {
        entry(1, START, START + 3600);
        final PilotLogbook.Entry second = logbook.create(2, 78, 11, 21, START + 7200, START + 9000);
        second.setNightLanding(true);

        logbook.save(dir);

        assertEquals(2 * 32, Files.size(dir.resolve("pilot-logbook")));

        final PilotLogbook loaded = new PilotLogbook();
        loaded.loadIfExists(dir);
        assertEquals(2, loaded.all().count());
        final PilotLogbook.Entry entry = loaded.byId(second.getId()).orElseThrow();
        assertEquals(2, entry.getPersonId());
        assertEquals(78, entry.getAircraftId());
        assertEquals(11, entry.getFromAirportId());
        assertEquals(21, entry.getToAirportId());
        assertEquals(30, entry.getDurationMinutes());
        assertEquals(1, entry.getNightLandings());
        assertEquals(1, entry.getDayTakeoffs());
        assertEquals(0, entry.getDayLandings());
    }
}
