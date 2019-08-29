package net.simforge.airways.processes.transportflight.handler;

import net.simforge.airways.engine.proto.Activities;
import net.simforge.airways.engine.proto.ActivityStatus;
import net.simforge.airways.engine.proto.Subscribe;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.activity.Checkin;
import net.simforge.airways.processes.transportflight.event.CheckinClosed;

@Subscribe(CheckinClosed.class)
public class OnCheckinClosed {
    public void process() {
        TransportFlight transportFlight = null;
        ActivityStatus checkinStatus = Activities.findStatus(Checkin.class, transportFlight);
        if (!checkinStatus.isDone())
            Activities.stop(checkinStatus);
    }
}
