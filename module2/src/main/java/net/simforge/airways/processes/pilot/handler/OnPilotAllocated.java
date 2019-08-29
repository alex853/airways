package net.simforge.airways.processes.pilot.handler;

import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.model.person.Pilot;
import net.simforge.airways.engine.Events;
import net.simforge.airways.processes.pilot.event.PilotCheckin;

import java.time.LocalDateTime;

/**
 * Created by Alexey on 17.07.2018.
 */
public class OnPilotAllocated {
    public void process(Pilot pilot) {
        Flight flight = null;
        LocalDateTime pilotChecksInAt = flight.getScheduledDepartureTime().minusMinutes(120);

        Events.schedule(PilotCheckin.class, null/*pilot*/);
    }
}
