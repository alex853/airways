package net.simforge.airways.worldbuilder.tools;

import net.simforge.airways.Airways;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.Airport2City;
import net.simforge.airways.model.geo.City;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.io.Csv;
import net.simforge.commons.misc.Geo;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class UpdateAirport2CityLinks {
    private static final Logger logger = LoggerFactory.getLogger(UpdateAirport2CityLinks.class.getName());
    public static final int maxDistance = 100;

    public static void main(String[] args) throws IOException {
        logger.info("Update Airport2City links");

        Map<String, Integer> icao2size = new HashMap<>();

        Csv csv = Csv.load(new File("./data/icaodata.csv"));
        logger.info("source dataset contains {} airports", csv.rowCount());

        for (int i = 0; i < csv.rowCount(); i++) {
            String icao = csv.value(i, "icao");
            String sizeStr = csv.value(i, "size");
            icao2size.put(icao, Integer.parseInt(sizeStr));
        }

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
                    .createQuery("from Airport2City")
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

                for (Map.Entry<Double, Airport> entry : distanceToAirport.entrySet()) {
                    Airport airport = entry.getValue();

                    logger.info("checking link {} -> {}", city.getName(), airport.getIcao());
                    Airport2City existingLink = existingLinks.stream()
                            .filter(each -> airport.getId().equals(each.getAirport().getId())
                                    && city.getId().equals(each.getCity().getId()))
                            .findFirst().orElse(null);

                    Integer size = icao2size.get(airport.getIcao());
                    double distance = entry.getKey();

                    if (distance > maxDistance + 1) {
                        break;
                    }

                    double maxDistanceDueSize = maxDistanceDueSize(size);
                    boolean linkShouldBeActive = distance < maxDistanceDueSize;
                    logger.info("    distance {}, size {}, max distance due size {}, should be active {}, existing link {}", distance, size, maxDistanceDueSize, linkShouldBeActive, existingLink);

                    if (linkShouldBeActive) {
                        if (existingLink != null) {
                            if (!existingLink.getDataset().equals(Airways.ACTIVE_DATASET)) {
                                existingLink.setDataset(Airways.ACTIVE_DATASET);

                                HibernateUtils.updateAndCommit(session, existingLink);

                                logger.info("    dataset changed to active in existing link");
                            } else {
                                logger.info("    link is ACTIVE and should be active");
                            }

                            existingLinks.remove(existingLink);
                        } else {
                            Airport2City newLink = new Airport2City();
                            newLink.setAirport(airport);
                            newLink.setCity(city);
                            newLink.setDataset(Airways.ACTIVE_DATASET);

                            HibernateUtils.saveAndCommit(session, newLink);

                            logger.info("    new active link saved");
                        }
                    } else {
                        if (existingLink != null) {
                            if (existingLink.getDataset().equals(Airways.ACTIVE_DATASET)) {
                                existingLink.setDataset(Airways.INACTIVE_DATASET);

                                HibernateUtils.updateAndCommit(session, existingLink);

                                logger.info("    dataset changed to INACTIVE in existing link");
                            } else {
                                logger.info("    link is INACTIVE and should be inactive");
                            }

                            existingLinks.remove(existingLink);
                        } else {
                            logger.info("    link does not exist and should not be created");
                        }
                    }
                }
            }
        }
    }

    private static double maxDistanceDueSize(Integer size) {
        if (size == null) {
            return 0;
        } else if (size < 2500) {
            return 10;
        } else if (size < 4000) {
            return 20;
        } else if (size < 6000) {
            return 35;
        } else if (size < 9000) {
            return 65;
        } else {
            return maxDistance;
        }
    }
}
