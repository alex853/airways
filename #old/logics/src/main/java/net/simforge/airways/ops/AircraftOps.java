package net.simforge.airways.ops;

import net.simforge.airways.model.Airline;
import net.simforge.airways.model.aircraft.Aircraft;
import net.simforge.airways.model.aircraft.AircraftType;
import net.simforge.airways.model.geo.Airport;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AircraftOps {
    private static final Logger log = LoggerFactory.getLogger(AircraftOps.class);

    public static void addAircrafts(Session session, String airlineIata, String aircraftTypeIcao, String airportIcao, String regNoPattern, int count) {
        Airline airline = CommonOps.airlineByIata(session, airlineIata);
        AircraftType aircraftType = CommonOps.aircraftTypeByIcao(session, aircraftTypeIcao);
        Airport airport = GeoOps.loadAirportByIcao(session, airportIcao);

        int created = 0;
        int number = -1;
        while (created < count) {
            number++;

            String regNo = regNoPattern;
            int remainder = number;
            int index;
            while ((index = regNo.lastIndexOf('?')) != -1) {
                int letterCode = remainder % 26;
                remainder = remainder / 26;

                regNo = regNo.substring(0, index) + (char) ('A' + letterCode) + regNo.substring(index + 1);
            }

            log.info("Aircraft Reg No: " + regNo);

            Aircraft aircraft = loadByRegNo(session, regNo);

            if (aircraft != null) {
                log.info("Aircraft " + regNo + " exists");
                continue;
            }

            aircraft = new Aircraft();
            aircraft.setType(aircraftType);
            aircraft.setRegNo(regNo);
            aircraft.setAirline(airline);
            aircraft.setLocationAirport(airport);
            aircraft.setStatus(Aircraft.Status.Idle);

            HibernateUtils.saveAndCommit(session, aircraft);

            created++;
        }
    }

    public static Aircraft loadByRegNo(Session session, String regNo) {
        BM.start("AircraftOps.loadByRegNo");
        try {
            return (Aircraft) session
                    .createQuery("from Aircraft a where regNo = :regNo")
                    .setString("regNo", regNo)
                    .setMaxResults(1)
                    .uniqueResult();
        } finally {
            BM.stop();
        }
    }

    public static Aircraft createAircraft(Session session, Airline airline, AircraftType aircraftType, Airport locationAirport, String regNoPattern) {
        Aircraft aircraft;

        String regNo = getFirstRegNo(regNoPattern);
        while (true) {
            aircraft = loadByRegNo(session, regNo);
            if (aircraft == null) {
                break;
            }

            regNo = getNextRegNo(regNoPattern, regNo);
            if (regNo == null) {
                return null;
            }
        }

        aircraft = new Aircraft();
        aircraft.setType(aircraftType);
        aircraft.setRegNo(regNo);
        aircraft.setAirline(airline);
        aircraft.setLocationAirport(locationAirport);
        aircraft.setStatus(Aircraft.Status.Idle);

        session.save(aircraft);

        return aircraft;
    }

    private static String getFirstRegNo(String regNoPattern) {
        return regNoPattern.replace('?', 'A');
    }

    private static String getNextRegNo(String regNoPattern, String prevRegNo) {
        String regNo = regNoPattern;

        int nextRegNoCharAddition = 0;
        while (true) {
            int index = regNo.lastIndexOf('?');
            if (index == -1) {
                if (nextRegNoCharAddition != 0) {
                    return null;
                } else {
                    return regNo;
                }
            }

            int prevRegNoChar = prevRegNo.charAt(index) - 'A';
            int regNoChar = prevRegNoChar + 1 + nextRegNoCharAddition;

            nextRegNoCharAddition = regNoChar / 26;
            regNoChar = regNoChar % 26;

            regNo = regNo.substring(0, index) + (char) ('A' + regNoChar) + regNo.substring(index + 1);
        }
    }

    public static Aircraft findAvailableAircraftAtAirport(Session session, Airline airline, AircraftType aircraftType, Airport locationAirport) {
        return  (Aircraft) session
                .createQuery("from Aircraft a" +
                        " where a.type = :type" +
                        " and a.airline = :airline" +
                        " and a.locationAirport = :locationAirport" +
                        " and a.status = :idle")
                .setParameter("type", aircraftType)
                .setParameter("airline", airline)
                .setParameter("locationAirport", locationAirport)
                .setInteger("idle", Aircraft.Status.Idle)
                .setMaxResults(1)
                .uniqueResult();
    }
}
