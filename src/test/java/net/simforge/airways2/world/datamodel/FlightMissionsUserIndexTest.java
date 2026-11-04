package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The missions of a user are found by the index kept by FlightMissions (allByUserId), it follows the user of a mission, the deletion and the loading.
 */
public class FlightMissionsUserIndexTest {
    private World world;
    private Airports.Airport origin;
    private Airports.Airport destination;
    private Aircrafts.Aircraft aircraft;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        origin = world.airports().create(0, 0, "A", "AAAA", "AAAA");
        destination = world.airports().create(0, 10, "B", "BBBB", "BBBB");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-1000", origin);
    }

    private FlightMissions.Mission mission(final int userId) {
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, origin, destination, world.getWorldTime(), world.getWorldTime() + 3600);
        mission.setUserId(userId);
        return mission;
    }

    private List<Integer> idsOf(final int userId) {
        return world.flightMissions().allByUserId(userId).map(FlightMissions.Mission::getId).toList();
    }

    @Test
    public void missionsOfAUserAreFoundInTheOrderOfIds() {
        final FlightMissions.Mission m1 = mission(5);
        mission(6);
        final FlightMissions.Mission m3 = mission(5);

        assertEquals(List.of(m1.getId(), m3.getId()), idsOf(5));
        assertEquals(1, idsOf(6).size());
        assertEquals(List.of(), idsOf(7));
    }

    @Test
    public void changeOfTheUserMovesTheMission() {
        final FlightMissions.Mission m = mission(5);

        m.setUserId(6);
        assertEquals(List.of(), idsOf(5));
        assertEquals(List.of(m.getId()), idsOf(6));

        m.setUserId(0);
        assertEquals(List.of(), idsOf(6));
    }

    @Test
    public void deletedMissionIsNotFound() {
        final FlightMissions.Mission m1 = mission(5);
        final FlightMissions.Mission m2 = mission(5);

        world.flightMissions().deleteById(m1.getId());

        assertEquals(List.of(m2.getId()), idsOf(5));
    }

    @Test
    public void indexIsRebuiltAfterLoading(@TempDir final Path dir) throws IOException {
        final FlightMissions.Mission m1 = mission(5);
        mission(6);
        world.flightMissions().save(dir);

        final FlightMissions loaded = new FlightMissions();
        loaded.loadIfExists(dir);

        assertEquals(List.of(m1.getId()), loaded.allByUserId(5).map(FlightMissions.Mission::getId).toList());
        assertEquals(1, loaded.allByUserId(6).count());
    }
}
