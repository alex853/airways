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
        final AircraftTypes.AircraftType c25c = aircraftTypes.byIcao("C25C").orElseThrow();

        createAircraft(world, busybirds, c25c, "BB-CJA", eglf);
        createAircraft(world, busybirds, c25c, "BB-CJB", eglf);
        createAircraft(world, busybirds, c25c, "BB-CJC", eglf);

        world.airportFacilities().createIfAbsent(eglf, busybirds, AirportFacilities.Type.BaseAirport);

        final AircraftTypes.AircraftType ga7c = aircraftTypes.byIcao("GA7C")
                .orElseGet(() -> aircraftTypes.create("GA7C", "GL7"));

        createAircraft(world, busybirds, ga7c, "BB-GLA", eglf);
        createAircraft(world, busybirds, ga7c, "BB-GLB", eglf);

        final AircraftTypes.AircraftType glf6 = aircraftTypes.byIcao("GLF6")
                .orElseGet(() -> aircraftTypes.create("GLF6", "GJ6"));

        createAircraft(world, busybirds, glf6, "BB-GLC", eglf);
        createAircraft(world, busybirds, glf6, "BB-GLD", eglf);

        world.airportFacilities().createIfAbsent(eglf, busybirds, AirportFacilities.Type.BaseAirport);

        world.airportFacilities().createIfAbsent(world.airports().byIcao("LFPB").get(), AirportFacilities.Type.BusinessAviationTerminal);
        world.airportFacilities().createIfAbsent(world.airports().byIcao("EDDM").get(), AirportFacilities.Type.BusinessAviationTerminal);

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
