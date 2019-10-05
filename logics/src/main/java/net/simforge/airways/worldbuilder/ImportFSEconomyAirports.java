/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder;

import net.simforge.airways.Airways;
import net.simforge.airways.ops.CommonOps;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.io.Csv;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class ImportFSEconomyAirports {
    private static final Logger logger = LoggerFactory.getLogger(ImportFSEconomyAirports.class.getName());

    public static void main(String[] args) throws IOException {
        logger.info("Importing FSEconomy airports data");

        try (SessionFactory sessionFactory = Airways.buildSessionFactory();
             Session session = sessionFactory.openSession()) {

            Csv csv = Csv.load(new File("./data/icaodata.csv"));
            logger.info("source dataset contains {} airports", csv.rowCount());

            for (int i = 0; i < csv.rowCount(); i++) {
                String icao = csv.value(i, "icao");

                Airport airport = CommonOps.airportByIcao(session, icao);
                if (airport != null) {
                    continue;
                }

                airport = new Airport();
                airport.setIcao(icao);
                airport.setName(csv.value(i, "name"));
                airport.setLatitude(Double.valueOf(csv.value(i, "lat")));
                airport.setLongitude(Double.valueOf(csv.value(i, "lon")));
                airport.setDataset(Airways.FSECONOMY_DATASET);

                HibernateUtils.saveAndCommit(session, airport);

                logger.info("airport {} added", icao);
            }
        }
    }
}
