/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.event;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.event.Event;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.persistence.model.flight.Flight;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.event.BoardingStarted;

import javax.inject.Inject;

@Subscribe(StartBoardingCommand.class)
public class StartBoardingCommand implements Event, Handler {
    @Inject
    private Flight flight;
    @Inject
    private Engine engine;

    @Override
    public void process() {
        TransportFlight transportFlight = flight.getTransportFlight();
        if (transportFlight == null) {
            return;
        }
        engine.fireEvent(BoardingStarted.class, transportFlight);
    }
}
