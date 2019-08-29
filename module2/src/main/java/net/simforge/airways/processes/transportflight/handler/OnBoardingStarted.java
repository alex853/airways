/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.handler;

import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.processes.transportflight.event.BoardingStarted;

/**
 * It starts boarding process.
 */
@Subscribe(BoardingStarted.class)
public class OnBoardingStarted implements Handler {
    @Override
    public void process() {
        // todo p3 implement boarding
        throw new UnsupportedOperationException();
    }
}
