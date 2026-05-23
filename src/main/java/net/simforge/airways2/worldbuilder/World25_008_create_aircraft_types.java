package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftTypes;

import java.io.IOException;

public class World25_008_create_aircraft_types {
    public static void main(String[] args) throws IOException {
        final World world = World25.load();

        final AircraftTypes aircraftTypes = world.aircraftTypes();

        aircraftTypes.byIcao("A306").orElseGet(() -> aircraftTypes.create("A306", "AB6"));

        aircraftTypes.byIcao("AT43").orElseGet(() -> aircraftTypes.create("AT43", "AT4"));
        aircraftTypes.byIcao("AT45").orElseGet(() -> aircraftTypes.create("AT45", "ATR"));
        aircraftTypes.byIcao("AT72").orElseGet(() -> aircraftTypes.create("AT72", "AT7"));
        aircraftTypes.byIcao("AT75").orElseGet(() -> aircraftTypes.create("AT75", "ATR"));

        aircraftTypes.byIcao("C152").orElseGet(() -> aircraftTypes.create("C152", null));
        aircraftTypes.byIcao("C172").orElseGet(() -> aircraftTypes.create("C172", null));
        aircraftTypes.byIcao("C182").orElseGet(() -> aircraftTypes.create("C182", null));

        aircraftTypes.byIcao("C25A").orElseGet(() -> aircraftTypes.create("C25A", "CNJ"));
        aircraftTypes.byIcao("C25B").orElseGet(() -> aircraftTypes.create("C25B", "CNJ"));
        aircraftTypes.byIcao("C25C").orElseGet(() -> aircraftTypes.create("C25C", "CNJ"));
        aircraftTypes.byIcao("C500").orElseGet(() -> aircraftTypes.create("C500", "CNJ"));
        aircraftTypes.byIcao("C510").orElseGet(() -> aircraftTypes.create("C510", "CNJ"));
        aircraftTypes.byIcao("C525").orElseGet(() -> aircraftTypes.create("C525", "CNJ"));
        aircraftTypes.byIcao("C550").orElseGet(() -> aircraftTypes.create("C550", "CNJ"));
        aircraftTypes.byIcao("C560").orElseGet(() -> aircraftTypes.create("C560", "CNJ"));
        aircraftTypes.byIcao("C56X").orElseGet(() -> aircraftTypes.create("C56X", "CNJ"));
        aircraftTypes.byIcao("C650").orElseGet(() -> aircraftTypes.create("C650", "CNJ"));
        aircraftTypes.byIcao("C680").orElseGet(() -> aircraftTypes.create("C680", "CNJ"));
        aircraftTypes.byIcao("C68A").orElseGet(() -> aircraftTypes.create("C68A", "CNJ"));
        aircraftTypes.byIcao("C700").orElseGet(() -> aircraftTypes.create("C700", "CNJ"));
        aircraftTypes.byIcao("C750").orElseGet(() -> aircraftTypes.create("C750", "CNJ"));

        aircraftTypes.byIcao("CL60").orElseGet(() -> aircraftTypes.create("CL60", "CCJ"));

        aircraftTypes.byIcao("CRJ1").orElseGet(() -> aircraftTypes.create("CRJ1", "CR1"));
        aircraftTypes.byIcao("CRJ2").orElseGet(() -> aircraftTypes.create("CRJ2", "CR2"));
        aircraftTypes.byIcao("CRJ7").orElseGet(() -> aircraftTypes.create("CRJ7", "CR7"));
        aircraftTypes.byIcao("CRJ9").orElseGet(() -> aircraftTypes.create("CRJ9", "CR9"));
        aircraftTypes.byIcao("CRJX").orElseGet(() -> aircraftTypes.create("CRJX", "CRK"));

        aircraftTypes.byIcao("DH8D").orElseGet(() -> aircraftTypes.create("DH8D", "DH4"));

        aircraftTypes.byIcao("E170").orElseGet(() -> aircraftTypes.create("E170", "E70"));
        aircraftTypes.byIcao("E75L").orElseGet(() -> aircraftTypes.create("E75L", "E7W"));
        aircraftTypes.byIcao("E75S").orElseGet(() -> aircraftTypes.create("E75S", "E75"));
        aircraftTypes.byIcao("E190").orElseGet(() -> aircraftTypes.create("E190", "E90"));
        aircraftTypes.byIcao("E195").orElseGet(() -> aircraftTypes.create("E195", "E95"));

        aircraftTypes.byIcao("E55P").orElseGet(() -> aircraftTypes.create("E55P", "EP3"));

        aircraftTypes.byIcao("GLF4").orElseGet(() -> aircraftTypes.create("GLF4", "GJ4"));
        aircraftTypes.byIcao("GLF5").orElseGet(() -> aircraftTypes.create("GLF5", "GJ5"));
        aircraftTypes.byIcao("GLF6").orElseGet(() -> aircraftTypes.create("GLF6", "GJ6"));
        aircraftTypes.byIcao("GA7C").orElseGet(() -> aircraftTypes.create("GA7C", "GL7"));
        aircraftTypes.byIcao("GA8C").orElseGet(() -> aircraftTypes.create("GA8C", "GL8"));

        aircraftTypes.byIcao("L101").orElseGet(() -> aircraftTypes.create("L101", "L10"));

        aircraftTypes.byIcao("MD11").orElseGet(() -> aircraftTypes.create("MD11", "M11"));

        aircraftTypes.byIcao("MD81").orElseGet(() -> aircraftTypes.create("MD81", "M81"));
        aircraftTypes.byIcao("MD82").orElseGet(() -> aircraftTypes.create("MD82", "M82"));
        aircraftTypes.byIcao("MD83").orElseGet(() -> aircraftTypes.create("MD83", "M83"));
        aircraftTypes.byIcao("MD87").orElseGet(() -> aircraftTypes.create("MD87", "M87"));
        aircraftTypes.byIcao("MD88").orElseGet(() -> aircraftTypes.create("MD88", "M88"));
        aircraftTypes.byIcao("MD90").orElseGet(() -> aircraftTypes.create("MD90", "M90"));

        aircraftTypes.byIcao("T134").orElseGet(() -> aircraftTypes.create("T134", "TU3"));
        aircraftTypes.byIcao("T154").orElseGet(() -> aircraftTypes.create("T154", "TU5"));
        aircraftTypes.byIcao("T204").orElseGet(() -> aircraftTypes.create("T204", "T20"));

        aircraftTypes.byIcao("SF50").orElseGet(() -> aircraftTypes.create("SF50", null));

        aircraftTypes.byIcao("SU95").orElseGet(() -> aircraftTypes.create("SU95", "SU9"));

        world.save();
    }
}
