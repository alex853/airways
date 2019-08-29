/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.handler;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.event.CheckinOpens;
import net.simforge.airways.processes.transportflight.event.Scheduled;

import javax.inject.Inject;
import java.time.LocalDateTime;

/**
 * It schedules beginning of check-in.
 */
@Subscribe(Scheduled.class)
public class OnScheduled implements Handler {
    @Inject
    private TransportFlight transportFlight;
    @Inject
    private Engine engine;

    @Override
    public void process() {
        LocalDateTime checkinOpensAt = transportFlight.getDepartureDt().minusMinutes(90); // checkin opens 90 mins before departure

        engine.scheduleEvent(CheckinOpens.class, transportFlight, checkinOpensAt);
    }
}
