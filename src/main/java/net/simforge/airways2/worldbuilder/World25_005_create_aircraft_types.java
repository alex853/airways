package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftTypes;

import java.io.IOException;

public class World25_005_create_aircraft_types {
    public static void main(String[] args) throws IOException {
        final World world = World25.load();

        final AircraftTypes aircraftTypes = world.aircraftTypes();

        aircraftTypes.byIcao("A318").orElseGet(() -> aircraftTypes.create("A318", "318"));
        aircraftTypes.byIcao("A319").orElseGet(() -> aircraftTypes.create("A319", "319"));
        aircraftTypes.byIcao("A320").orElseGet(() -> aircraftTypes.create("A320", "320"));
        aircraftTypes.byIcao("A321").orElseGet(() -> aircraftTypes.create("A321", "321"));

        aircraftTypes.byIcao("A19N").orElseGet(() -> aircraftTypes.create("A19N", "31N"));
        aircraftTypes.byIcao("A20N").orElseGet(() -> aircraftTypes.create("A20N", "32N"));
        aircraftTypes.byIcao("A21N").orElseGet(() -> aircraftTypes.create("A21N", "32Q"));

        aircraftTypes.byIcao("A359").orElseGet(() -> aircraftTypes.create("A359", "359"));
        aircraftTypes.byIcao("A35K").orElseGet(() -> aircraftTypes.create("A35K", "351"));

        aircraftTypes.byIcao("B738").orElseGet(() -> aircraftTypes.create("B738", "738"));
        aircraftTypes.byIcao("B739").orElseGet(() -> aircraftTypes.create("B739", "739"));

        aircraftTypes.byIcao("B772").orElseGet(() -> aircraftTypes.create("B772", "772"));
        aircraftTypes.byIcao("B773").orElseGet(() -> aircraftTypes.create("B773", "773"));
        aircraftTypes.byIcao("B77L").orElseGet(() -> aircraftTypes.create("B77L", "77L"));
        aircraftTypes.byIcao("B77W").orElseGet(() -> aircraftTypes.create("B77W", "77W"));

        aircraftTypes.byIcao("B788").orElseGet(() -> aircraftTypes.create("B788", "788"));
        aircraftTypes.byIcao("B789").orElseGet(() -> aircraftTypes.create("B789", "789"));
        aircraftTypes.byIcao("B78X").orElseGet(() -> aircraftTypes.create("B78X", "781"));

        world.save();
    }
}
