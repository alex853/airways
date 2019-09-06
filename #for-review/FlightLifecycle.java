/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.legacy.airways;

import net.simforge.legacy.airways.model.Flight;
import net.simforge.legacy.airways.model.FlightEvent;
import net.simforge.legacy.lifecycle.*;

import java.sql.SQLException;
import java.sql.Connection;

import forge.commons.db.DB;
import net.simforge.commons.persistence.Persistence;
import org.joda.time.DateTime;
import org.apache.log4j.Logger;

@LifecycleDefinition(clazz = Flight.class, finalStatus = Flight.Status.Done)
public class FlightLifecycle extends BaseLifecycle {

    private static Logger logger;

    public static Logger getLogger() {
        if (logger == null) {
            logger = Logger.getLogger("Flights");
        }
        return logger;
    }

    @StatusHandler(code = Flight.Status.Scheduled)
    public void tickScheduled() throws SQLException {
        Connection connx = DB.getConnection();
        Flight flight = getFlight();
        if (DT.isPast(FlightHelper.getCheckinDT(flight))) {
            flight.setStatus(Flight.Status.Checkin);
            flight.setHeartbeatDt(DT.addMinutes(1));
            getLogger().info(l(flight) + " Check-in");
            makeLog(connx, flight, "Check-in started");
        } else {
            flight.setHeartbeatDt(FlightHelper.getCheckinDT(flight));
        }
        Persistence.update(connx, flight);
        connx.commit();
        connx.close();
    }

    @StatusHandler(code = Flight.Status.Checkin)
    public void tickCheckin() throws SQLException {
        Connection connx = DB.getConnection();
        Flight flight = getFlight();
        if (DT.isPast(FlightHelper.getCheckinDoneDT(flight))) {
            flight.setStatus(Flight.Status.CheckinDone);
            flight.setHeartbeatDt(DT.addMinutes(1));
            getLogger().info(l(flight) + " Check-in done");
            makeLog(connx, flight, "Check-in done");
        } else {
            flight.setHeartbeatDt(FlightHelper.getCheckinDoneDT(flight));
        }
        Persistence.update(connx, flight);
        connx.commit();
        connx.close();
    }

    @StatusHandler(code = Flight.Status.CheckinDone)
    public void tickCheckinDone() throws SQLException {
        Connection connx = DB.getConnection();
        Flight flight = getFlight();
        if (DT.isPast(FlightHelper.getBoardingDT(flight))) {
            flight.setStatus(Flight.Status.Boarding);
            flight.setHeartbeatDt(DT.addMinutes(1));
            getLogger().info(l(flight) + " Boarding");
            makeLog(connx, flight, "Boarding started");
        } else {
            flight.setHeartbeatDt(FlightHelper.getBoardingDT(flight));
        }
        Persistence.update(connx, flight);
        connx.commit();
        connx.close();
    }

    @StatusHandler(code = Flight.Status.Boarding)
    public void tickBoarding() throws SQLException {
        Connection connx = DB.getConnection();
        Flight flight = getFlight();
        int pob = FlightHelper.getPOB(connx, flight);
        if (DT.isPast(FlightHelper.getDeparingDT(flight))) {
            flight.setStatus(Flight.Status.Departing);
            flight.setHeartbeatDt(flight.getDepTime());
            Persistence.update(connx, flight);
            logBoardingDone(connx, flight, pob);
            logDeparting(connx, flight);
        } else {
            int seatsSold = flight.getTotalTickets() - flight.getFreeTickets();
            if (pob < seatsSold) {
                flight.setHeartbeatDt(DT.addMinutes(1));
                Persistence.update(connx, flight);
            } else {
                flight.setStatus(Flight.Status.BoardingDone);
                flight.setHeartbeatDt(DT.addMinutes(1));
                Persistence.update(connx, flight);
                logBoardingDone(connx, flight, pob);
            }
        }
        connx.commit();
        connx.close();
    }

    private void logBoardingDone(Connection connx, Flight flight, int pob) throws SQLException {
        makeLog(connx, flight, "Boarding done, passengers on board: " + pob);
    }

    private void logDeparting(Connection connx, Flight flight) throws SQLException {
        makeLog(connx, flight, "Departing");
        getLogger().info(l(flight) + " Departing");
    }

    @StatusHandler(code = Flight.Status.BoardingDone)
    public void tickBoardingDone() throws SQLException {
        Connection connx = DB.getConnection();
        Flight flight = getFlight();
        if (DT.isPast(FlightHelper.getDeparingDT(flight))) {
            flight.setStatus(Flight.Status.Departing);
            flight.setHeartbeatDt(flight.getDepTime());
            Persistence.update(connx, flight);
            logDeparting(connx, flight);
        } else {
            flight.setHeartbeatDt(DT.addMinutes(1));
            Persistence.update(connx, flight);
        }
        connx.commit();
        connx.close();
    }

    @StatusHandler(code = Flight.Status.Departing)
    public void tickDeparting() throws SQLException {
        Flight flight = getFlight();
        if (DT.isPast(flight.getDepTime())) {
            flight.setStatus(Flight.Status.Flying);
            flight.setHeartbeatDt(flight.getArrTime());
            Connection connx = DB.getConnection();
            Persistence.update(connx, flight);
            makeLog(connx, flight, "Takeoff");
            connx.commit();
            connx.close();
            getLogger().info(l(flight) + " Takeoff");
        }
    }

    @StatusHandler(code = Flight.Status.Flying)
    public void tickFlying() throws SQLException {
        Flight flight = getFlight();
        if (DT.isPast(flight.getArrTime())) {
            flight.setStatus(Flight.Status.Landed);
            flight.setHeartbeatDt(FlightHelper.getUnboardingDT(flight));
            Connection connx = DB.getConnection();
            Persistence.update(connx, flight);
            makeLog(connx, flight, "Landed");
            connx.commit();
            connx.close();
            getLogger().info(l(flight) + " Landed");
        }
    }

    @StatusHandler(code = Flight.Status.Landed)
    public void tickLanded() throws SQLException {
        Flight flight = getFlight();
        if (DT.isPast(FlightHelper.getUnboardingDT(flight))) {
            flight.setStatus(Flight.Status.Unboarding);
            flight.setHeartbeatDt(DT.addMinutes(1));
            Connection connx = DB.getConnection();
            Persistence.update(connx, flight);
            makeLog(connx, flight, "Unboarding started");
            connx.commit();
            connx.close();
            getLogger().info(l(flight) + " Unboarding");
        }
    }

    @StatusHandler(code = Flight.Status.Unboarding)
    public void tickUnboarding() throws SQLException {
        Connection connx = DB.getConnection();
        Flight flight = getFlight();
        if (DT.isPast(FlightHelper.getDoneDT(flight))) {
            makeFlightDone(flight, connx);
        } else {
            int pob = FlightHelper.getPOB(connx, flight);
            if (pob == 0) {
                flight.setStatus(Flight.Status.UnboardingDone);
                flight.setStatusDt(DT.now());
                makeLog(connx, flight, "Unboarding done");
                getLogger().info(l(flight) + " Unboarding done");
            }
            flight.setHeartbeatDt(DT.addMinutes(1));
            Persistence.update(connx, flight);
        }
        connx.commit();
        connx.close();
    }

    @StatusHandler(code = Flight.Status.UnboardingDone)
    public void tickUnboardingDone() throws SQLException {
        Connection connx = DB.getConnection();
        Flight flight = getFlight();
        if (DT.isPast(FlightHelper.getDoneDT(flight))) {
            makeFlightDone(flight, connx);
        } else {
            if (DT.isPast(FlightHelper.getEndOfUnboardingDoneDT(flight))) {
                makeFlightDone(flight, connx);
            } else {
                flight.setHeartbeatDt(DT.addMinutes(1));
                Persistence.update(connx, flight);
            }
        }
        connx.commit();
        connx.close();
    }

    @StatusHandler(code = Flight.Status.Done)
    public void tickDone() {
    }

    private void makeFlightDone(Flight flight, Connection connx) throws SQLException {
        flight.setStatus(Flight.Status.Done);
        Persistence.update(connx, flight);
        makeLog(connx, flight, "Flight done");
        getLogger().info(l(flight) + " Done");
    }

    private String l(Flight flight) {
        return flight.getNumber() + " " + DT.DF.print(new DateTime(flight.getDate()));
    }

    private Flight getFlight() {
        return (Flight) getObject();
    }

    public static void main(String[] args) {
        new LifecycleRunnable(FlightLifecycle.class).run();
    }

    public static void makeLog(Connection connx, Flight flight, String msg) throws SQLException {
        FlightEvent e = new FlightEvent();
        e.setFlightId(flight.getId());
        e.setDt(new DateTime());
        e.setMsg(msg);
        Persistence.create(connx, e);
    }
}
