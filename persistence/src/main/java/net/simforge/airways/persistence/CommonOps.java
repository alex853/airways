package net.simforge.airways.persistence;

import net.simforge.airways.persistence.model.Airline;
import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.airways.persistence.model.geo.Country;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;

public class CommonOps {

    public static Country countryByName(Session session, String countryName) {
        BM.start("Airways.countryByName");
        try {

            //noinspection JpaQlInspection
            return (Country) session
                    .createQuery("from Country c where name = :name")
                    .setString("name", countryName)
                    .setMaxResults(1)
                    .uniqueResult();
        } finally {
            BM.stop();
        }
    }

    public static City cityByNameAndCountry(Session session, String cityName, Country country) {
        BM.start("Airways.cityByNameAndCountry");
        try {
            //noinspection JpaQlInspection
            return (City) session
                    .createQuery("from City c where name = :name and country = :country")
                    .setString("name", cityName)
                    .setEntity("country", country)
                    .setMaxResults(1)
                    .uniqueResult();
        } finally {
            BM.stop();
        }
    }

    public static Airport airportByIcao(Session session, String icao) {
        BM.start("Airways.airportByIcao");
        try {
            //noinspection JpaQlInspection
            return (Airport) session
                    .createQuery("from Airport c where icao = :icao")
                    .setString("icao", icao)
                    .setMaxResults(1)
                    .uniqueResult();
        } finally {
            BM.stop();
        }
    }


    public static Airline airlineByIata(Session session, String iata) {
        BM.start("Airways.airlineByIata");
        try {
            //noinspection JpaQlInspection
            return (Airline) session
                    .createQuery("from Airline c where iata = :iata")
                    .setString("iata", iata)
                    .setMaxResults(1)
                    .uniqueResult();
        } finally {
            BM.stop();
        }
    }

    public static AircraftType aircraftTypeByIcao(Session session, String icao) {
        BM.start("Airways.aircraftTypeByIcao");
        try {
            //noinspection JpaQlInspection
            return (AircraftType) session
                    .createQuery("from AircraftType c where icao = :icao")
                    .setString("icao", icao)
                    .setMaxResults(1)
                    .uniqueResult();
        } finally {
            BM.stop();
        }
    }

    public static String makeFlightNumber(String iataCode, int number) {
        return iataCode + String.valueOf(number);
    }

    public static String increaseFlightNumber(String number) {
        String iataCode = number.substring(0, 2);
        String digits = number.substring(2);
        int flightNumber = Integer.parseInt(digits);
        return makeFlightNumber(iataCode, flightNumber + 1);
    }

}
