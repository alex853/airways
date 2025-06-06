package net.simforge.airways2.world.computations;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.FlightMissions;

import java.time.LocalDateTime;

public class FlightMissionToTimeline {

    public static FlightTimeline byMission(final FlightMissions.Mission mission) {
        FlightTimeline timeline = FlightTimeline.byScheduledDepartureArrivalTime(
                Time.toLdt(mission.getPlannedDepartureWorldTime()),
                Time.toLdt(mission.getPlannedArrivalWorldTime()));

        final LocalDateTime actualDepartureTime = Time.toLdtOrNull(mission.getActualDepartureWorldTime());
        if (actualDepartureTime != null) {
            timeline.getBlocksOff().setActualTime(actualDepartureTime);
        }

        final LocalDateTime actualTakeoffTime = Time.toLdtOrNull(mission.getActualTakeoffWorldTime());
        if (actualTakeoffTime != null) {
            timeline.getTakeoff().setActualTime(actualTakeoffTime);
        }

        final LocalDateTime actualLandingTime = Time.toLdtOrNull(mission.getActualLandingWorldTime());
        if (actualLandingTime != null) {
            timeline.getLanding().setActualTime(actualLandingTime);
        }

        final LocalDateTime actualArrivalTime = Time.toLdtOrNull(mission.getActualArrivalWorldTime());
        if (actualArrivalTime != null) {
            timeline.getBlocksOn().setActualTime(actualArrivalTime);
        }

        return timeline;
    }
}
