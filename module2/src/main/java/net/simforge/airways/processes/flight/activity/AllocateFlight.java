/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.activity;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.persistence.model.flight.Flight;
import net.simforge.airways.processes.flight.event.Allocated;
import net.simforge.airways.processes.flight.event.Cancelled;

import javax.inject.Inject;

/**
 * todo p3 description
 */
public class AllocateFlight implements Activity {
    @Inject
    private Flight flight;
    @Inject
    private Engine engine;

    @Override
    public Result act() {
        // todo p2 allocation stuff


        // todo p2 if pilot allocated - fire PilotAllocated event, all other flight-related things will happen from pilot's control

        boolean isAllocated = false;
        if (isAllocated) {
            engine.fireEvent(Allocated.class, flight);
            return Result.ok();
        } else {
            return Result.ok(Result.NextRun.FewTimesPerDay);
        }
    }

    public Result afterExpired() {
        // todo p2 message unable to allocate
        engine.fireEvent(Cancelled.class, flight);
        return null;
    }
}
