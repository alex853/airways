package net.simforge.airways.processes.flight.event;

import net.simforge.airways.model.flight.AircraftAssignment;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.model.flight.PilotAssignment;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.ops.TransportFlightOps;
import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.processes.flight.activity.FlightContext;
import net.simforge.airways.processes.transportflight.activity.JourneyCancellation;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;

@Subscribe(CancelDueToNoAllocation.class)
public class CancelDueToNoAllocation implements Event, Handler {
    @Inject
    private Flight flight;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("CancelDueToNoAllocation.process");
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {

                flight = session.load(Flight.class, flight.getId());

                flight.setStatus(Flight.Status.Cancelled);
                session.update(flight);

                FlightContext flightContext = FlightContext.load(session, flight);

                AircraftAssignment aircraftAssignment = flightContext.getAircraftAssignment();
                if (aircraftAssignment != null) {
                    aircraftAssignment.setStatus(AircraftAssignment.Status.Cancelled);
                    session.update(aircraftAssignment);
                }

                PilotAssignment pilotAssignment = flightContext.getPilotAssignment();
                if (pilotAssignment != null) {
                    pilotAssignment.setStatus(PilotAssignment.Status.Cancelled);
                    session.update(pilotAssignment);
                }

                TransportFlight transportFlight = flight.getTransportFlight();
                if (transportFlight != null) {
                    TransportFlightOps.checkAndSetStatus(transportFlight, TransportFlight.Status.Cancelled);
                    session.update(transportFlight);

                    scheduling.startActivity(session, JourneyCancellation.class, flight.getTransportFlight());
                }

            });
        } finally {
            BM.stop();
        }
    }
}
