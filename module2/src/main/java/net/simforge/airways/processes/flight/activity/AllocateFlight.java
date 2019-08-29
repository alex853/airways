/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.activity;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.processes.flight.event.Allocated;
import net.simforge.airways.processes.flight.event.Cancelled;

public class AllocateFlight implements Activity {
    private Flight flight;
    private Engine engine;

    @Override
    public Result act() {
        // todo allocation stuff

        boolean isAllocated = false;
        if (isAllocated) {
            engine.fireEvent(null/*Allocated.class*/, flight);
            return Result.ok();
        } else {
            return Result.ok();//ActivityResult.repeat(/*FewTimesPerDay*/);
        }
    }

    public Result afterExpired() {
        engine.fireEvent(null/*Cancelled.class*/, flight);
        return null;
    }
}
