package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AircraftsTest {

    private World world;
    private FlightMissions.Mission mission;
    private Aircrafts.Aircraft aircraft;

    @BeforeEach
    public void beforeEach() {
        int startTime = Time.START_TIME_EPOCH_SECONDS; // 2025-01-01 00:00
        world = World.create(new InMemoryStorageStrategy(), startTime);

        Airports.Airport airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");

        AircraftTypes.AircraftType aircraftType = world.aircraftTypes().create("TTTT", "TTT");
        aircraft = world.aircrafts().create(aircraftType, "A-BCDE", airportA);
    }

    @Test
    public void test__heading__check_default() {
        assertEquals(0, aircraft.getLocationHeading());
    }

    @Test
    public void test__heading__check_all_values() {
        for (int hdg = 0; hdg < 360; hdg++) {
            aircraft.setLocationHeading(hdg);

            assertTrue(Math.abs(hdg - aircraft.getLocationHeading()) <= 1);
        }
    }

    @Test
    public void test__heading__check_360() {
        aircraft.setLocationHeading(360);

        assertEquals(0, aircraft.getLocationHeading());
    }

    @Test
    public void test__heading__check_400() {
        aircraft.setLocationHeading(400);

        assertEquals(39, aircraft.getLocationHeading());
    }

    @Test
    public void test__heading__check_minus_90() {
        aircraft.setLocationHeading(-90);

        assertEquals(270, aircraft.getLocationHeading());
    }
}
