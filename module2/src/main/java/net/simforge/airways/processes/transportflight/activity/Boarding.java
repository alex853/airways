/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.activity;

import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.model.Journey;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * It boards passengers to the aircraft. Boarding started by BoardingStarted event which initiated by pilot's command.
 */
public class Boarding implements Activity {
    // todo p3 implement boarding


    @Override
    public Result act() {
//        Collection<Journey> journeys = JourneyOps.loadJourneysForFlight(session, transportFlight);
//
//        List<Journey> journeysToBoard = journeys.stream().filter(journey -> journey.getStatus() == Journey.Status.WaitingForBoarding).collect(Collectors.toList());
//        if (journeysToBoard.isEmpty()) {
//            return Result.done(); // todo p2 finish checkin!
//        }


        return null;
    }

    @Override
    public Result onExpiry() {
        return null;
    }

}
