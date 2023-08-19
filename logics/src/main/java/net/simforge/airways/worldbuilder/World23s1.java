/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder;

import net.simforge.airways.AirwaysApp;
import net.simforge.airways.ops.AircraftOps;
import net.simforge.airways.ops.CommonOps;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.worldbuilder.tools.*;
import org.hibernate.Session;

import java.io.IOException;

public class World23s1 {
    public static void main(String[] args) throws IOException {
        ImportCityPopulation.main(new String[]{"country-code:GB", "min-population:1000000"});
        ImportCityPopulation.main(new String[]{"country-code:US", "min-population:1000000"});
        ImportCityPopulation.main(new String[]{"country-code:FR", "min-population:1000000"});
        UpdateCityFlows.main(null);

        ImportFSEconomyAirports.main(args);
        ActivateAirports.main(args);
        UpdateAirport2CityLinks.main(args);

        AddAirlinesAndAircraftTypes.main(null);

        new AirwaysApp.StartupAction().run();
        try (Session session = AirwaysApp.getSessionFactory().openSession()) {

            PilotOps.addNPCPilots(session, "United Kingdom", "London", "EGLL", 10);
            PilotOps.addNPCPilots(session, "United Kingdom", "London", "EGSS", 10);

            AircraftOps.addAircrafts(session, "WW", "A320", "EGLL", "G-AA??", 10);
            AircraftOps.addAircrafts(session, "WW", "B744", "EGLL", "G-BN??", 5);
            AircraftOps.addAircrafts(session, "WW", "E170", "EGSS", "G-EM??", 10);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "A320",
                    CommonOps.airportByIcao(session, "EGLL"), CommonOps.airportByIcao(session, "LFPG"),
                    "05:00", 160, 300);
            BuildTimetable.addRoundtripTimetableRow(session, "WW", "A320",
                    CommonOps.airportByIcao(session, "EGLL"), CommonOps.airportByIcao(session, "EGPF"),
                    "06:00", 160, 300);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "B744",
                    CommonOps.airportByIcao(session, "EGLL"), CommonOps.airportByIcao(session, "KJFK"),
                    "04:00", 350, 300);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGSS"), CommonOps.airportByIcao(session, "LEMG"),
                    "08:00", 72, 240);

            BuildTimetable.addRoundtripTimetableRow(session, "WW", "E170",
                    CommonOps.airportByIcao(session, "EGSS"), CommonOps.airportByIcao(session, "LEPA"),
                    "12:00", 72, 240);

        }
        new AirwaysApp.ShutdownAction().run();
    }
}
