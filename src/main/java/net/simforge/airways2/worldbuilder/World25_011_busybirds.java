package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;

import java.io.IOException;

public class World25_011_busybirds {
    public static void main(String[] args) throws IOException {
        final World world = World25.load();

        final AircraftOperators aircraftOperators = world.aircraftOperators();
        final AircraftOperators.AircraftOperator busybirds = aircraftOperators.byIata(World25.BusyBirdsIata)
                .orElseGet(() -> aircraftOperators.create(
                        World25.BusyBirdsIata,
                        World25.BusyBirdsIcao,
                        "busyBirds"));

        final Airports.Airport eglf = world.airports().byIcao("EGLF").orElseThrow();

        final AircraftTypes aircraftTypes = world.aircraftTypes();
        final AircraftTypes.AircraftType type = aircraftTypes.byIcao("C25C").orElseThrow();

        createAircraft(world, busybirds, type, "BB-CJA", eglf);
        createAircraft(world, busybirds, type, "BB-CJB", eglf);
        createAircraft(world, busybirds, type, "BB-CJC", eglf);

        if (!world.airportFacilities().hasFacility(eglf, busybirds, AirportFacilities.Type.BaseAirport)) {
            world.airportFacilities().create(eglf, busybirds, AirportFacilities.Type.BaseAirport);
        }

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
