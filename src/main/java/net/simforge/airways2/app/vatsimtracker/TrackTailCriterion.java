package net.simforge.airways2.app.vatsimtracker;

import net.simforge.airways2.world.Time;
import net.simforge.commons.misc.Geo;
import net.simforge.networkview.core.Position;

import java.text.DecimalFormat;

public class TrackTailCriterion {
    private static final DecimalFormat df3 = new DecimalFormat("#.###");
    private final double lastTrackedDistance;
    private final double lastTrackedDistanceTime;
    private final double lastTrackedDistanceSpeed;

    private final double distanceToNewPosition;
    private final double distanceToNewPositionTime;

    private final double maxAllowedDistanceToNewPosition;

    private final boolean continued;

    public TrackTailCriterion(final PilotContext pilotContext, final Position newPosition) {
        lastTrackedDistance = TrackLeg.distance(pilotContext.getTrackTail());
        lastTrackedDistanceTime = TrackLeg.time(pilotContext.getTrackTail());
        lastTrackedDistanceSpeed = lastTrackedDistance / lastTrackedDistanceTime;

        distanceToNewPosition = Geo.distance(pilotContext.getPositionCoords(), newPosition.getCoords());
        distanceToNewPositionTime = (double) pilotContext.getElapsedSecondsSinceLastSeen(newPosition.getReportInfo().getReport()) / (double) Time.ONE_HOUR;

        maxAllowedDistanceToNewPosition = distanceToNewPositionTime * lastTrackedDistanceSpeed * 1.5;

        continued = distanceToNewPosition < maxAllowedDistanceToNewPosition;
    }

    public boolean isContinued() {
        return continued;
    }

    @Override
    public String toString() {
        return "TrackTailCriterion{" +
                "lastTrackedDistance=" + df3.format(lastTrackedDistance) +
                ", lastTrackedDistanceTime=" + df3.format(lastTrackedDistanceTime) +
                ", lastTrackedDistanceSpeed=" + df3.format(lastTrackedDistanceSpeed) +
                ", distanceToNewPosition=" + df3.format(distanceToNewPosition) +
                ", distanceToNewPositionTime=" + df3.format(distanceToNewPositionTime) +
                ", maxAllowedDistanceToNewPosition=" + df3.format(maxAllowedDistanceToNewPosition) +
                ", continued=" + continued +
                '}';
    }
}
