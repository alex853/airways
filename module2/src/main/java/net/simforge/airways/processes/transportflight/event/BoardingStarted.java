/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.event;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.event.Event;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.activity.Boarding;

import javax.inject.Inject;

/**
 * This event is sourced by Pilot. TransportFlight reacts and does its job - board passengers.
 */
@Subscribe(BoardingStarted.class)
public class BoardingStarted implements Event, Handler {
    @Inject
    private TransportFlight transportFlight;
    @Inject
    private Engine engine;

    @Override
    public void process() {
        engine.startActivity(Boarding.class, transportFlight);
    }
}
