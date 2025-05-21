package net.simforge.airways2.world;

import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.junit.jupiter.api.Test;

public class SingleFlightTest {
    @Test
    public void test() {
        final int startTime = 1700000000;
        final World world = World.create(new InMemoryStorageStrategy(), startTime);

        final Airports.Airport airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        final Airports.Airport airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");

        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().create("TTTT", "TTT");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-BCDE", airportA);

        final int departureTime = startTime + 2*Time.ONE_HOUR;
        final int arrivalTime = departureTime + 2*Time.ONE_HOUR;
        final FlightMissions.Mission mission = world.flightMissions()
                .createPlannedMission(aircraft, airportA, airportB, departureTime, arrivalTime);

        final int finishTime = startTime + 6*Time.ONE_HOUR;
        while (world.getWorldTime() < finishTime) {
            world.process(world.getWorldTime() + 10);
        }

        // todo ak1 check statuses and locations
        System.out.println();
    }
}
