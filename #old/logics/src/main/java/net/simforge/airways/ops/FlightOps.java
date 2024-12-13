/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ops;

public class FlightOps {
    /* old code public void cancelFlight(Flight _flight, String reason) {
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
    }*/
}
