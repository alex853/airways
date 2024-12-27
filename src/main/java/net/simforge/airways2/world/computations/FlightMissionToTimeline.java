package net.simforge.airways2.world.computations;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.storage.FlightMissions;

import java.time.LocalDateTime;

public class FlightMissionToTimeline {

    public static FlightTimeline byMission(final FlightMissions.Mission mission) {
        FlightTimeline timeline = FlightTimeline.byScheduledDepartureArrivalTime(
                Time.toLdt(mission.getPlannedDepartureTime()),
                Time.toLdt(mission.getPlannedArrivalTime()));

        final LocalDateTime actualDepartureTime = Time.toLdt(mission.getActualDepartureTime());
        if (actualDepartureTime != null) {
            timeline.getBlocksOff().setActualTime(actualDepartureTime);
        }

        final LocalDateTime actualTakeoffTime = Time.toLdt(mission.getActualTakeoffTime());
        if (actualTakeoffTime != null) {
            timeline.getTakeoff().setActualTime(actualTakeoffTime);
        }

        final LocalDateTime actualLandingTime = Time.toLdt(mission.getActualLandingTime());
        if (actualLandingTime != null) {
            timeline.getLanding().setActualTime(actualLandingTime);
        }

        final LocalDateTime actualArrivalTime = Time.toLdt(mission.getActualArrivalTime());
        if (actualArrivalTime != null) {
            timeline.getBlocksOn().setActualTime(actualArrivalTime);
        }

        return timeline;
    }
}
