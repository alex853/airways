package net.simforge.airways2.world.computations;

import net.simforge.airways2.world.datamodel.FlightMissions;

import java.time.LocalDateTime;

public class FlightMissionToTimeline {

    public static FlightTimeline byMission(final FlightMissions.Mission mission) {
        FlightTimeline timeline = FlightTimeline.byScheduledDepartureArrivalTime(
                mission.getPlannedDepartureLdt(),
                mission.getPlannedArrivalLdt());

        final LocalDateTime actualDepartureTime = mission.getActualDepartureLdt();
        if (actualDepartureTime != null) {
            timeline.getBlocksOff().setActualTime(actualDepartureTime);
        }

        final LocalDateTime actualTakeoffTime = mission.getActualTakeoffLdt();
        if (actualTakeoffTime != null) {
            timeline.getTakeoff().setActualTime(actualTakeoffTime);
        }

        final LocalDateTime actualLandingTime = mission.getActualLandingLdt();
        if (actualLandingTime != null) {
            timeline.getLanding().setActualTime(actualLandingTime);
        }

        final LocalDateTime actualArrivalTime = mission.getActualArrivalLdt();
        if (actualArrivalTime != null) {
            timeline.getBlocksOn().setActualTime(actualArrivalTime);
        }

        return timeline;
    }
}
