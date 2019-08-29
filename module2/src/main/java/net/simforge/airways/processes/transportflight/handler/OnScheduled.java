package net.simforge.airways.processes.transportflight.handler;

import net.simforge.airways.model.flight.TransportFlight;
//import net.simforge.airways.engine.Events;
import net.simforge.airways.engine.proto.Handler;
import net.simforge.airways.engine.proto.Subscribe;
import net.simforge.airways.processes.transportflight.event.CheckinOpens;
import net.simforge.airways.processes.transportflight.event.Scheduled;

import java.time.LocalDateTime;

@Subscribe(Scheduled.class)
public class OnScheduled implements Handler<TransportFlight> {
    @Override
    public void process(TransportFlight transportFlight) {
        LocalDateTime checkinOpensAt = null;

        //Events.schedule(CheckinOpens.class, transportFlight, checkinOpensAt);
    }
}
