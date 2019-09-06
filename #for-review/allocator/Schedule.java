/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage1.allocator;

import java.util.ArrayList;
import java.util.List;

public class Schedule {
    private State currentState;
    private List<Activity> activities = new ArrayList<>();

    public boolean isActivitySuitable(Activity activity) {
        Activity previousActivity = null;
        Activity nextActivity = null;
        for (Activity each : activities) {
            if (each.isIntersecting(activity)) {
                return false;
            }

            if (each.getFinishTime().isBefore(activity.getStartTime())) {
                if (previousActivity != null) {
                    if (previousActivity.getFinishTime().isBefore(each.getStartTime())) {
                        previousActivity = each;
                    }
                } else {
                    previousActivity = each;
                }
            } else if (each.getStartTime().isAfter(activity.getFinishTime())) {
                if (nextActivity != null) {
                    if (nextActivity.getStartTime().isAfter(each.getStartTime())) {
                        nextActivity = each;
                    }
                } else {
                    nextActivity = each;
                }
            }
        }

        if (previousActivity == null && nextActivity == null) {
            return currentState.isCompatibleWith(activity.getStartState());
        } else {
            if (previousActivity != null) {
                if (!previousActivity.getFinishState().isCompatibleWith(activity.getStartState())) {
                    return false;
                }
            } else {
                return currentState.isCompatibleWith(activity.getStartState());
            }

            if (nextActivity != null) {
                if (!nextActivity.getStartState().isCompatibleWith(activity.getFinishState())) {
                    return false;
                }
            }

            return true;
        }
    }

    public void setCurrentState(State currentState) {
        this.currentState = currentState;
    }

    public void add(Activity activity) {
        activities.add(activity);
    }
}
