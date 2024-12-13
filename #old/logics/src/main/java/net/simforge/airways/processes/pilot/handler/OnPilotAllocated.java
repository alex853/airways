package net.simforge.airways.processes.pilot.handler;

import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.processes.flight.activity.FlightContext;
import net.simforge.airways.processes.flight.event.PilotAllocated;
import net.simforge.airways.processes.pilot.event.PilotCheckin;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;
import java.time.LocalDateTime;

@SuppressWarnings("unused")
@Subscribe(PilotAllocated.class) // used via reflection
public class OnPilotAllocated implements Handler {
    @Inject
    private Flight flight;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        try (Session session = sessionFactory.openSession()) {
            FlightContext flightContext = FlightContext.load(session, flight);
            Pilot pilot = flightContext.getPilot();

            LocalDateTime pilotChecksInAt = flight.getScheduledDepartureTime().minusMinutes(120);

            scheduling.scheduleEvent(PilotCheckin.class, pilot, pilotChecksInAt);
        }
    }
}
