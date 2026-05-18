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
        final int startTime = Time.START_TIME_EPOCH_SECONDS; // 2025-01-01 00:00
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
        assertEquals(FlightMissions.CharacterMode.NPC, mission.getCharacterMode());
    }

    @Test
    public void test__pc_then_status() {
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        mission.setStatus(FlightMissions.Status.Flying);

        assertEquals(FlightMissions.CharacterMode.PC, mission.getCharacterMode());
        assertEquals(FlightMissions.Status.Flying, mission.getStatus());
    }

    @Test
    public void test__npc_then_status() {
        mission.setCharacterMode(FlightMissions.CharacterMode.NPC);
        mission.setStatus(FlightMissions.Status.Flying);

        assertEquals(FlightMissions.CharacterMode.NPC, mission.getCharacterMode());
        assertEquals(FlightMissions.Status.Flying, mission.getStatus());
    }

    @Test
    public void test__status_then_pc() {
        mission.setStatus(FlightMissions.Status.Flying);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);

        assertEquals(FlightMissions.CharacterMode.PC, mission.getCharacterMode());
        assertEquals(FlightMissions.Status.Flying, mission.getStatus());
    }

    @Test
    public void test__status_then_npc() {
        mission.setStatus(FlightMissions.Status.Flying);
        mission.setCharacterMode(FlightMissions.CharacterMode.NPC);

        assertEquals(FlightMissions.CharacterMode.NPC, mission.getCharacterMode());
        assertEquals(FlightMissions.Status.Flying, mission.getStatus());
    }

    @Test
    public void test__automatic_coords_is_default() {
        assertEquals(FlightMissions.CoordinatesSource.AutomaticSimpleFlight, mission.getCoordinatesSource());
    }
}
