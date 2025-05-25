package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;

import java.io.IOException;

public class World25_003_autonomia_airways {
    public static void main(String[] args) throws IOException {
        final World world = World25.load();

        final Airports.Airport lfpg = world.airports().byIcao("LFPG").orElseThrow();

        final AircraftTypes aircraftTypes = world.aircraftTypes();
        final AircraftTypes.AircraftType type = aircraftTypes.byIcao("A320").orElseThrow();

        final AircraftOperators aircraftOperators = world.aircraftOperators();
        final AircraftOperators.AircraftOperator airline = aircraftOperators.byIata("AW")
                .orElseGet(() -> aircraftOperators.create("AW", "AUW", "Autonomia Airways"));

        createAircraft(world, airline, type, "F-AUWA", lfpg);
        createAircraft(world, airline, type, "F-AUWB", lfpg);
        createAircraft(world, airline, type, "F-AUWC", lfpg);
        createAircraft(world, airline, type, "F-AUWD", lfpg);
        createAircraft(world, airline, type, "F-AUWE", lfpg);

        world.save();
    }

    public static void createAircraft(final World world,
                                      final AircraftOperators.AircraftOperator airline,
                                      final AircraftTypes.AircraftType type,
                                      final String regNo,
                                      final Airports.Airport location) {
        final Aircrafts aircrafts = world.aircrafts();
        aircrafts.byRegNo(regNo).orElseGet(() ->
                        aircrafts.create(type, regNo, location))
                .setAircraftOperatorId(airline.getId());
    }
}
