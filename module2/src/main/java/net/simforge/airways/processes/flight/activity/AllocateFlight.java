package net.simforge.airways.processes.flight.activity;

import net.simforge.airways.engine.proto.Activity;
import net.simforge.airways.engine.proto.ActivityResult;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.engine.Events;
import net.simforge.airways.processes.flight.event.Allocated;
import net.simforge.airways.processes.flight.event.Cancelled;

/**
 * Created by Alexey on 17.07.2018.
 */
public class AllocateFlight implements Activity<Flight> {
    @Override
    public ActivityResult act(Flight flight) {
        // todo allocation stuff

        boolean isAllocated = false;
        if (isAllocated) {
            Events.fire(Allocated.class, flight);
            return ActivityResult.done();
        } else {
            return ActivityResult.repeat(/*FewTimesPerDay*/);
        }
    }

    public void onExpired(Flight flight) {
        Events.fire(Cancelled.class, flight);
    }
}
