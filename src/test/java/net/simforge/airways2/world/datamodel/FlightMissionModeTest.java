package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FlightMissionModeTest {

    private World world;
    private FlightMissions.Mission mission;

    @BeforeEach
    public void beforeEach() {
        final int startTime = 1700000000;
        world = World.create(new InMemoryStorageStrategy(), startTime);

        final Airports.Airport airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        final Airports.Airport airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");

        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().create("TTTT", "TTT");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-BCDE", airportA);

        final int departureTime = startTime + 2* Time.ONE_HOUR;
        final int arrivalTime = departureTime + 2*Time.ONE_HOUR;
        mission = world.flightMissions()
                .createDispatchedMission(
                        aircraft,
                        airportA,
                        airportB,
                        departureTime,
                        arrivalTime);
    }

    @Test
    public void test__npc_is_default() {
        assertFalse(mission.isModePc());
    }

    @Test
    public void test__pc_then_status() {
        mission.setModePc(true);
        mission.setStatus(FlightMissions.Status.Flying);

        assertTrue(mission.isModePc());
        assertEquals(FlightMissions.Status.Flying, mission.getStatus());
    }

    @Test
    public void test__npc_then_status() {
        mission.setModePc(false);
        mission.setStatus(FlightMissions.Status.Flying);

        assertFalse(mission.isModePc());
        assertEquals(FlightMissions.Status.Flying, mission.getStatus());
    }

    @Test
    public void test__status_then_pc() {
        mission.setStatus(FlightMissions.Status.Flying);
        mission.setModePc(true);

        assertTrue(mission.isModePc());
        assertEquals(FlightMissions.Status.Flying, mission.getStatus());
    }

    @Test
    public void test__status_then_npc() {
        mission.setStatus(FlightMissions.Status.Flying);
        mission.setModePc(false);

        assertFalse(mission.isModePc());
        assertEquals(FlightMissions.Status.Flying, mission.getStatus());
    }

    // todo ak2 when I will start pushing vatsim coords into the system, I will need the second mode available,
    //          now it is busy by time mode, so I will need to add tests for that mode wich replaces time mode
}
