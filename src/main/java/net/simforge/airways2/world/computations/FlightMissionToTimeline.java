package net.simforge.airways2.world.computations;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.FlightMissions;

import java.time.LocalDateTime;

public class FlightMissionToTimeline {

    public static FlightTimeline byMission(final FlightMissions.Mission mission) {
        FlightTimeline timeline = FlightTimeline.byScheduledDepartureArrivalTime(
                Time.toLdt(mission.getPlannedDepartureTime()),
                Time.toLdt(mission.getPlannedArrivalTime()));

        final LocalDateTime actualDepartureTime = Time.toLdtOrNull(mission.getActualDepartureTime());
        if (actualDepartureTime != null) {
            timeline.getBlocksOff().setActualTime(actualDepartureTime);
        }

        final LocalDateTime actualTakeoffTime = Time.toLdtOrNull(mission.getActualTakeoffTime());
        if (actualTakeoffTime != null) {
            timeline.getTakeoff().setActualTime(actualTakeoffTime);
        }

        final LocalDateTime actualLandingTime = Time.toLdtOrNull(mission.getActualLandingTime());
        if (actualLandingTime != null) {
            timeline.getLanding().setActualTime(actualLandingTime);
        }

        final LocalDateTime actualArrivalTime = Time.toLdtOrNull(mission.getActualArrivalTime());
        if (actualArrivalTime != null) {
            timeline.getBlocksOn().setActualTime(actualArrivalTime);
        }

        return timeline;
    }
}
