package net.simforge.airways.ops;

import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;

public class GeoOps {
    public static City loadBiggestCityLinkedToAirport(Session session, Airport airport) {
        BM.start("GeoOps.loadBiggestCityLinkedToAirport");
        try {
            return (City) session
                    .createQuery("select a2c.city " +
                            "from Airport2City a2c " +
                            "where airport = :locationAirport " +
                            "order by city.population desc")
                    .setParameter("airport", airport)
                    .setMaxResults(1)
                    .uniqueResult();
        } finally {
            BM.stop();
        }
    }
}
