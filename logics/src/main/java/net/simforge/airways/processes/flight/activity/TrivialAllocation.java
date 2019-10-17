/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.activity;

import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.Result;
import net.simforge.airways.processengine.activity.Activity;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.aircraft.Aircraft;
import net.simforge.airways.model.flight.AircraftAssignment;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.model.flight.PilotAssignment;
import net.simforge.airways.processes.flight.event.AircraftAllocated;
import net.simforge.airways.processes.flight.event.PilotAllocated;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static net.simforge.airways.processengine.Result.When.FewTimesPerHour;

public class TrivialAllocation implements Activity {
    private static Logger logger = LoggerFactory.getLogger(TrivialAllocation.class);

    @Inject
    private Flight flight;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Result act() {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, "TrivialAllocation.act", () -> {
                logger.debug("{} - Doing trivial allocation", flight);

                FlightContext flightContext = FlightContext.load(session, flight);

                AircraftAssignment aircraftAssignment = flightContext.getAircraftAssignment();
                if (aircraftAssignment == null) {
                    // todo nov17 airline check && aircraft.getAirline().getId().equals(_flight.get?????????)
                    Aircraft aircraft = (Aircraft) session
                            .createQuery("from Aircraft a" +
                                    " where a.type = :type" +
                                    " and a.locationAirport = :fromAirport" +
                                    " and a.status = :idle")
                            .setParameter("type", flight.getAircraftType())
                            .setParameter("fromAirport", flight.getFromAirport())
                            .setInteger("idle", Aircraft.Status.Idle)
                            .setMaxResults(1)
                            .uniqueResult();
                    if (aircraft != null) {
                        aircraft.setStatus(Aircraft.Status.IdlePlanned);
                        session.update(aircraft);

                        aircraftAssignment = new AircraftAssignment();
                        aircraftAssignment.setFlight(session.load(Flight.class, flight.getId()));
                        aircraftAssignment.setAircraft(aircraft);
                        aircraftAssignment.setStatus(AircraftAssignment.Status.Assigned);

                        session.save(aircraftAssignment);

                        session.save(EventLog.make(flight, "Aircraft is allocated to flight", aircraft));
                        session.save(EventLog.make(aircraft, "Aircraft is allocated to flight", flight));

                        logger.info("{} - Aircraft {} allocated", flight, aircraft.getRegNo());

                        engine.fireEvent(session, AircraftAllocated.class, flight);
                    }
                }

                PilotAssignment pilotAssignment = flightContext.getPilotAssignment();
                if (pilotAssignment == null) {
                    // todo nov17 airline check
                    // todo nov17 license check
                    Pilot pilot = (Pilot) session
                            .createQuery("from Pilot p " +
                                    "where p.person.locationAirport = :fromAirport" +
                                    " and p.status = :idle")
                            .setParameter("fromAirport", flight.getFromAirport())
                            .setInteger("idle", Pilot.Status.Idle)
                            .setMaxResults(1)
                            .uniqueResult();
                    if (pilot != null) {
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
            });
        }

        FlightContext flightContext;
        try (Session session = sessionFactory.openSession()) {
            flightContext = FlightContext.load(session, flight);
        }

        boolean isFullyAllocated = flightContext.isFullyAllocated();
        if (!isFullyAllocated) {
            return Result.resume(FewTimesPerHour);
        } else {
            return Result.done();
        }
    }

    @Override
    public Result onExpiry() {
        return Result.nothing();
    }
}
