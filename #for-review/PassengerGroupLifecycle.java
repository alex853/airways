/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.legacy.airways;

import net.simforge.legacy.lifecycle.*;
import net.simforge.legacy.airways.model.*;
import net.simforge.legacy.airways.newflows.NewFlowLogics;
import net.simforge.legacy.airways.newflows.City2CityFlowStats;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.apache.log4j.Logger;

import forge.commons.db.DB;
import net.simforge.commons.persistence.Persistence;
import org.joda.time.DateTime;

@LifecycleDefinition(clazz = PassengerGroup.class, finalStatus = PassengerGroup.Status.Died, batchSize = 1000)
@WorldTask(period = 5)
public class PassengerGroupLifecycle extends BaseLifecycle {

    private static Logger logger;

    public static Logger getLogger() {
        if (logger == null) {
            logger = Logger.getLogger("PassengerGroups");
        }
        return logger;
    }

    @StatusHandler(code = PassengerGroup.Status.LookingForTickets)
    public void tickLookingForTickets() throws SQLException {
        Connection connx = DB.getConnection();
        PassengerGroup group = getGroup();
        if (isExpired()) {
            group.setStatus(PassengerGroup.Status.CouldNotFindTickets);
            group.setHeartbeatDt(DT.addDays(7));
            Persistence.update(connx, group);

            City2CityFlowStats stats = NewFlowLogics.getCurrentStats(connx, group.getC2cFlowId());
            stats.setCouldNotFindTickets(stats.getCouldNotFindTickets() + group.getSize());
            Persistence.update(connx, stats);

            getLogger().info(l(group) + " Could not find tickets");
            makeLog(connx, group, "Could not find tickets");
        } else {
            long searchingStart = System.currentTimeMillis();
            List<Flight> flights = TicketSearching.search(connx, group);
            long searchingEnd = System.currentTimeMillis();
            if (flights == null) { // if can not find
                group.setHeartbeatDt(DT.addHours(new DateTime(), 1));
                Persistence.update(connx, group);
            } else {
                getLogger().info(l(group) + " Tickets found for " + (searchingEnd - searchingStart) + " ms");

                List<PassengerGroupItinerary> itineraries = getItineraries(connx);
                int order = itineraries.size() + 1;
                PassengerGroupItinerary firstItinerary = null;
                for (Flight flight : flights) {
                    PassengerGroupItinerary itinerary = new PassengerGroupItinerary();
                    itinerary.setGroupId(group.getId());
                    itinerary.setItemOrder(order++);
                    itinerary.setFlightId(flight.getId());
                    itinerary = Persistence.create(connx, itinerary);
                    if (firstItinerary == null) {
                        firstItinerary = itinerary;
                    }
                    flight.setFreeTickets(flight.getFreeTickets() - group.getSize());
                    Persistence.update(connx, flight);
                    makeLog(connx, group, "Tickets bought", 0, 0, flight.getId());
                }

                group.setStatus(PassengerGroup.Status.WaitingForFlight);
                //noinspection ConstantConditions
                group.setItineraryId(firstItinerary.getId());
                group.setHeartbeatDt(DT.addMinutes(new DateTime(), 1));
                Persistence.update(connx, group);

                City2CityFlowStats stats = NewFlowLogics.getCurrentStats(connx, group.getC2cFlowId());
                stats.setTicketsBought(stats.getTicketsBought() + group.getSize());
                Persistence.update(connx, stats);

                getLogger().info(l(group) + " Waiting for flight " + flights.get(0).getNumber() + " " + DT.DtF.print(flights.get(0).getDepTime()));
                makeLog(connx, group, "Waiting for flight", group.getPositionCityId(), 0, flights.get(0).getId());
            }
        }
        connx.commit();
        connx.close();
    }

    @StatusHandler(code = PassengerGroup.Status.CouldNotFindTickets)
    public void tickCouldNotFindTickets() throws SQLException {
        die();
    }

    @StatusHandler(code = PassengerGroup.Status.WaitingForFlight)
    public void tickWaitingForFlight() throws SQLException {
        Connection connx = DB.getConnection();
        PassengerGroup group = getGroup();
        Flight flight = getCurrentFlight(connx);
        int flightStatus = flight.getStatus();
        if (flightStatus < Flight.Status.Boarding) {
            // wait more
            group.setHeartbeatDt(DT.addRandomMinutes(FlightHelper.getBoardingDT(flight), FlightHelper.BoardingDuration / 2));
        } else if (flightStatus == Flight.Status.Boarding) {
            // do boarding
            group.setPositionCityId(0);
            group.setPositionFlightId(flight.getId());
            group.setStatus(PassengerGroup.Status.OnBoard);
            group.setHeartbeatDt(DT.addMinutes(new DateTime(), 1));
            getLogger().info(l(group) + " OnBoard");
            makeLog(connx, group, "On board", 0, 0, flight.getId());
        } else {
            // too late to board
            group.setStatus(PassengerGroup.Status.TooLateToBoard);
            group.setHeartbeatDt(DT.addDays(new DateTime(), 7));
            getLogger().info(l(group) + " Too late to board");
            makeLog(connx, group, "Too late to board");
        }

        Persistence.update(connx, group);
        connx.commit();
        connx.close();
    }

    @StatusHandler(code = PassengerGroup.Status.TooLateToBoard)
    public void tickTooLateToBoard() throws SQLException {
        die();
    }

    @StatusHandler(code = PassengerGroup.Status.OnBoard)
    public void tickOnBoard() throws SQLException {
        Connection connx = DB.getConnection();
        PassengerGroup group = getGroup();
        Flight flight = getCurrentFlight(connx);
        int flightStatus = flight.getStatus();
        if (flightStatus == Flight.Status.Unboarding || flightStatus == Flight.Status.Done) {
            group.setPositionAirportId(flight.getToAirportId());
            group.setPositionFlightId(0);
            group.setStatus(PassengerGroup.Status.Arrived);
            group.setHeartbeatDt(DT.addMinutes(new DateTime(), 1));
            getLogger().info(l(group) + " Unboarded");
            makeLog(connx, group, "Unboarded", 0, flight.getToAirportId(), flight.getId());
        } else {
            group.setHeartbeatDt(DT.addMinutes(new DateTime(), 10));
        }
        Persistence.update(connx, group);
        connx.commit();
        connx.close();
    }

    private Flight getCurrentFlight(Connection connx) throws SQLException {
        PassengerGroup group = getGroup();
        int itineraryId = group.getItineraryId();
        PassengerGroupItinerary itinerary = Persistence.load(connx, PassengerGroupItinerary.class, itineraryId);
        int flightId = itinerary.getFlightId();
        return Persistence.load(connx, Flight.class, flightId);
    }

    @StatusHandler(code = PassengerGroup.Status.Arrived)
    public void tickArrived() throws SQLException {
        Connection connx = DB.getConnection();
        PassengerGroup group = getGroup();
        List<PassengerGroupItinerary> itineraries = getItineraries(connx);
        int itineraryIndex = -1;
        for (int i = 0; i < itineraries.size(); i++) {
            if (itineraries.get(i).getId() == group.getItineraryId()) {
                itineraryIndex = i;
                break;
            }
        }
        if (itineraryIndex == itineraries.size()-1) {
            group.setStatus(PassengerGroup.Status.ItinerariesDone);
            group.setHeartbeatDt(DT.addMinutes(new DateTime(), 1));
            getLogger().info(l(group) + " Itineraries done");
            makeLog(connx, group, "Itineraries done");

            City2CityFlowStats stats = NewFlowLogics.getCurrentStats(connx, group.getC2cFlowId());
            stats.setItinerariesDone(stats.getItinerariesDone() + group.getSize());
            Persistence.update(connx, stats);
        } else {
            group.setItineraryId(itineraries.get(itineraryIndex+1).getId());
            group.setStatus(PassengerGroup.Status.WaitingForFlight);
            group.setHeartbeatDt(DT.addMinutes(new DateTime(), 10));
            Flight flight = getCurrentFlight(connx);
            getLogger().info(l(group) + " Waiting for flight " + flight.getNumber() + " " + DT.DtF.print(flight.getDepTime()));
            makeLog(connx, group, "Waiting for flight", 0, group.getPositionAirportId(), flight.getId());
        }
        Persistence.update(connx, group);
        connx.commit();
        connx.close();
    }

    private List<PassengerGroupItinerary> getItineraries(Connection connx) throws SQLException {
        String sql = "select * from %tn% where group_id = " + getGroup().getId() + " order by item_order";
        return Persistence.loadByQuery(connx, PassengerGroupItinerary.class, sql);
    }

    @StatusHandler(code = PassengerGroup.Status.ItinerariesDone)
    public void tickItinerariesDone() throws SQLException {
        Connection connx = DB.getConnection();
        PassengerGroup group = getGroup();

        boolean done;
        if (group.isRoundtrip()) {
            //noinspection RedundantIfStatement
            if (group.isPositionRoundtrip()) {
                group.setPositionCityId(group.getFromCityId());
                done = true;
            } else {
                group.setPositionCityId(group.getToCityId());
                done = false;
            }
        } else {
            group.setPositionCityId(group.getToCityId());
            done = true;
        }
        group.setPositionAirportId(0);

        if (done) {
            group.setStatus(PassengerGroup.Status.Done);
            group.setHeartbeatDt(DT.addDays(new DateTime(), 7));
            getLogger().info(l(group) + " Done");
            makeLog(connx, group, "Done", group.getPositionCityId(), 0, 0);
        } else {
            group.setStatus(PassengerGroup.Status.LivingAtDestination);
            group.setPositionRoundtrip(true);
            group.setHeartbeatDt(DT.addDays(new DateTime(), 3));
            getLogger().info(l(group) + " LivingAtDestination");
            makeLog(connx, group, "Living at destination", group.getPositionCityId(), 0, 0);
        }
        Persistence.update(connx, group);
        connx.commit();
        connx.close();
    }

    @StatusHandler(code = PassengerGroup.Status.LivingAtDestination)
    public void tickLivingAtDestination() throws SQLException {
        Connection connx = DB.getConnection();
        PassengerGroup group = getGroup();
        group.setStatus(PassengerGroup.Status.LookingForTickets);
        group.setHeartbeatDt(DT.addMinutes(new DateTime(), 10));
        group.setExpireDt(DT.addDays(14));
        getLogger().info(l(group) + " LookingForTickets - returning");
        Persistence.update(connx, group);
        makeLog(connx, group, "Looking for returning tickets");
        connx.commit();
        connx.close();
    }

    @StatusHandler(code = PassengerGroup.Status.Done)
    public void tickDone() throws SQLException {
        die();
    }

    private void die() throws SQLException {
        Connection connx = DB.getConnection();
        PassengerGroup group = getGroup();
        group.setStatus(PassengerGroup.Status.Died);
        Persistence.update(connx, group);
        makeLog(connx, group, "Died");
        connx.commit();
        connx.close();
    }

    private PassengerGroup getGroup() {
        return (PassengerGroup) getObject();
    }

    public boolean isExpired() {
        return DT.isPast(getGroup().getExpireDt());
    }

    public static void main(String[] args) {
        new LifecycleRunnable(PassengerGroupLifecycle.class).run();
    }

    public static String l(PassengerGroup group) {
        String route = RefDataCache.getCity(group.getFromCityId()).getName() + "-" + RefDataCache.getCity(group.getToCityId()).getName();
        if (group.isRoundtrip()) {
            route += "-" + RefDataCache.getCity(group.getFromCityId()).getName();
        }
        return route + " (#" + String.valueOf(group.getId()) + ")";
    }

    public static void makeLog(Connection connx, PassengerGroup group, String msg) throws SQLException {
        makeLog(connx, group, msg, 0, 0, 0);
    }

    public static void makeLog(Connection connx, PassengerGroup group, String msg, int cityId, int airportId, int flightId) throws SQLException {
        PassengerGroupEvent e = new PassengerGroupEvent();
        e.setGroupId(group.getId());
        e.setDt(new DateTime());
        e.setMsg(msg);
        e.setCityId(cityId);
        e.setAirportId(airportId);
        e.setFlightId(flightId);
        Persistence.create(connx, e);
    }
}
