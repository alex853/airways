/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ops;

import net.simforge.airways.persistence.model.Airline;
import net.simforge.airways.persistence.model.aircraft.Aircraft;
import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AircraftOps {
    private static Logger logger = LoggerFactory.getLogger(AircraftOps.class);

    public static void addAircrafts(Session session, String airlineIata, String aircraftTypeIcao, String airportIcao, String regNoPattern, int count) {
        Airline airline = CommonOps.airlineByIata(session, airlineIata);
        AircraftType aircraftType = CommonOps.aircraftTypeByIcao(session, aircraftTypeIcao);
        Airport airport = CommonOps.airportByIcao(session, airportIcao);

        for (int number = 0; number < count; number++) {
            String regNo = regNoPattern;
            int remainder = number;
            int index;
            while ((index = regNo.lastIndexOf('?')) != -1) {
                int letterCode = remainder % 26;
                remainder = remainder / 26;

                regNo = regNo.substring(0, index) + (char) ('A' + letterCode) + regNo.substring(index + 1);
            }

            logger.info("Aircraft Reg No: " + regNo);

            //noinspection JpaQlInspection
            Aircraft aircraft = (Aircraft) session
                    .createQuery("from Aircraft a where regNo = :regNo")
                    .setString("regNo", regNo)
                    .setMaxResults(1)
                    .uniqueResult();

            if (aircraft != null) {
                logger.info("Aircraft " + regNo + " exists");
                continue;
            }

            aircraft = new Aircraft();
            aircraft.setType(aircraftType);
            aircraft.setRegNo(regNo);
            aircraft.setAirline(airline);
            aircraft.setPositionAirport(airport);
            aircraft.setStatus(Aircraft.Status.Idle);

            HibernateUtils.saveAndCommit(session, aircraft);
        }
    }
}
