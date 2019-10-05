/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder;

import net.simforge.airways.Airways;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ActivateAirports {
    private static final Logger logger = LoggerFactory.getLogger(ActivateAirports.class.getName());

    public static void main(String[] args) throws IOException {
        logger.info("Activate airports");

        try (SessionFactory sessionFactory = Airways.buildSessionFactory();
             Session session = sessionFactory.openSession()) {

            //noinspection JpaQlInspection,unchecked
            List<Airport> airports = session
                    .createQuery("from Airport where dataset != :activeDataset")
                    .setInteger("activeDataset", Airways.ACTIVE_DATASET)
                    .list();

            logger.info("found {} airports to activate", airports.size());

            for (Airport airport : airports) {
                airport.setDataset(Airways.ACTIVE_DATASET);

                HibernateUtils.updateAndCommit(session, airport);

                logger.info("airport {} activated", airport.getName());
            }
        }
    }
}
