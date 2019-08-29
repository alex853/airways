package net.simforge.airways.processes.transportflight.handler;

import net.simforge.airways.engine.proto.Activities;
import net.simforge.airways.engine.proto.Handler;
import net.simforge.airways.engine.proto.Subscribe;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.engine.*;
import net.simforge.airways.processes.transportflight.activity.Checkin;
import net.simforge.airways.processes.transportflight.event.CheckinClosed;
import net.simforge.airways.processes.transportflight.event.CheckinOpens;

import java.time.LocalDateTime;

@Subscribe(CheckinOpens.class)
public class OnCheckinOpens implements Handler<TransportFlight> {
    public void process(TransportFlight transportFlight) {
        Events.schedule(CheckinClosed.class, transportFlight, LocalDateTime.now().plusMinutes(90));
        Activities.start(Checkin.class, transportFlight);
    }
}
