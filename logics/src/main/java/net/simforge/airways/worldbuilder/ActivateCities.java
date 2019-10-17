/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder;

import net.simforge.airways.Airways;
import net.simforge.airways.model.geo.City;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ActivateCities {
    private static final Logger logger = LoggerFactory.getLogger(ActivateCities.class.getName());

    public static void main(String[] args) throws IOException {
        logger.info("Activate cities");

        try (SessionFactory sessionFactory = Airways.buildSessionFactory();
             Session session = sessionFactory.openSession()) {

            //noinspection JpaQlInspection,unchecked
            List<City> cities = session
                    .createQuery("from City where population >= :population and dataset != :activeDataset")
                    .setInteger("population", 1000000)
                    .setInteger("activeDataset", Airways.ACTIVE_DATASET)
                    .list();

            logger.info("found {} cities to activate", cities.size());

            for (City city : cities) {
                city.setDataset(Airways.ACTIVE_DATASET);

                HibernateUtils.updateAndCommit(session, city);

                logger.info("city {} with population {} activated", city.getName(), city.getPopulation());
            }
        }
    }
}
