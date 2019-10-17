/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.event;

import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.event.DeboardingStarted;

import javax.inject.Inject;

@Subscribe(StartDeboardingCommand.class)
public class StartDeboardingCommand implements Event, Handler {
    @Inject
    private Flight flight;
    @Inject
    private ProcessEngine engine;

    @Override
    public void process() {
        TransportFlight transportFlight = flight.getTransportFlight();
        if (transportFlight == null) {
            return;
        }
        engine.fireEvent(DeboardingStarted.class, transportFlight);
    }
}
