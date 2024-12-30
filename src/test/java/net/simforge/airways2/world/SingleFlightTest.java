package net.simforge.airways2.world;

import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.junit.jupiter.api.Test;

public class SingleFlightTest {
    @Test
    public void test() {
        final int startTime = 000000000000000000;
        final World world = World.create(new InMemoryStorageStrategy(), startTime);

        final Airports.Airport airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        final Airports.Airport airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");

        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().create("TTT", "TTTT");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-BCDE", airportA);

        final FlightMissions.Mission mission = world.flightMissions().createPlannedMission(aircraft, airportA, airportB, departureTime, arrivalTime);

        // plan mission for that aircraft and between those 2 airports
        // start world runner for some time
        // check statuses and locations
        // etc
    }
}
