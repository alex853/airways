package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftTypes;

import java.io.IOException;

public class World25_008_create_aircraft_types {
    public static void main(String[] args) throws IOException {
        final World world = World25.load();

        final AircraftTypes aircraftTypes = world.aircraftTypes();

        aircraftTypes.byIcao("A306").orElseGet(() -> aircraftTypes.create("A306", "AB6"));

        aircraftTypes.byIcao("A310").orElseGet(() -> aircraftTypes.create("A310", "312"));

        aircraftTypes.byIcao("A318").orElseGet(() -> aircraftTypes.create("A318", "318"));
        aircraftTypes.byIcao("A319").orElseGet(() -> aircraftTypes.create("A319", "319"));
        aircraftTypes.byIcao("A320").orElseGet(() -> aircraftTypes.create("A320", "320"));
        aircraftTypes.byIcao("A321").orElseGet(() -> aircraftTypes.create("A321", "321"));

        aircraftTypes.byIcao("A19N").orElseGet(() -> aircraftTypes.create("A19N", "31N"));
        aircraftTypes.byIcao("A20N").orElseGet(() -> aircraftTypes.create("A20N", "32N"));
        aircraftTypes.byIcao("A21N").orElseGet(() -> aircraftTypes.create("A21N", "32Q"));

        aircraftTypes.byIcao("A332").orElseGet(() -> aircraftTypes.create("A332", "332"));
        aircraftTypes.byIcao("A333").orElseGet(() -> aircraftTypes.create("A333", "333"));
        aircraftTypes.byIcao("A338").orElseGet(() -> aircraftTypes.create("A338", "338"));
        aircraftTypes.byIcao("A339").orElseGet(() -> aircraftTypes.create("A339", "339"));

        aircraftTypes.byIcao("A342").orElseGet(() -> aircraftTypes.create("A342", "342"));
        aircraftTypes.byIcao("A343").orElseGet(() -> aircraftTypes.create("A343", "343"));
        aircraftTypes.byIcao("A345").orElseGet(() -> aircraftTypes.create("A345", "345"));
        aircraftTypes.byIcao("A346").orElseGet(() -> aircraftTypes.create("A346", "346"));

        aircraftTypes.byIcao("A359").orElseGet(() -> aircraftTypes.create("A359", "359"));
        aircraftTypes.byIcao("A35K").orElseGet(() -> aircraftTypes.create("A35K", "351"));

        aircraftTypes.byIcao("A388").orElseGet(() -> aircraftTypes.create("A388", "388"));

        aircraftTypes.byIcao("AT43").orElseGet(() -> aircraftTypes.create("AT43", "AT4"));
        aircraftTypes.byIcao("AT45").orElseGet(() -> aircraftTypes.create("AT45", "ATR"));
        aircraftTypes.byIcao("AT72").orElseGet(() -> aircraftTypes.create("AT72", "AT7"));
        aircraftTypes.byIcao("AT75").orElseGet(() -> aircraftTypes.create("AT75", "ATR"));
        aircraftTypes.byIcao("AT76").orElseGet(() -> aircraftTypes.create("AT76", "ATR"));

        aircraftTypes.byIcao("B731").orElseGet(() -> aircraftTypes.create("B731", "731"));
        aircraftTypes.byIcao("B732").orElseGet(() -> aircraftTypes.create("B732", "732"));

        aircraftTypes.byIcao("B733").orElseGet(() -> aircraftTypes.create("B733", "733"));
        aircraftTypes.byIcao("B734").orElseGet(() -> aircraftTypes.create("B734", "734"));
        aircraftTypes.byIcao("B735").orElseGet(() -> aircraftTypes.create("B735", "735"));

        aircraftTypes.byIcao("B736").orElseGet(() -> aircraftTypes.create("B736", "736"));
        aircraftTypes.byIcao("B737").orElseGet(() -> aircraftTypes.create("B737", "73G"));
        aircraftTypes.byIcao("B738").orElseGet(() -> aircraftTypes.create("B738", "738"));
        aircraftTypes.byIcao("B739").orElseGet(() -> aircraftTypes.create("B739", "739"));

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

        aircraftTypes.byIcao("B772").orElseGet(() -> aircraftTypes.create("B772", "772"));
        aircraftTypes.byIcao("B773").orElseGet(() -> aircraftTypes.create("B773", "773"));
        aircraftTypes.byIcao("B77L").orElseGet(() -> aircraftTypes.create("B77L", "77L"));
        aircraftTypes.byIcao("B77W").orElseGet(() -> aircraftTypes.create("B77W", "77W"));

        aircraftTypes.byIcao("B788").orElseGet(() -> aircraftTypes.create("B788", "788"));
        aircraftTypes.byIcao("B789").orElseGet(() -> aircraftTypes.create("B789", "789"));
        aircraftTypes.byIcao("B78X").orElseGet(() -> aircraftTypes.create("B78X", "781"));

        aircraftTypes.byIcao("C152").orElseGet(() -> aircraftTypes.create("C152", null));
        aircraftTypes.byIcao("C172").orElseGet(() -> aircraftTypes.create("C172", null));
        aircraftTypes.byIcao("C182").orElseGet(() -> aircraftTypes.create("C182", null));
        aircraftTypes.byIcao("C208").orElseGet(() -> aircraftTypes.create("C208", null));

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

        aircraftTypes.byIcao("CONC").orElseGet(() -> aircraftTypes.create("CONC", null));

        aircraftTypes.byIcao("CRJ1").orElseGet(() -> aircraftTypes.create("CRJ1", "CR1"));
        aircraftTypes.byIcao("CRJ2").orElseGet(() -> aircraftTypes.create("CRJ2", "CR2"));
        aircraftTypes.byIcao("CRJ7").orElseGet(() -> aircraftTypes.create("CRJ7", "CR7"));
        aircraftTypes.byIcao("CRJ9").orElseGet(() -> aircraftTypes.create("CRJ9", "CR9"));
        aircraftTypes.byIcao("CRJX").orElseGet(() -> aircraftTypes.create("CRJX", "CRK"));

        aircraftTypes.byIcao("DA40").orElseGet(() -> aircraftTypes.create("DA40", null));
        aircraftTypes.byIcao("DA42").orElseGet(() -> aircraftTypes.create("DA42", null));
        aircraftTypes.byIcao("DA62").orElseGet(() -> aircraftTypes.create("DA62", null));

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
