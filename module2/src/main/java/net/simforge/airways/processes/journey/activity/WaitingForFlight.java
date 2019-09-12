/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.activity;

import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;

public class WaitingForFlight implements Activity {
    @Override
    public Result act() {
        return Result.done();
    }

    @Override
    public Result onExpiry() {
        return Result.nothing();
    }
}
