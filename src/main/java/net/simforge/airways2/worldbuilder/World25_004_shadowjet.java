package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;

import java.io.IOException;

public class World25_004_shadowjet {
    public static void main(String[] args) throws IOException {
        final World world = World25.load();

        final AircraftOperators aircraftOperators = world.aircraftOperators();
        aircraftOperators.byIata(World25.ShadowJetIata)
                .orElseGet(() -> aircraftOperators.create(
                        World25.ShadowJetIata,
                        World25.ShadowJetIcao,
                        "ShadowJet"));

        world.save();
    }
}
