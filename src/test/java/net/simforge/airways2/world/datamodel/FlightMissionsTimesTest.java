package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The six times of a flight mission are the date of flight and 12 bits of minutes each, two in a 24-bit field, accessed as bit sections. The format on
 * the disk must stay the same as it has always been.
 */
class FlightMissionsTimesTest {
    private World world;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Aircrafts.Aircraft aircraft;

    private static int utc(final int day, final int hour, final int minute) {
        return (int) LocalDateTime.of(2025, 6, day, 0, 0).plusHours(hour).plusMinutes(minute).toEpochSecond(ZoneOffset.UTC);
    }

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), utc(21, 0, 0));
        airportA = world.airports().create(51.5, -0.1, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(51.5, 0.4, "BBB", "BBBB", "Bravo");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
    }

    private FlightMissions.Mission mission() {
        return world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, utc(21, 12, 30), utc(21, 14, 0));
    }

    @Test
    public void test__planned_times_and_the_date_of_flight_come_from_the_creation() {
        final FlightMissions.Mission mission = mission();

        assertEquals(LocalDate.of(2025, 6, 21), mission.getDateOfFlight());
        assertEquals(utc(21, 12, 30), mission.getPlannedDepartureWorldTime());
        assertEquals(utc(21, 14, 0), mission.getPlannedArrivalWorldTime());
        assertEquals(0, mission.getActualDepartureWorldTime());
        assertEquals(0, mission.getActualTakeoffWorldTime());
        assertEquals(0, mission.getActualLandingWorldTime());
        assertEquals(0, mission.getActualArrivalWorldTime());
    }

    @Test
    public void test__all_six_times_are_independent() {
        final FlightMissions.Mission mission = mission();

        mission.setActualDepartureWorldTime(utc(21, 12, 40));
        mission.setActualTakeoffWorldTime(utc(21, 12, 52));
        mission.setActualLandingWorldTime(utc(21, 14, 5));
        mission.setActualArrivalWorldTime(utc(21, 14, 11));

        assertEquals(utc(21, 12, 30), mission.getPlannedDepartureWorldTime());
        assertEquals(utc(21, 14, 0), mission.getPlannedArrivalWorldTime());
        assertEquals(utc(21, 12, 40), mission.getActualDepartureWorldTime());
        assertEquals(utc(21, 12, 52), mission.getActualTakeoffWorldTime());
        assertEquals(utc(21, 14, 5), mission.getActualLandingWorldTime());
        assertEquals(utc(21, 14, 11), mission.getActualArrivalWorldTime());

        mission.setActualTakeoffWorldTime(utc(21, 12, 53));
        assertEquals(utc(21, 12, 40), mission.getActualDepartureWorldTime());
        assertEquals(utc(21, 12, 53), mission.getActualTakeoffWorldTime());

        mission.setActualTakeoffWorldTime(0);
        assertEquals(0, mission.getActualTakeoffWorldTime());
        assertEquals(utc(21, 12, 40), mission.getActualDepartureWorldTime());
    }

    @Test
    public void test__a_time_of_the_next_day_and_of_the_previous_evening() {
        final FlightMissions.Mission mission = mission();

        mission.setActualArrivalWorldTime(utc(22, 3, 15)); // 15 hours after the midnight of the next day
        mission.setActualDepartureWorldTime(utc(20, 20, 0)); // the evening before

        assertEquals(utc(22, 3, 15), mission.getActualArrivalWorldTime());
        assertEquals(utc(20, 20, 0), mission.getActualDepartureWorldTime());
    }

    @Test
    public void test__a_time_out_of_the_window_of_the_date_is_refused() {
        final FlightMissions.Mission mission = mission();

        assertThrows(IllegalArgumentException.class, () -> mission.setActualArrivalWorldTime(utc(23, 12, 0))); // 60 hours after the midnight
        assertEquals(0, mission.getActualArrivalWorldTime());
    }

    @Test
    public void test__the_seconds_are_dropped() {
        final FlightMissions.Mission mission = mission();

        mission.setActualDepartureWorldTime(utc(21, 12, 40) + 59);

        assertEquals(utc(21, 12, 40), mission.getActualDepartureWorldTime());
    }

    @Test
    public void test__the_format_on_the_disk_is_unchanged__the_first_time_is_in_the_high_12_bits(@TempDir final Path dir) throws IOException {
        final FlightMissions.Mission mission = mission();
        // the header (1), the aircraft (3), the status (1), the heartbeat (4), the airports (2 + 2) and the date of flight (2)
        final int offsetOfPlannedTimes = 1 + 3 + 1 + 4 + 2 + 2 + 2;

        world.flightMissions().save(dir);

        final byte[] data = Files.readAllBytes(dir.resolve("flight-missions"));
        // the date of flight: 172 days
        assertEquals(0, data[offsetOfPlannedTimes - 2]);
        assertEquals((byte) 172, data[offsetOfPlannedTimes - 1]);
        // 12:30 is 750 minutes, 1750 is 0x6D6; 14:00 is 840 minutes, 1840 is 0x730: 0x6D6730
        assertEquals((byte) 0x6D, data[offsetOfPlannedTimes]);
        assertEquals((byte) 0x67, data[offsetOfPlannedTimes + 1]);
        assertEquals((byte) 0x30, data[offsetOfPlannedTimes + 2]);
        assertEquals(utc(21, 12, 30), mission.getPlannedDepartureWorldTime());
    }

    @Test
    public void test__the_times_survive_save_and_load(@TempDir final Path dir) throws IOException {
        final FlightMissions.Mission mission = mission();
        mission.setActualDepartureWorldTime(utc(21, 12, 40));
        mission.setActualArrivalWorldTime(utc(22, 0, 11));
        mission.setNightMinutes(132.0f);
        mission.setNightTakeoff(true);

        world.flightMissions().save(dir);
        final FlightMissions loaded = new FlightMissions();
        loaded.loadIfExists(dir);

        final FlightMissions.Mission loadedMission = loaded.byId(mission.getId()).orElseThrow();
        assertEquals(LocalDate.of(2025, 6, 21), loadedMission.getDateOfFlight());
        assertEquals(utc(21, 12, 30), loadedMission.getPlannedDepartureWorldTime());
        assertEquals(utc(21, 14, 0), loadedMission.getPlannedArrivalWorldTime());
        assertEquals(utc(21, 12, 40), loadedMission.getActualDepartureWorldTime());
        assertEquals(utc(22, 0, 11), loadedMission.getActualArrivalWorldTime());
        assertEquals(132.0f, loadedMission.getNightMinutes(), 0.0f);
        assertEquals(true, loadedMission.isNightTakeoff());
    }

    @Test
    public void test__tracking_mode_is_none_by_default_and_does_not_touch_the_night_flags(@TempDir final Path dir) throws IOException {
        final FlightMissions.Mission mission = mission();
        assertEquals(FlightMissions.TrackingMode.None, mission.getTrackingMode());

        mission.setNightTakeoff(true);
        mission.setNightLanding(true);
        for (final FlightMissions.TrackingMode mode : FlightMissions.TrackingMode.values()) {
            mission.setTrackingMode(mode);
            assertEquals(mode, mission.getTrackingMode());
            assertEquals(true, mission.isNightTakeoff());
            assertEquals(true, mission.isNightLanding());
        }

        mission.setTrackingMode(FlightMissions.TrackingMode.Manual);
        mission.setNightTakeoff(false);
        assertEquals(FlightMissions.TrackingMode.Manual, mission.getTrackingMode());
        assertEquals(true, mission.isNightLanding());

        world.flightMissions().save(dir);
        final FlightMissions loaded = new FlightMissions();
        loaded.loadIfExists(dir);
        assertEquals(FlightMissions.TrackingMode.Manual, loaded.byId(mission.getId()).orElseThrow().getTrackingMode());
    }
}
