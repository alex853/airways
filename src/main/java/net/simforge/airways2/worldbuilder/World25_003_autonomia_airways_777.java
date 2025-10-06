package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;

import java.io.IOException;

public class World25_003_autonomia_airways_777 {
    public static void main(String[] args) throws IOException {
        final World world = World25.load();

        final Airports.Airport lfpg = world.airports().byIcao("LFPG").orElseThrow();

        final AircraftTypes aircraftTypes = world.aircraftTypes();
        final AircraftTypes.AircraftType type = aircraftTypes.byIcao("B773").orElseThrow();

        final AircraftOperators aircraftOperators = world.aircraftOperators();
        final AircraftOperators.AircraftOperator airline = aircraftOperators.byIata("AW")
                .orElseGet(() -> aircraftOperators.create("AW", "AUW", "Autonomia Airways"));

        createAircraft(world, airline, type, "F-AUWV", lfpg);
        createAircraft(world, airline, type, "F-AUWW", lfpg);
        createAircraft(world, airline, type, "F-AUWX", lfpg);
        createAircraft(world, airline, type, "F-AUWY", lfpg);
        createAircraft(world, airline, type, "F-AUWZ", lfpg);

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
