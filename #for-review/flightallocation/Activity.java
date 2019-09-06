/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage2.flightallocation;

import net.simforge.airways.stage2.model.geo.Airport;

import java.time.LocalDateTime;

public class Activity {
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private State startState;
    private State finishState;

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public State getStartState() {
        return startState;
    }

    public State getFinishState() {
        return finishState;
    }

    public boolean isIntersecting(Activity activity) {
        boolean myStartTimeInside = isInside(activity, startTime);
        boolean myFinishTimeInside = isInside(activity, finishTime);

        boolean itsStartTimeInside = isInside(this, activity.getStartTime());
        boolean itsFinishTimeInside = isInside(this, activity.getFinishTime());

        return myStartTimeInside || myFinishTimeInside || itsStartTimeInside || itsFinishTimeInside;
    }

    private boolean isInside(Activity activity, LocalDateTime time) {
        return activity.getStartTime().isBefore(time) && activity.getFinishTime().isAfter(time);
    }

    @Override
    public String toString() {
        return "Activity{ " + startState + " -> " + finishState +" }";
    }

    public static Activity forFlight(Airport fromAirport, LocalDateTime startTime, Airport toAirport, LocalDateTime finishTime) {
        Activity activity = new Activity();

        activity.startTime = startTime;
        activity.startState = new InAirportState(fromAirport);

        activity.finishTime = finishTime;
        activity.finishState = new InAirportState(toAirport);

        return activity;
    }
}
