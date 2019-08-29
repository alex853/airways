package net.simforge.airways.cityflows;

import net.simforge.airways.persistence.AirwaysApp;
import net.simforge.airways.persistence.model.flow.City2CityFlow;
import net.simforge.airways.persistence.model.flow.CityFlow;
import net.simforge.commons.HeartbeatTask;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class CityFlowTask extends HeartbeatTask<CityFlow> {
    private final SessionFactory sessionFactory;

    public CityFlowTask() {
        this(AirwaysApp.getSessionFactory());
    }

    public CityFlowTask(SessionFactory sessionFactory) {
        super("CityFlow", sessionFactory);
        this.sessionFactory = sessionFactory;
    }

    @Override
    protected void startup() {
        super.startup();

        BM.setLoggingPeriod(ChronoUnit.HOURS.getDuration().toMillis());
    }

    @Override
    protected CityFlow heartbeat(CityFlow _cityFlow) {
        BM.start("CityFlowTask.heartbeat");
        try (Session session = sessionFactory.openSession()) {
            CityFlow cityFlow = session.get(CityFlow.class, _cityFlow.getId());

            HibernateUtils.transaction(session, () -> {
                switch (cityFlow.getStatus()) {
                    case CityFlow.Status.Active:
                    case CityFlow.Status.Inactive:
                        logger.warn("CityFlow {} is in {} status, redistribution is not required", cityFlow.getCity().getName(), cityFlow.getStatus());
                        cityFlow.setHeartbeatDt(null);
                        break;
                    case CityFlow.Status.RedistributeThenActivate:
                    case CityFlow.Status.ActiveNeedsRedistribution:
                        redistribute(session, cityFlow);
                        break;
                }

                session.update(cityFlow);
            });

            return cityFlow;
        } finally {
            BM.stop();
        }
    }

    private void redistribute(Session session, CityFlow cityFlow) {
        logger.info("CityFlow for city {} - redistribution", cityFlow.getCity().getName());

        List<CityFlow> allCityFlows = loadCityFlows(session);
        logger.debug("CityFlow for city {} - loaded {} city flows", cityFlow.getCity().getName(), allCityFlows.size());

        double totalUnits = 0;
        double unitsThreshold = cityFlow.getUnitsThreshold() != null ? cityFlow.getUnitsThreshold() : CityFlowOps.DefaultUnitsThreshold;
        logger.debug("CityFlow for city {} - unitsThreshold", cityFlow.getCity().getName(), unitsThreshold);

        List<CityFlow> activeCityFlows = new ArrayList<>();
        for (CityFlow eachCityFlow : allCityFlows) {
            if (cityFlow.getId().equals(eachCityFlow.getId())) {
                continue;
            }

//                    if (hasAirportConnections(connx, cityFlow, eachCityFlow)) {
//                        continue;
//                    }

            double units = CityFlowOps.getUnits(cityFlow, eachCityFlow);

            if (units >= unitsThreshold) {
                totalUnits += units;
                activeCityFlows.add(eachCityFlow);
            }
        }
        logger.debug("CityFlow for city {} - there are {} active city flows, totalUnits is {}", cityFlow.getCity().getName(), activeCityFlows.size(), totalUnits);

        List<City2CityFlow> c2cFlows = loadC2CFlows(session, cityFlow);
        logger.debug("CityFlow for city {} - loaded {} C2C flows", cityFlow.getCity().getName(), c2cFlows.size());

        int created = 0;
        int activated = 0;
        int updated = 0;
        for (CityFlow activeCityFlow : activeCityFlows) {
            City2CityFlow city2cityFlow = c2cFlows.stream().filter(c2cFlow -> c2cFlow.getToFlow().getId().equals(activeCityFlow.getId())).findFirst().orElse(null);

            double units = CityFlowOps.getUnits(cityFlow, activeCityFlow);
            double percentage = units / totalUnits;

            if (city2cityFlow == null) {
                city2cityFlow = new City2CityFlow();
                city2cityFlow.setFromFlow(cityFlow);
                city2cityFlow.setToFlow(activeCityFlow);
                city2cityFlow.setActive(true);

                city2cityFlow.setUnits(units);
                city2cityFlow.setPercentage(percentage);
                city2cityFlow.setAvailability(cityFlow.getDefaultAvailability() != null ? cityFlow.getDefaultAvailability() : CityFlowOps.DefaultAvailability);

                city2cityFlow.setNextGroupSize(CityFlowOps.randomGroupSize());
                city2cityFlow.setAccumulatedFlow(0.0);
                city2cityFlow.setAccumulatedFlowDt(JavaTime.nowUtc());

                city2cityFlow.setHeartbeatDt(CityFlowOps.getNextC2CFlowHeartbeatDt(city2cityFlow));

                session.save(city2cityFlow);
                created++;
            } else {
                if (!city2cityFlow.getActive()) {
                    city2cityFlow.setActive(true);

                    city2cityFlow.setNextGroupSize(CityFlowOps.randomGroupSize());
                    city2cityFlow.setAccumulatedFlow(0.0);
                    city2cityFlow.setAccumulatedFlowDt(JavaTime.nowUtc());

                    activated++;
                }

                city2cityFlow.setPercentage(percentage);

                city2cityFlow.setHeartbeatDt(CityFlowOps.getNextC2CFlowHeartbeatDt(city2cityFlow));

                session.update(city2cityFlow);
                updated++;

                c2cFlows.remove(city2cityFlow);
            }
        }

        // all remained city2city flows - mark them as inactive
        int deactivated = 0;
        for (City2CityFlow c2cFlow : c2cFlows) {
            if (c2cFlow.getActive()) {
                c2cFlow.setActive(false);

                HibernateUtils.updateAndCommit(session, "deactivateC2C", c2cFlow);
                deactivated++;
            }
        }

        // make it active and remove city flow from queue
        cityFlow.setStatus(CityFlow.Status.Active);
        cityFlow.setHeartbeatDt(null);
        cityFlow.setLastRedistributionDt(JavaTime.nowUtc());

        logger.info("CityFlow for city {} - done: created {} flows, activated {}, updated {}, deactivated {}", cityFlow.getCity().getName(), created, activated, updated, deactivated);
    }

    private List<CityFlow> loadCityFlows(Session session) {
        BM.start("CityFlowTask.loadCityFlows");
        try {
            //noinspection JpaQlInspection,unchecked
            return session
                    .createQuery("from CityFlow where status in (:active, :redistributeThenActivate, :activeNeedsRedistribution)")
                    .setInteger("active", CityFlow.Status.Active)
                    .setInteger("redistributeThenActivate", CityFlow.Status.RedistributeThenActivate)
                    .setInteger("activeNeedsRedistribution", CityFlow.Status.ActiveNeedsRedistribution)
                    .list();
        } finally {
            BM.stop();
        }
    }

    private List<City2CityFlow> loadC2CFlows(Session session, CityFlow cityFlow) {
        BM.start("CityFlowTask.loadC2CFlows");
        try {
            //noinspection JpaQlInspection,unchecked
            return session
                    .createQuery("from City2CityFlow where fromFlow = :flow")
                    .setEntity("flow", cityFlow)
                    .list();
        } finally {
            BM.stop();
        }
    }

/*
    private static boolean hasAirportConnections(Connection connx, CityFlow cityFlow1, CityFlow cityFlow2) throws SQLException {
        String sql = "select distinct(airport_id), count(city_id) " +
                "from aw_airport2city " +
                "where city_id = %cityId1% or city_id = %cityId2% " +
                "group by airport_id " +
                "having count(city_id) > 1";
        sql = sql.replaceAll("%cityId1%", String.valueOf(cityFlow1.getCityId()));
        sql = sql.replaceAll("%cityId2%", String.valueOf(cityFlow2.getCityId()));
        Statement st = connx.createStatement();
        ResultSet rs = st.executeQuery(sql);
        boolean result = rs.next(); // if we have at least one record in result then we have intercity airport connection
        rs.close();
        st.close();
        return result;
    }


    private void runRecalc() {
        try {
            String sql =
                    "select * from %tn% " +
                    "where status in (" + CityFlow.Status.RecalcThenActivate + ", " + CityFlow.Status.ActiveButNeedsRecalc + ") " +
                    "or (status = " + CityFlow.Status.Active + " and last_recalc_dt < '" + DT.DTF.print(DT.addDays(-1)) + "') " +
                    "limit 10";

            Connection connx = DB.getConnection();
            List<CityFlow> cityFlows = Persistence.loadByQuery(connx, CityFlow.class, sql);
            connx.close();

            if (cityFlows.isEmpty()) {
                return;
            }

            getLogger().info("Recalc: " + cityFlows.size() + " to process");

            for (CityFlow cityFlow : cityFlows) {
                recalcCityFlow(cityFlow);
            }
        } catch (SQLException e) {
            getLogger().error("SQL exception happened", e);
        }
    }

*/
}
