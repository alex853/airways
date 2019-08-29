/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.handler;

import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.persistence.model.flight.Flight;
import net.simforge.airways.processes.flight.event.Cancelled;

/**
 *
 */
@Subscribe(Cancelled.class)
public class OnCancelled implements Handler {
    public void process() {
        Flight flight = null;

        // todo cancel assignments

        // todo cancel transport flight
    }
}
