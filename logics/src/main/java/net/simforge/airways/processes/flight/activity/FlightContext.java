/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.activity;

import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.aircraft.Aircraft;
import net.simforge.airways.model.flight.AircraftAssignment;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.model.flight.PilotAssignment;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;

public class FlightContext {
    private Flight flight;
    private Pilot pilot;
    private PilotAssignment pilotAssignment;
    private Aircraft aircraft;
    private AircraftAssignment aircraftAssignment;

    public static FlightContext load(Session session, Flight flight) {
        BM.start("FlightContext.load#direct");
        try {
            FlightContext ctx = new FlightContext();

            ctx.flight = session.load(Flight.class, flight.getId());

            ctx.aircraftAssignment = (AircraftAssignment) session
                    .createQuery("select aa " +
                            "from AircraftAssignment as aa " +
                            "where aa.flight = :flight" +
                            "  and aa.status != :cancelled")
                    .setEntity("flight", flight)
                    .setInteger("cancelled", AircraftAssignment.Status.Cancelled)
                    .setMaxResults(1)
                    .uniqueResult();
            if (ctx.aircraftAssignment != null) {
                ctx.aircraft = ctx.aircraftAssignment.getAircraft();
            }

            ctx.pilotAssignment = (PilotAssignment) session
                    .createQuery("select pa " +
                            "from PilotAssignment as pa " +
                            "where pa.flight = :flight" +
                            "  and pa.status != :cancelled")
                    .setEntity("flight", flight)
                    .setInteger("cancelled", PilotAssignment.Status.Cancelled)
                    .setMaxResults(1)
                    .uniqueResult();
            if (ctx.pilotAssignment != null) {
                ctx.pilot = ctx.pilotAssignment.getPilot();
            }

            return ctx;
        } finally {
            BM.stop();
        }
    }

    public Flight getFlight() {
        return flight;
    }

    public Pilot getPilot() {
        return pilot;
    }

    public PilotAssignment getPilotAssignment() {
        return pilotAssignment;
    }

    public Aircraft getAircraft() {
        return aircraft;
    }

    public AircraftAssignment getAircraftAssignment() {
        return aircraftAssignment;
    }

    public boolean isFullyAllocated() {
        return pilotAssignment != null && aircraftAssignment != null;
    }
}
