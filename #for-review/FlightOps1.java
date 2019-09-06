/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage3;

import net.simforge.airways.stage3.model.aircraft.Aircraft;
import net.simforge.airways.stage3.model.aircraft.AircraftEntity;
import net.simforge.airways.stage3.model.flight.*;
import net.simforge.airways.stage3.model.geo.Airport;
import net.simforge.airways.stage3.model.person.Person;
import net.simforge.airways.stage3.model.person.Pilot;
import net.simforge.airways.stage3.model.person.PilotEntity;
import net.simforge.commons.hibernate.AuditInterceptor;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class FlightOps implements BaseOps<Flight> {

    private static Logger logger = LoggerFactory.getLogger(FlightOps.class.getName());

    private final EntityStorage storage;
    private final SessionFactory sessionFactory;

    public FlightOps(EntityStorage storage, SessionFactory sessionFactory) {
        this.storage = storage;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public List<Flight> whereHeartbeatDtBelow(LocalDateTime threshold, int resultLimit) {
        BM.start("FlightOps1.whereHeartbeatDtBelow");
        try {
            return storage.filter(Flight.class,
                    (flight) -> flight.getHeartbeatDt() != null && flight.getHeartbeatDt().isBefore(threshold),
                    resultLimit);
        } finally {
            BM.stop();
        }
    }

    @Override
    public void arrangeHeartbeatAt(Flight _flight, LocalDateTime nextHeartbeatDt) {
        arrangeHeartbeatAt(_flight, nextHeartbeatDt, null);
    }

    @Override
    public void arrangeHeartbeatAt(Flight _flight, LocalDateTime nextHeartbeatDt, String msg) {
        try (Session session = openSession()) {
            HibernateUtils.transaction(session, "FlightOps1.arrangeHeartbeatAt", () -> {
                Flight flight = session.get(FlightEntity.class, _flight.getId());
                flight.setHeartbeatDt(nextHeartbeatDt);
                session.update(flight);

                if (msg != null) {
                    session.save(EventLog.make(flight, msg));
                }
            });
        }
    }

    @Override
    public void arrangeHeartbeatIn(Flight _flight, long millisTillNextHeartbeat) {
        try (Session session = openSession()) {
            HibernateUtils.transaction(session, "FlightOps1.arrangeHeartbeatIn", () -> {
                Flight flight = session.get(FlightEntity.class, _flight.getId());
                flight.setHeartbeatDt(JavaTime.nowUtc().plus(millisTillNextHeartbeat, ChronoUnit.MILLIS));
                session.update(flight);
            });
        }
    }

    public void cancelFlight(Flight _flight, String reason) {
        try (Session session = openSession()) {
            HibernateUtils.transaction(session, "FlightOps1.cancel", () -> {
                logger.debug("Cancelling flight {}, reason '{}'", _flight, reason);

                FlightContext cachedFlightContext = FlightContext.fromCache(_flight);
                FlightContext loadedFlightContext = FlightContext.load(session, cachedFlightContext);

                Flight flight = loadedFlightContext.getFlight();
                AircraftAssignment aircraftAssignment = loadedFlightContext.getAircraftAssignment();
                PilotAssignment pilotAssignment = loadedFlightContext.getPilotAssignment();

                flight.setStatus(Flight.Status.Cancelled);
                flight.setHeartbeatDt(null);
                session.update(flight);

                String msg = "Flight cancelled, reason is '" + reason + "'";
                session.save(EventLog.make(flight, msg));

                if (aircraftAssignment != null) {
                    logger.debug("Aircraft assignment found, status is {}, cancelling", aircraftAssignment.getStatus());

                    aircraftAssignment.setStatus(AircraftAssignment.Status.Cancelled);
                    session.update(aircraftAssignment);

                    Aircraft aircraft = loadedFlightContext.getAircraft();
                    aircraft.setStatus(Aircraft.Status.Idle); // this is to unset IdlePlanned
                    session.update(aircraft);

                    session.save(EventLog.make(flight, msg, aircraftAssignment.getAircraft()));
                }

                if (pilotAssignment != null) {
                    logger.debug("Pilot assignment found, status is {}, cancelling", pilotAssignment.getStatus());

                    pilotAssignment.setStatus(PilotAssignment.Status.Cancelled);
                    session.update(pilotAssignment);

                    // reset pilot heartbeat when pilot assignment changed
                    Pilot pilot = pilotAssignment.getPilot();
                    pilot.setStatus(Pilot.Status.Idle); // this is to unset IdlePlanned
                    pilot.setHeartbeatDt(JavaTime.nowUtc());
                    session.update(pilot);

                    logger.debug("Pilot {} gets heartbeat", pilot);

                    session.save(EventLog.make(flight, msg, pilot));
                }

                logger.info("Flight {} - CANCELLED", flight);
            });
        }
    }

    public void doStupidAllocation(Flight _flight) {
        try (Session session = openSession()) {
            HibernateUtils.transaction(session, "FlightOps1.doStupidAllocation", () -> {
                logger.debug("Doing stupid allocation for flight {}", _flight);

                FlightContext cachedFlightContext = FlightContext.fromCache(_flight);
//                FlightContext loadedFlightContext = FlightContext.load(session, cachedFlightContext);

                AircraftAssignment aircraftAssignment = cachedFlightContext.getAircraftAssignment();
                if (aircraftAssignment == null) {
                    List<Aircraft> aircrafts = storage.filter(Aircraft.class,
                            (aircraft) -> aircraft.getStatus() == Aircraft.Status.Idle
                                    // todo nov17 airline check && aircraft.getAirline().getId().equals(_flight.get?????????)
                                    && aircraft.getType().getId().equals(_flight.getAircraftType().getId())
                                    && aircraft.getPositionAirport().getId().equals(_flight.getFromAirport().getId()));
                    if (!aircrafts.isEmpty()) {
                        Aircraft aircraft = aircrafts.get(0);
                        aircraft = session.load(AircraftEntity.class, aircraft.getId());

                        aircraft.setStatus(Aircraft.Status.IdlePlanned);
                        session.update(aircraft);

                        aircraftAssignment = new AircraftAssignmentEntity();
                        aircraftAssignment.setFlight(session.load(FlightEntity.class, _flight.getId()));
                        aircraftAssignment.setAircraft(aircraft);
                        aircraftAssignment.setStatus(AircraftAssignment.Status.Assigned);

                        session.save(aircraftAssignment);

                        session.save(EventLog.make(_flight, "Aircraft is assigned to flight", aircraft));

                        logger.info("Flight {} - aircraft {} allocated", _flight, aircraft.getRegNo());
                    }
                }

                PilotAssignment pilotAssignment = cachedFlightContext.getPilotAssignment();
                if (pilotAssignment == null) {
                    List<Pilot> pilots = storage.filter(Pilot.class,
                            (pilot) -> {
                                Person person = pilot.getPerson();
                                Airport positionAirport = person != null ? storage.<Person>get(Person.class, pilot.getPerson().getId()).getPositionAirport() : null;
                                boolean positionOk = positionAirport != null && positionAirport.getId().equals(_flight.getFromAirport().getId());

                                return pilot.getStatus() == Pilot.Status.Idle
                                        // todo nov17 airline check
                                        // todo nov17 license check
                                        && positionOk;
                            });
                    if (!pilots.isEmpty()) {
                        Pilot pilot = pilots.get(0);
                        pilot = session.load(PilotEntity.class, pilot.getId());

                        pilot.setStatus(Pilot.Status.IdlePlanned);
                        session.update(pilot);

                        pilotAssignment = new PilotAssignmentEntity();
                        pilotAssignment.setFlight(session.load(FlightEntity.class, _flight.getId()));
                        pilotAssignment.setPilot(pilot);
                        pilotAssignment.setStatus(PilotAssignment.Status.Assigned);

                        session.save(pilotAssignment);

                        session.save(EventLog.make(_flight, "Pilot is assigned to flight", pilot));

                        logger.info("Flight {} - pilot {} allocated", _flight, pilot);
                    }
                }

                cachedFlightContext = FlightContext.fromCache(_flight);
                if (cachedFlightContext.isFullyAssigned()) {
                    logger.debug("Flight {} is fully assigned", _flight);

                    FlightEntity flight = session.load(FlightEntity.class, _flight.getId());

                    flight.setStatus(Flight.Status.Assigned);
                    flight.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));
                    session.update(flight);

                    session.save(EventLog.make(flight, "Flight is fully assigned"));

                    logger.info("Flight {} - ASSIGNED", flight);

                    // reset pilot heartbeat when pilot assignment changed
                    Pilot pilot = session.load(PilotEntity.class, cachedFlightContext.getPilot().getId());
                    pilot.setHeartbeatDt(JavaTime.nowUtc());
                }
            });
        }
    }

    public Flight getOrLoad(Integer flightId) {
        Flight flight = storage.find(Flight.class, flightId);
        if (flight != null) {
            return flight;
        }

        BM.start("FlightOps1.getOrLoad#load");
        try (Session session = openSession()) {
            FlightEntity loaded = session.load(FlightEntity.class, flightId);
            storage.putIfKnown(loaded);

            return storage.get(Flight.class, flightId);
        } finally {
            BM.stop();
        }
    }

    private Session openSession() {
        return sessionFactory
                .withOptions()
                .interceptor(
                        new CacheInvalidationInterceptor(
                                storage,
                                new AuditInterceptor()))
                .openSession();
    }
}
