package net.simforge.airways2.pilottracker;

import net.simforge.airways2.pilottracker.track.TrackLeg;
import net.simforge.airways2.pilottracker.track.TrackPosition;
import net.simforge.commons.misc.Geo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SimTrackerContextStoreTest {
    private static final long NOW = 1_700_000_000_000L;
    private static final long MAX_AGE = 30 * 60 * 1000L;

    @TempDir
    Path tempDir;

    private Path file;
    private SimTrackerContextStore store;

    @BeforeEach
    public void setUp() {
        file = tempDir.resolve("sim-tracker").resolve("contexts.json");
        store = new SimTrackerContextStore(file);
    }

    @Test
    public void roundTrip___all_fields_are_restored() throws Exception {
        final SimTracker.Context context = context(7, NOW - 5_000, true, "EGLL", 42);

        assertEquals(1, store.save(List.of(context), NOW));

        final SimTrackerContextStore.Result result = store.load(NOW, MAX_AGE);
        assertEquals(1, result.restored().size());
        assertEquals(0, result.stale());
        assertEquals(0, result.broken());

        final SimTracker.Context restored = result.restored().get(0);
        assertEquals(7, restored.getUserId());
        assertEquals(42, restored.getFlightMissionId());
        assertTrue(restored.isParkingBrake());
        assertEquals(2, restored.getNumberOfEnginesRunning());
        assertEquals(12.5f, restored.getMeasuredGs());

        final TrackPosition position = restored.getPosition();
        assertEquals(NOW - 5_000, position.getTime());
        assertTrue(position.isOnGround());
        assertEquals(51.47, position.getCoords().getLat(), 1e-9);
        assertEquals(-0.4543, position.getCoords().getLon(), 1e-9);
        assertEquals("EGLL", position.getAirportIcao());
        assertEquals(270, position.getHeading());
        assertEquals(83, position.getAltitude());

        assertEquals(2, restored.getTrackTrail().size());
        assertEquals(0.1, restored.getTrackTrail().get(0).getDistance(), 1e-9);
        assertEquals(5.0, restored.getTrackTrail().get(0).getTime(), 1e-9);

        // actions are not stored, they are recalculated
        assertNull(restored.getActions());
    }

    @Test
    public void null_fields_are_supported() throws Exception {
        final SimTracker.Context context = SimTracker.Context.builder()
                .userId(3)
                .position(new TrackPosition(NOW, false, Geo.coords(10, 20), null, 0, 35000))
                .build();

        store.save(List.of(context), NOW);

        final SimTracker.Context restored = store.load(NOW, MAX_AGE).restored().get(0);
        assertNull(restored.getFlightMissionId());
        assertNull(restored.getTrackTrail());
        assertNull(restored.getPosition().getAirportIcao());
        assertFalse(restored.getPosition().isOnGround());
    }

    @Test
    public void contexts_without_position_are_not_saved() throws Exception {
        final SimTracker.Context noPosition = SimTracker.Context.forUser(1);

        assertEquals(0, store.save(List.of(noPosition), NOW));
        assertEquals(0, store.load(NOW, MAX_AGE).restored().size());
    }

    @Test
    public void stale_contexts_are_dropped() throws Exception {
        final SimTracker.Context fresh = context(1, NOW - 60_000, true, "EGLL", 1);
        final SimTracker.Context old = context(2, NOW - MAX_AGE - 1, true, "EGLL", 2);

        store.save(List.of(fresh, old), NOW);

        final SimTrackerContextStore.Result result = store.load(NOW, MAX_AGE);
        assertEquals(1, result.restored().size());
        assertEquals(1, result.restored().get(0).getUserId());
        assertEquals(1, result.stale());
    }

    @Test
    public void missing_file___nothing_restored() {
        final SimTrackerContextStore.Result result = store.load(NOW, MAX_AGE);
        assertEquals(0, result.restored().size());
    }

    @Test
    public void corrupted_file___nothing_restored_and_no_exception() throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ this is not json");

        assertEquals(0, store.load(NOW, MAX_AGE).restored().size());
    }

    @Test
    public void empty_file___nothing_restored() throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");

        assertEquals(0, store.load(NOW, MAX_AGE).restored().size());
    }

    @Test
    public void unknown_version___nothing_restored() throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"version\": 999, \"savedAt\": 1, \"contexts\": []}");

        assertEquals(0, store.load(NOW, MAX_AGE).restored().size());
    }

    @Test
    public void broken_context___is_skipped_and_others_are_restored() throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"version": 1, "savedAt": 1, "contexts": [
                  {"userId": 5},
                  {"userId": 6, "position": {"time": %d, "onGround": true, "lat": 1.0, "lon": 2.0, "heading": 0, "altitude": 0},
                   "trackTrail": ["not-a-leg"]},
                  {"userId": 8, "position": {"time": %d, "onGround": true, "lat": 1.0, "lon": 2.0, "heading": 0, "altitude": 0}}
                ]}
                """.formatted(NOW, NOW));

        final SimTrackerContextStore.Result result = store.load(NOW, MAX_AGE);
        assertEquals(1, result.restored().size());
        assertEquals(8, result.restored().get(0).getUserId());
        assertEquals(2, result.broken());
    }

    @Test
    public void save_overwrites_previous_file_and_leaves_no_temp_file() throws Exception {
        store.save(List.of(context(1, NOW, true, "EGLL", 1)), NOW);
        store.save(List.of(context(2, NOW, true, "EGLL", 2)), NOW);

        final SimTrackerContextStore.Result result = store.load(NOW, MAX_AGE);
        assertEquals(1, result.restored().size());
        assertEquals(2, result.restored().get(0).getUserId());

        assertFalse(Files.exists(file.resolveSibling("contexts.json.tmp")));
    }

    @Test
    public void file_with_the_night_time_meter_of_the_old_format_is_restored_and_the_meter_is_ignored() throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"version": 1, "savedAt": 1, "contexts": [
                  {"userId": 9, "position": {"time": %d, "onGround": true, "lat": 1.0, "lon": 2.0, "heading": 0, "altitude": 0},
                   "flightMissionId": 11, "hasNightMeter": true, "nightSeconds": 4321.5, "nightCountedAt": 1750000000}
                ]}
                """.formatted(NOW));

        final SimTracker.Context restored = store.load(NOW, MAX_AGE).restored().get(0);

        // the night time meter is kept by the flight mission control now, an old file only has to be readable
        assertEquals(11, restored.getFlightMissionId());
    }

    private static SimTracker.Context context(final int userId, final long positionTime, final boolean onGround,
                                              final String airportIcao, final int flightMissionId) {
        final List<TrackLeg> trail = new ArrayList<>();
        trail.add(new TrackLeg(0.1, 5.0));
        trail.add(new TrackLeg(0.2, 5.0));

        return SimTracker.Context.builder()
                .userId(userId)
                .position(new TrackPosition(positionTime, onGround, Geo.coords(51.47, -0.4543), airportIcao, 270, 83))
                .parkingBrake(true)
                .numberOfEnginesRunning(2)
                .trackTrail(trail)
                .measuredGs(12.5f)
                .flightMissionId(flightMissionId)
                .build();
    }
}
