/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder;

import net.simforge.airways.Airways;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.persistence.model.geo.Airport2City;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.misc.Geo;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class UpdateAirport2CityLinks {
    private static final Logger logger = LoggerFactory.getLogger(UpdateAirport2CityLinks.class.getName());

    public static void main(String[] args) {
        logger.info("Update Airport2City links");

        try (SessionFactory sessionFactory = Airways.buildSessionFactory();
             Session session = sessionFactory.openSession()) {

            //noinspection unchecked
            List<Airport> airports = session
                    .createQuery("from Airport where dataset = :activeDataset")
                    .setInteger("activeDataset", Airways.ACTIVE_DATASET)
                    .list();
            logger.info("loaded {} airports", airports.size());

            //noinspection unchecked
            List<City> cities = session
                    .createQuery("from City where dataset = :activeDataset")
                    .setInteger("activeDataset", Airways.ACTIVE_DATASET)
                    .list();
            logger.info("loaded {} cities", cities.size());

            //noinspection unchecked
            List<Airport2City> existingLinks = session
                    .createQuery("from Airport2City where dataset = :activeDataset")
                    .setInteger("activeDataset", Airways.ACTIVE_DATASET)
                    .list();
            logger.info("loaded {} links", existingLinks.size());


            for (City city : cities) {
                logger.info("processing city {}", city.getName());

                Map<Double, Airport> distanceToAirport = new TreeMap<>();

                for (Airport airport : airports) {
                    double distance = Geo.distance(new Geo.Coords(city.getLatitude(), city.getLongitude()), new Geo.Coords(airport.getLatitude(), airport.getLongitude()));

                    while (distanceToAirport.containsKey(distance)) {
                        distance += 0.001;
                    }

                    distanceToAirport.put(distance, airport);
                }

                double maxDistanceToAirport = 100;
                for (Map.Entry<Double, Airport> entry : distanceToAirport.entrySet()) {
                    double distance = entry.getKey();
                    if (distance > maxDistanceToAirport) {
                        break;
                    }

                    Airport airport = entry.getValue();

                    logger.info("checking link {} -> {}", city.getName(), airport.getIcao());
                    Airport2City existingLink = null;
                    for (Airport2City each : existingLinks) {
                        if (airport.getId().equals(each.getAirport().getId())
                                && city.getId().equals(each.getCity().getId())) {
                            existingLink = each;
                            break;
                        }
                    }

                    if (existingLink != null) {
                        if (!existingLink.getDataset().equals(Airways.ACTIVE_DATASET)) {
                            existingLink.setDataset(Airways.ACTIVE_DATASET);

                            HibernateUtils.updateAndCommit(session, existingLink);

                            logger.info("    dataset changed to active in exising link");
                        } else {
                            logger.info("    link is fine");
                        }

                        existingLinks.remove(existingLink);
                    } else {
                        Airport2City newLink = new Airport2City();
                        newLink.setAirport(airport);
                        newLink.setCity(city);
                        newLink.setDataset(Airways.ACTIVE_DATASET);

                        HibernateUtils.saveAndCommit(session, newLink);

                        logger.info("    new link saved");
                    }
                }
            }
        }
    }
}
