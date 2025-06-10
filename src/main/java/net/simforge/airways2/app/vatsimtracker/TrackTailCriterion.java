package net.simforge.airways2.app.vatsimtracker;

import net.simforge.airways2.world.Time;
import net.simforge.commons.misc.Geo;
import net.simforge.networkview.core.Position;

public class TrackTailCriterion {
    private final double lastTrackedDistance;
    private final double lastTrackedDistanceTime;
    private final double lastTrackedDistanceSpeed;

    private final double distanceToNewPosition;
    private final double distanceToNewPositionTime;

    private final double maxAllowedDistanceToNewPosition;

    private final boolean сontinued;

    public TrackTailCriterion(final PilotContext pilotContext, final Position newPosition) {
        lastTrackedDistance = TrackLeg.distance(pilotContext.getTrackTail());
        lastTrackedDistanceTime = TrackLeg.time(pilotContext.getTrackTail());
        lastTrackedDistanceSpeed = lastTrackedDistance / lastTrackedDistanceTime;

        distanceToNewPosition = Geo.distance(pilotContext.getPositionCoords(), newPosition.getCoords());
        distanceToNewPositionTime = (double) pilotContext.getElapsedSecondsSinceLastSeen(newPosition.getReportInfo().getReport()) / (double) Time.ONE_HOUR;

        maxAllowedDistanceToNewPosition = distanceToNewPositionTime * lastTrackedDistanceSpeed * 1.5;

        сontinued = distanceToNewPosition < maxAllowedDistanceToNewPosition;
    }

    public boolean isСontinued() {
        return сontinued;
    }

    @Override
    public String toString() {
        return "TrackTailCriterion{" +
                "lastTrackedDistance=" + lastTrackedDistance +
                ", lastTrackedDistanceTime=" + lastTrackedDistanceTime +
                ", lastTrackedDistanceSpeed=" + lastTrackedDistanceSpeed +
                ", distanceToNewPosition=" + distanceToNewPosition +
                ", distanceToNewPositionTime=" + distanceToNewPositionTime +
                ", maxAllowedDistanceToNewPosition=" + maxAllowedDistanceToNewPosition +
                ", сontinued=" + сontinued +
                '}';
    }
}
