/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.cityflows;

import net.simforge.airways.persistence.model.flow.City2CityFlow;
import net.simforge.airways.persistence.model.flow.City2CityFlowStats;
import net.simforge.airways.persistence.model.flow.CityFlow;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class CityFlowOps {
    private static double MinAvailability = 0.000001;
    private static double MaxAvailability = 1.0;

    public static final long DAY = TimeUnit.DAYS.toMillis(1);

    public static final double DefaultAttraction = 1;
    public static final double DefaultUnitsThreshold = 0.1;
    public static final double DefaultAvailability = 0.01;

    private static final Random random = new Random();

    public static int getDailyFlow(City city) {
        return (int) (city.getPopulation() * 0.001);
    }

    public static double boundAvailability(double availability) {
        if (availability < MinAvailability)
            return MinAvailability;
        if (availability > MaxAvailability)
            return MaxAvailability;
        return availability;
    }

    public static int randomGroupSize() {
        return (int) (Math.random() * 10) + 1;
    }

    public static boolean randomDirection() {
        return Math.random() < 0.5;
    }

    public static double getUnits(CityFlow fromCityFlow, CityFlow toCityFlow) {
        BM.start("CityFlowOps.getUnits");
        try {
            double attractionUnits = toCityFlow.getAttraction() != null ? toCityFlow.getAttraction() : DefaultAttraction;

            City fromCity = fromCityFlow.getCity();
            City toCity = toCityFlow.getCity();

            double dist = Geo.distance(new Geo.Coords(fromCity.getLatitude(), fromCity.getLongitude()), new Geo.Coords(toCity.getLatitude(), toCity.getLongitude()));
            double distUnits = dist / 500;
            if (distUnits < 1) {
                distUnits = 1;
            }

            return attractionUnits / distUnits;
        } finally {
            BM.stop();
        }
    }

    public static LocalDateTime getNextC2CFlowHeartbeatDt(City2CityFlow flow) {
        BM.start("CityFlowOps.getNextC2CFlowHeartbeatDt");
        try {
            LocalDateTime now = JavaTime.nowUtc();

            if (flow.getAccumulatedFlow() >= flow.getNextGroupSize()) {
                return now.plusMinutes(1);
            }

            CityFlow fromCityFlow = flow.getFromFlow();
            City city = fromCityFlow.getCity();
            int dailyFlow = CityFlowOps.getDailyFlow(city);

            double requiredFlowToDistribute = (flow.getNextGroupSize() - flow.getAccumulatedFlow()) / flow.getPercentage() / CityFlowOps.boundAvailability(flow.getAvailability());
            long requiredMillis = (long) (requiredFlowToDistribute * DAY / dailyFlow);

            LocalDateTime accumulatedFlowDt = flow.getAccumulatedFlowDt();
            //noinspection UnnecessaryLocalVariable
            LocalDateTime result = accumulatedFlowDt.plus(requiredMillis, ChronoUnit.MILLIS).plusMinutes(1);
            return result;
        } finally {
            BM.stop();
        }
    }

    public static City2CityFlowStats getCurrentStats(Session session, City2CityFlow c2cFlow) {
        BM.start("CityFlowOps.getCurrentStats");
        try {
            LocalDate date = JavaTime.nowUtc().toLocalDate();

            //noinspection JpaQlInspection
            City2CityFlowStats stats = (City2CityFlowStats) session
                    .createQuery("from City2CityFlowStats where c2cFlow = :flow and date = :date")
                    .setEntity("flow", c2cFlow)
                    .setParameter("date", date)
                    .setMaxResults(1)
                    .uniqueResult();

            if (stats != null) {
                return stats;
            }

            stats = new City2CityFlowStats();
            stats.setC2cFlow(c2cFlow);
            stats.setDate(date);
            stats.setHeartbeatDt(date.plusDays(1).atTime(4 + random.nextInt(24), random.nextInt(60)));
            stats.setAvailabilityBefore(0.0);
            stats.setAvailabilityAfter(0.0);
            stats.setAvailabilityDelta(0.0);
            stats.setNoTickets(0);
            stats.setTicketsBought(0);
            stats.setTravelled(0);

            session.save(stats);

            return stats;
        } finally {
            BM.stop();
        }
    }

    public static double calcAvailabilityDelta(City2CityFlow c2cFlow, City2CityFlowStats stats) {
        CityFlow cityFlow = c2cFlow.getFromFlow();
        double dailyFlow = CityFlowOps.getDailyFlow(cityFlow.getCity());

        int noTickets = stats.getNoTickets();
        int ticketsBought = stats.getTicketsBought();
        int travelled = stats.getTravelled();

        double noTicketsWeight = 1.0;
        double ticketsBoughtWeight = 3.0;
        double travelledWeight = 6.0;

        //noinspection UnnecessaryLocalVariable
        double totalDelta = (-noTickets * noTicketsWeight + ticketsBought * ticketsBoughtWeight + travelled * travelledWeight) / (noTicketsWeight + ticketsBoughtWeight + travelledWeight);
        totalDelta /= dailyFlow;

        return totalDelta;
    }
}
