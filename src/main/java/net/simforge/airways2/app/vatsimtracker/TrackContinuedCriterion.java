package net.simforge.airways2.app.vatsimtracker;

import net.simforge.airways2.world.Time;
import net.simforge.commons.misc.Geo;
import net.simforge.networkview.core.Position;

public class TrackContinuedCriterion {
    private final double lastTrackedDistance;
    private final double lastTrackedDistanceTime;
    private final double lastTrackedDistanceSpeed;

    private final double distanceToNextPosition;
    private final double distanceToNextPositionTime;

    private final double approximatedDistanceForNextPosition;

    private final double ratio;
    private final boolean сontinued;

    public TrackContinuedCriterion(final PilotContext pilotContext, final Position newPosition) {
        lastTrackedDistance = TrackLeg.distance(pilotContext.getTrackTail());
        lastTrackedDistanceTime = TrackLeg.time(pilotContext.getTrackTail());
        lastTrackedDistanceSpeed = lastTrackedDistance / lastTrackedDistanceTime;

        distanceToNextPosition = Geo.distance(pilotContext.getPositionCoords(), newPosition.getCoords());
        distanceToNextPositionTime = (double) pilotContext.getElapsedSecondsSinceLastSeen(newPosition.getReportInfo().getReport()) / (double) Time.ONE_HOUR;

        approximatedDistanceForNextPosition = distanceToNextPositionTime * lastTrackedDistanceSpeed;

        ratio = approximatedDistanceForNextPosition / distanceToNextPosition;

        сontinued = (ratio <= 1.5);
    }

    public boolean isСontinued() {
        return сontinued;
    }

    @Override
    public String toString() {
        return "TrackContinuedCriterion{" +
                "lastTrackedDistance=" + lastTrackedDistance +
                ", lastTrackedDistanceTime=" + lastTrackedDistanceTime +
                ", lastTrackedDistanceSpeed=" + lastTrackedDistanceSpeed +
                ", distanceToNextPosition=" + distanceToNextPosition +
                ", distanceToNextPositionTime=" + distanceToNextPositionTime +
                ", approximatedDistanceForNextPosition=" + approximatedDistanceForNextPosition +
                ", ratio=" + ratio +
                ", trackContinued=" + сontinued +
                '}';
    }
}
