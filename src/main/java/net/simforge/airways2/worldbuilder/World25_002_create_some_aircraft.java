package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.storage.AircraftTypes;
import net.simforge.airways2.world.storage.Aircrafts;
import net.simforge.airways2.world.storage.Airports;

import java.io.IOException;

public class World25_002_create_some_aircraft {
    public static void main(String[] args) throws IOException {
        final World world = World.loadOrCreate(World25.name);

        final Airports.Airport lfpg = world.airports().byIcao("LFPG").orElseThrow();

        final AircraftTypes aircraftTypes = world.aircraftTypes();
        final AircraftTypes.AircraftType type = aircraftTypes.byIcao("A320")
                .orElseGet(() -> aircraftTypes.create("A320", "320"));

        createAircraft(world, type, "F-NTAA", lfpg);
        createAircraft(world, type, "F-NTAB", lfpg);
        createAircraft(world, type, "F-NTAC", lfpg);
        createAircraft(world, type, "F-NTAD", lfpg);
        createAircraft(world, type, "F-NTAE", lfpg);
        createAircraft(world, type, "F-NTAF", lfpg);
        createAircraft(world, type, "F-NTAG", lfpg);
        createAircraft(world, type, "F-NTAH", lfpg);
        createAircraft(world, type, "F-NTAI", lfpg);
        createAircraft(world, type, "F-NTAJ", lfpg);

        world.save();
    }

    public static void createAircraft(final World world,
                                      final AircraftTypes.AircraftType type,
                                      final String regNo,
                                      final Airports.Airport location) {
        final Aircrafts aircrafts = world.aircrafts();
        aircrafts.byRegNo(regNo).orElseGet(() -> aircrafts.create(type, regNo, location));
    }
}
