package net.simforge.airways.ops;

import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;

import java.util.List;

public class GeoOps {
    public static City loadBiggestCityLinkedToAirport(Session session, Airport airport) {
        BM.start("GeoOps.loadBiggestCityLinkedToAirport");
        try {
            return (City) session
                    .createQuery("select a2c.city " +
                            "from Airport2City a2c " +
                            "where a2c.airport = :airport " +
                            "order by a2c.city.population desc")
                    .setParameter("airport", airport)
                    .setMaxResults(1)
                    .uniqueResult();
        } finally {
            BM.stop();
        }
    }

    public static List<City> loadCitiesLinkedToAirport(Session session, int airportId) {
        BM.start("GeoOps.loadCitiesLinkedToAirport");
        try {
            //noinspection unchecked
            return session
                    .createQuery("select a2c.city " +
                            "from Airport2City a2c " +
                            "where a2c.airport.id = :airportId " +
                            "order by a2c.city.population desc")
                    .setParameter("airportId", airportId)
                    .list();
        } finally {
            BM.stop();
        }
    }

    public static List<Airport> loadAirportsLinkedToCity(Session session, int cityId) {
        BM.start("GeoOps.loadAirportsLinkedToCity");
        try {
            //noinspection unchecked
            return session
                    .createQuery("select a2c.airport " +
                            "from Airport2City a2c " +
                            "where a2c.city.id = :cityId " +
                            "order by a2c.airport.icao")
                    .setParameter("cityId", cityId)
                    .list();
        } finally {
            BM.stop();
        }
    }

    public static int calcTransferDurationMinutes(double distance) {
        return (int) ((distance / 25) * 60 + 15);
    }
}
