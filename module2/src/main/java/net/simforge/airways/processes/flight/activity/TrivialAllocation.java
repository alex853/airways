/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.activity;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.Pilot;
import net.simforge.airways.persistence.model.flight.Flight;
import net.simforge.airways.persistence.model.flight.PilotAssignment;
import net.simforge.airways.processes.flight.event.PilotAllocated;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class TrivialAllocation implements Activity {
    private static Logger logger = LoggerFactory.getLogger(TrivialAllocation.class);

    @Inject
    private Flight flight;
    @Inject
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Result act() {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, "TrivialAllocation.act", () -> {
                logger.debug("{} - Doing trivial allocation", flight);

                FlightContext flightContext = FlightContext.load(session, flight);

                // todo p1 aircraft allocation
/*                AircraftAssignment aircraftAssignment = cachedFlightContext.getAircraftAssignment();
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
                }*/

                PilotAssignment pilotAssignment = flightContext.getPilotAssignment();
                if (pilotAssignment == null) {
                    // todo nov17 airline check
                    // todo nov17 license check
                    Pilot pilot = (Pilot) session
                            .createQuery("from Pilot p where p.person.positionAirport = :fromAirport and p.status = :idle")
                            .setParameter("fromAirport", flight.getFromAirport())
                            .setInteger("idle", Pilot.Status.Idle)
                            .setMaxResults(1)
                            .uniqueResult();
                    if (pilot != null) {
                        pilot = session.load(Pilot.class, pilot.getId());

                        pilot.setStatus(Pilot.Status.IdlePlanned);
                        session.update(pilot);

                        pilotAssignment = new PilotAssignment();
                        pilotAssignment.setFlight(flight);
                        pilotAssignment.setPilot(pilot);
                        pilotAssignment.setStatus(PilotAssignment.Status.Assigned);

                        session.save(pilotAssignment);

                        session.save(EventLog.make(flight, "Pilot is allocated to flight", pilot));
                        session.save(EventLog.make(pilot, "Pilot is allocated to flight", flight));

                        logger.info("{} - Pilot {} allocated", flight, pilot);

                        engine.fireEvent(session, PilotAllocated.class, flight);
                    }
                }

/*                flightContext = FlightContext.load(flight);
                if (flightContext.isFullyAllocated()) {
                    logger.debug("Flight {} is fully assigned", _flight);

                    FlightEntity flight = session.load(FlightEntity.class, _flight.getId());

                    flight.setStatus(Flight.Status.Assigned);
                    flight.setHeartbeatDt(JavaTime.nowUtc().plusMinutes(1));
                    session.update(flight);

                    session.save(EventLog.make(flight, "Flight is fully assigned"));

                    logger.info("{} - FULLY ALLOCATED", flight);

                    // reset pilot heartbeat when pilot assignment changed
                    Pilot pilot = session.load(PilotEntity.class, flightContext.getPilot().getId());
                    pilot.setHeartbeatDt(JavaTime.nowUtc());
                }*/
            });
        }

        FlightContext flightContext;
        try (Session session = sessionFactory.openSession()) {
            flightContext = FlightContext.load(session, flight);
        }

        boolean isFullyAllocated = flightContext.isFullyAllocated();
        if (!isFullyAllocated) {
            return Result.ok(Result.NextRun.FewTimesPerHour);
        } else {
            return Result.ok(Result.NextRun.DoNotRun); // todo p3 done instead of expiry
        }
    }

    @Override
    public Result onExpiry() {
        return null;
    }
}
