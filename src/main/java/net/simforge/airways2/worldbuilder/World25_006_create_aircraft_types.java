package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftTypes;

import java.io.IOException;

public class World25_006_create_aircraft_types {
    public static void main(String[] args) throws IOException {
        final World world = World25.load();

        final AircraftTypes aircraftTypes = world.aircraftTypes();

        aircraftTypes.byIcao("A310").orElseGet(() -> aircraftTypes.create("A310", "312"));

        aircraftTypes.byIcao("A332").orElseGet(() -> aircraftTypes.create("A332", "332"));
        aircraftTypes.byIcao("A333").orElseGet(() -> aircraftTypes.create("A333", "333"));
        aircraftTypes.byIcao("A338").orElseGet(() -> aircraftTypes.create("A338", "338"));
        aircraftTypes.byIcao("A339").orElseGet(() -> aircraftTypes.create("A339", "339"));

        aircraftTypes.byIcao("A342").orElseGet(() -> aircraftTypes.create("A342", "342"));
        aircraftTypes.byIcao("A343").orElseGet(() -> aircraftTypes.create("A343", "343"));
        aircraftTypes.byIcao("A345").orElseGet(() -> aircraftTypes.create("A345", "345"));
        aircraftTypes.byIcao("A346").orElseGet(() -> aircraftTypes.create("A346", "346"));

        aircraftTypes.byIcao("A388").orElseGet(() -> aircraftTypes.create("A388", "388"));

        aircraftTypes.byIcao("B736").orElseGet(() -> aircraftTypes.create("B736", "736"));
        aircraftTypes.byIcao("B737").orElseGet(() -> aircraftTypes.create("B737", "73G"));

        aircraftTypes.byIcao("B37M").orElseGet(() -> aircraftTypes.create("B37M", "7M7"));
        aircraftTypes.byIcao("B38M").orElseGet(() -> aircraftTypes.create("B38M", "7M8"));
        aircraftTypes.byIcao("B39M").orElseGet(() -> aircraftTypes.create("B39M", "7M9"));
        aircraftTypes.byIcao("B3XM").orElseGet(() -> aircraftTypes.create("B3XM", "7MJ"));

        aircraftTypes.byIcao("B741").orElseGet(() -> aircraftTypes.create("B741", "741"));
        aircraftTypes.byIcao("B742").orElseGet(() -> aircraftTypes.create("B742", "742"));
        aircraftTypes.byIcao("B743").orElseGet(() -> aircraftTypes.create("B743", "743"));
        aircraftTypes.byIcao("B744").orElseGet(() -> aircraftTypes.create("B744", "744"));
        aircraftTypes.byIcao("B748").orElseGet(() -> aircraftTypes.create("B748", "74H"));

        aircraftTypes.byIcao("B752").orElseGet(() -> aircraftTypes.create("B752", "752"));
        aircraftTypes.byIcao("B753").orElseGet(() -> aircraftTypes.create("B753", "753"));

        aircraftTypes.byIcao("B762").orElseGet(() -> aircraftTypes.create("B762", "762"));
        aircraftTypes.byIcao("B763").orElseGet(() -> aircraftTypes.create("B763", "763"));
        aircraftTypes.byIcao("B764").orElseGet(() -> aircraftTypes.create("B764", "764"));

        world.save();
    }
}
