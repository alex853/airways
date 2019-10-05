/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder;

import net.simforge.airways.ops.CommonOps;
import net.simforge.airways.Airways;
import net.simforge.airways.persistence.model.Airline;
import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddAirlinesAndAircraftTypes {
    private static final Logger logger = LoggerFactory.getLogger(AddAirlinesAndAircraftTypes.class.getName());

    public static void main(String[] args) {
        logger.info("Add misc reference data");

        try (SessionFactory sessionFactory = Airways.buildSessionFactory();
             Session session = sessionFactory.openSession()) {

            HibernateUtils.transaction(session, () -> {
                addAirline(session, "ZZ", "ZZA", "ZZ Airways");
                addAirline(session, "WW", "WWA", "Worldwide Airways");
                addAirline(session, "PH", "PHA", "PhantomAir");

                addAircraftType(session, "A320", "320", 36000, 444, 160, 150, 2000, 1200);
                addAircraftType(session, "B744", "744", 36000, 480, 160, 150, 1800, 1000);
            });
        }
    }

    private static void addAirline(Session session, String iata, String icao, String name) {
        if (CommonOps.airlineByIata(session, iata) != null) {
            return;
        }

        Airline airline = new Airline();
        airline.setIata(iata);
        airline.setIcao(icao);
        airline.setName(name);

        session.save(airline);
    }

    private static void addAircraftType(Session session,
                                        String icaoCode, String iataCode,
                                        int typicalCruiseAltitude, int typicalCruiseSpeed,
                                        int takeoffSpeed, int landingSpeed,
                                        int climbVerticalSpeed, int descentVerticalSpeed) {
        if (CommonOps.aircraftTypeByIcao(session, icaoCode) != null) {
            return;
        }

        AircraftType type = new AircraftType();
        type.setIcao(icaoCode);
        type.setIata(iataCode);
        type.setTypicalCruiseAltitude(typicalCruiseAltitude);
        type.setTypicalCruiseSpeed(typicalCruiseSpeed);
        type.setTakeoffSpeed(takeoffSpeed);
        type.setLandingSpeed(landingSpeed);
        type.setClimbVerticalSpeed(climbVerticalSpeed);
        type.setDescentVerticalSpeed(descentVerticalSpeed);

        session.save(type);
    }
}
