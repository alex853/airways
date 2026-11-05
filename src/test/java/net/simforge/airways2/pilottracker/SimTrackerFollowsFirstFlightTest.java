package net.simforge.airways2.pilottracker;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.WorldAccess;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The sim tracker of the user follows the first flight of the roster which is not over. A flight which is not started yet does not hold it: a flight booked
 * (or moved) before it takes the tracker (the dashboard opened the later flight otherwise), but a started flight is kept.
 */
class SimTrackerFollowsFirstFlightTest {
    private static final int USER_ID = 7;

    private World world;
    private SimTracker tracker;
    private Airports.Airport airport;
    private Aircrafts.Aircraft aircraft;

    @BeforeEach
    void beforeEach(@TempDir final Path dir) {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        airport = world.airports().create(51.5, -0.1, "AAA", "AAAA", "Alpha");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airport);

        tracker = new SimTracker(dir.resolve("contexts.json"));
        tracker.setWorldAccess(new WorldAccess() {
            @Override
            public <T> T read(final Action<T> action) {
                return action.invoke(world);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public int getWorldTime() {
                return world.getWorldTime();
            }

            @Override
            public <T> T modifySync(final Action<T> action) {
                return action.invoke(world);
            }
        });
    }

    private FlightMissions.Mission flight(final int hoursFromNow) {
        final int departure = world.getWorldTime() + hoursFromNow * Time.ONE_HOUR;
        final FlightMissions.Mission m = world.flightMissions().createDispatchedMission(aircraft, airport, airport, departure, departure + Time.ONE_HOUR);
        m.setUserId(USER_ID);
        return m;
    }

    @SuppressWarnings("unchecked")
    private SimTracker.Context follow(final FlightMissions.Mission m) {
        final Map<Integer, SimTracker.Context> contexts = (Map<Integer, SimTracker.Context>) ReflectionTestUtils.getField(tracker, "userContexts");
        assertNotNull(contexts);
        contexts.put(USER_ID, SimTracker.Context.forUser(USER_ID).switchToFlightMissionId(m.getId()));
        tracker.refreshContext(USER_ID);
        return contexts.get(USER_ID);
    }

    @Test
    void anEarlierFlightTakesTheTrackerFromANotStartedFlight() {
        final FlightMissions.Mission later = flight(24);
        final FlightMissions.Mission earlier = flight(3);

        assertEquals(earlier.getId(), follow(later).getFlightMissionId());
    }

    @Test
    void aStartedFlightIsKept() {
        final FlightMissions.Mission later = flight(24);
        final FlightMissions.Mission earlier = flight(3);
        later.setStatus(FlightMissions.Status.Preflight);

        assertEquals(later.getId(), follow(later).getFlightMissionId());
    }
}
