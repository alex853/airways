/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.event;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.event.Event;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.activity.Deboarding;

import javax.inject.Inject;

@Subscribe(DeboardingStarted.class)
public class DeboardingStarted implements Event, Handler {
    @Inject
    private TransportFlight transportFlight;
    @Inject
    private Engine engine;

    @Override
    public void process() {
        engine.startActivity(Deboarding.class, transportFlight);
    }
}
