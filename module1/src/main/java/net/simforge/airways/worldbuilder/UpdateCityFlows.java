package net.simforge.airways.worldbuilder;

import net.simforge.airways.persistence.Airways;
import net.simforge.airways.persistence.model.flow.CityFlow;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class UpdateCityFlows {
    private static final Logger logger = LoggerFactory.getLogger(UpdateCityFlows.class.getName());

    public static void main(String[] args) {
        logger.info("Update City Flows");

        try (SessionFactory sessionFactory = Airways.buildSessionFactory();
             Session session = sessionFactory.openSession()) {

            //noinspection unchecked
            List<City> cities = session
                    .createQuery("from City")
                    .list();
            logger.info("loaded {} cities", cities.size());

            //noinspection unchecked
            List<CityFlow> existingCityFlows = session
                    .createQuery("from CityFlow")
                    .list();
            logger.info("loaded {} CityFlows", existingCityFlows.size());


            for (City city : cities) {
                logger.info("processing city {}", city.getName());

                CityFlow existingCityFlow = existingCityFlows.stream().filter(cityFlow -> cityFlow.getCity().getId().equals(city.getId())).findFirst().orElse(null);

                HibernateUtils.transaction(session, () -> {
                    if (city.getDataset() == Airways.ACTIVE_DATASET) {
                        if (existingCityFlow == null) {
                            CityFlow cityFlow = new CityFlow();
                            cityFlow.setCity(city);
                            cityFlow.setStatus(CityFlow.Status.RedistributeThenActivate);
                            cityFlow.setHeartbeatDt(JavaTime.nowUtc());

                            session.save(cityFlow);

                            logger.info("CityFlow for city {} added", city.getName());
                        } else if (existingCityFlow.getStatus() == CityFlow.Status.Inactive) {
                            if (existingCityFlow.getStatus() != CityFlow.Status.Active) {
                                existingCityFlow.setStatus(CityFlow.Status.RedistributeThenActivate);
                                existingCityFlow.setHeartbeatDt(JavaTime.nowUtc());

                                session.update(existingCityFlow);

                                logger.info("CityFlow for city {} marked as RedistributeThenActivate", city.getName());
                            }
                        } // else noop

                        // todo city2city flows
                    } else { // City is not Active
                        if (existingCityFlow != null) {
                            existingCityFlow.setStatus(CityFlow.Status.Inactive);
                            // todo set inactive city2city flows

                            session.update(existingCityFlow);
                            existingCityFlows.remove(existingCityFlow);

                            logger.info("City flow set Inactive for city {}", city.getName());
                        }
                    }
                });
            }
        }
    }
}
