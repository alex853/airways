package net.simforge.airways.processes.flight.handler;

import net.simforge.airways.engine.proto.Activities;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.processes.flight.activity.AllocateFlight;

/**
 * Created by Alexey on 17.07.2018.
 */
public class OnPlanned {
    public void process() {
        Flight flight = null;
        Activities.startWithExpiration(AllocateFlight.class, flight, flight.getScheduledDepartureTime().minusMinutes(60));
    }
}
