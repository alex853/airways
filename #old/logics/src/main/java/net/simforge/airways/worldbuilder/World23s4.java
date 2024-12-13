package net.simforge.airways.worldbuilder;

import net.simforge.airways.AirwaysApp;
import net.simforge.airways.ops.AircraftOps;
import net.simforge.airways.ops.CommonOps;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.worldbuilder.tools.*;
import org.hibernate.Session;

import java.io.IOException;

public class World23s4 {
    public static void main(String[] args) throws IOException {
        ImportCityPopulation.main(new String[]{"country-code:DE", "min-population:1000000"});
        ImportCityPopulation.main(new String[]{"country-code:CZ", "min-population:1000000"});
        ImportCityPopulation.main(new String[]{"country-code:AT", "min-population:1000000"});
        ImportCityPopulation.main(new String[]{"country-code:IT", "min-population:1000000"});
        ImportCityPopulation.main(new String[]{"country-code:ES", "min-population:1000000"});
        ImportCityPopulation.main(new String[]{"country-code:PT", "min-population:1000000"});
        ImportCityPopulation.main(new String[]{"country-code:IE", "min-population:1000000"});

        UpdateCityFlows.main(null);

        UpdateAirport2CityLinks.main(args);

        new AirwaysApp.StartupAction().run();
        try (Session session = AirwaysApp.getSessionFactory().openSession()) {

            PilotOps.addNPCPilots(session, "United Kingdom", "London", "EGLC", 10);

            AircraftOps.addAircrafts(session, "WW", "E170", "EGLC", "G-EM??", 10);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGLC"), CommonOps.airportByIcao(session, "EDDB"),
                    "09:30", 72, 180);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGLC"), CommonOps.airportByIcao(session, "EDDH"),
                    "10:15", 72, 240);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGLC"), CommonOps.airportByIcao(session, "EDDM"),
                    "11:10", 72, 240);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGLC"), CommonOps.airportByIcao(session, "LKPR"),
                    "12:50", 72, 240);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGLC"), CommonOps.airportByIcao(session, "LOWW"),
                    "14:05", 72, 240);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGLC"), CommonOps.airportByIcao(session, "LIRF"),
                    "15:20", 72, 240);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGLC"), CommonOps.airportByIcao(session, "LIML"),
                    "16:00", 72, 240);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGLC"), CommonOps.airportByIcao(session, "LEMD"),
                    "16:20", 72, 240);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGLC"), CommonOps.airportByIcao(session, "LEBL"),
                    "17:30", 72, 240);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGLC"), CommonOps.airportByIcao(session, "EIDW"),
                    "18:15", 72, 240);

        }
        new AirwaysApp.ShutdownAction().run();
    }
}
