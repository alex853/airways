package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;

import java.io.IOException;

public class World25_011_busybirds {
    public static void main(String[] args) throws IOException {
        final World world = World25.load();

        final AircraftOperators aircraftOperators = world.aircraftOperators();
        final AircraftOperators.AircraftOperator airline = aircraftOperators.byIata(World25.BusyBirdsIata)
                .orElseGet(() -> aircraftOperators.create(
                        World25.BusyBirdsIata,
                        World25.BusyBirdsIcao,
                        "busyBirds"));

        final Airports.Airport eglf = world.airports().byIcao("EGLF").orElseThrow();

        final AircraftTypes aircraftTypes = world.aircraftTypes();
        final AircraftTypes.AircraftType type = aircraftTypes.byIcao("C25C").orElseThrow();

        createAircraft(world, airline, type, "BB-CJA", eglf);
        createAircraft(world, airline, type, "BB-CJB", eglf);
        createAircraft(world, airline, type, "BB-CJC", eglf);

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
