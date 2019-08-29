package net.simforge.airways.processes.flight.handler;

import net.simforge.airways.engine.proto.Handler;
import net.simforge.airways.engine.proto.Subscribe;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.processes.flight.event.Allocated;

@Subscribe(Allocated.class)
public class OnAllocated implements Handler<Flight> {
    @Override
    public void process(Flight flight) {
        //Pilot pilot = get pilot for flight;

    }
}
