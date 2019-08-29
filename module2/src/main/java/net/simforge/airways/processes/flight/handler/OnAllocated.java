/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.handler;

import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.processes.flight.event.Allocated;

@Subscribe(Allocated.class)
public class OnAllocated implements Handler {
    @Override
    public void process() {
        //Pilot pilot = get pilot for flight;

    }
}
