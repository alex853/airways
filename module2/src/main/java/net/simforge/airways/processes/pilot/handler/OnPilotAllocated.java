/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.pilot.handler;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.persistence.model.Pilot;
import net.simforge.airways.persistence.model.flight.Flight;

import javax.inject.Inject;
import java.time.LocalDateTime;

/**
 * Created by Alexey on 17.07.2018.
 */
public class OnPilotAllocated {

    @Inject
    private Engine engine;

    public void process(Pilot pilot) {
        Flight flight = null;
        LocalDateTime pilotChecksInAt = flight.getScheduledDepartureTime().minusMinutes(120);

        engine.scheduleEvent(null/*PilotCheckin.class*/, null/*pilot*/, pilotChecksInAt);
    }
}
