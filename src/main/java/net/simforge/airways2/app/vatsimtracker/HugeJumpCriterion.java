package net.simforge.airways2.app.vatsimtracker;

import net.simforge.commons.misc.Geo;
import net.simforge.networkview.core.Position;

public class HugeJumpCriterion {
    private final double distanceToNextPosition;
    private final boolean detected;

    public HugeJumpCriterion(final PilotContext pilotContext, final Position newPosition) {
        final Geo.Coords positionCoords = pilotContext.getPositionCoords();

        if (positionCoords.getLat() == 0 && positionCoords.getLon() == 0) {
            distanceToNextPosition = 99999;
            detected = false;
            return;
        } else {
            distanceToNextPosition = Geo.distance(pilotContext.getPositionCoords(), newPosition.getCoords());
            detected = distanceToNextPosition > 50;
        }
    }

    public boolean isDetected() {
        return detected;
    }

    @Override
    public String toString() {
        return "HugeJumpCriterion{" +
                "distanceToNextPosition=" + distanceToNextPosition +
                ", detected=" + detected +
                '}';
    }
}
