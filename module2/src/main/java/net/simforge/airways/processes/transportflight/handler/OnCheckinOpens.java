/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.handler;

import net.simforge.airways.engine.proto.Activities;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.engine.*;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.activity.Checkin;
import net.simforge.airways.processes.transportflight.event.CheckinClosed;
import net.simforge.airways.processes.transportflight.event.CheckinOpens;

import javax.inject.Inject;

@Subscribe(CheckinOpens.class)
public class OnCheckinOpens implements Handler {

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private Engine engine;

    public void process() {
        Activities.start(Checkin.class, transportFlight);
        Events.schedule(CheckinClosed.class, transportFlight, transportFlight.getDepartureDt().minusMinutes(40)); // checkin ends 40 mins before departure
    }
}
