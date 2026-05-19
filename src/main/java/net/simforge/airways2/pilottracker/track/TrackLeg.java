package net.simforge.airways2.pilottracker.track;

import net.simforge.commons.misc.Geo;

import java.util.*;

public class TrackLeg {
    private final double distance;
    private final double time;

    public TrackLeg(final double distance, final double time) {
        this.distance = distance;
        this.time = time;
    }

    public static Queue<TrackLeg> add(final Queue<TrackLeg> trackTail, final double distance, final double time) {
        final Queue<TrackLeg> newTrackTail = new LinkedList<>(trackTail);
        newTrackTail.add(new TrackLeg(distance, time));
        while (newTrackTail.size() > 3) {
            newTrackTail.poll();
        }
        return newTrackTail;
    }

    public static double distance(final Collection<TrackLeg> trackTail) {
        return trackTail.stream().map(TrackLeg::getDistance).reduce(0.0, Double::sum);
    }

    public static double time(final Collection<TrackLeg> trackTail) {
        return trackTail.stream().map(TrackLeg::getTime).reduce(0.0, Double::sum);
    }

    public static TrackLeg between(TrackPosition p1, TrackPosition p2) {
        double distanceNm = Geo.distance(p1.getCoords(), p2.getCoords());
        double timeSecs = (p2.getTime() - p1.getTime()) / 1000.0;
        return new TrackLeg(distanceNm, timeSecs);
    }

    public static List<TrackLeg> buildNewTrackTrail(List<TrackLeg> oldTrackTrail, TrackPosition oldPosition, TrackPosition newPosition) {
        TrackLeg trackLeg = oldPosition != null ? between(oldPosition, newPosition) : null;
        List<TrackLeg> newTrackTrail = null;
        if (trackLeg != null) {
            if (oldTrackTrail == null) {
                newTrackTrail = new ArrayList<>();
            }
            newTrackTrail.add(trackLeg);
        }

        if (newTrackTrail != null) {
            while (time(newTrackTrail) > 1.0) {
                if (newTrackTrail.size() < 3) {
                    break;
                }
                newTrackTrail.remove(0);
            }
        }

        return newTrackTrail;
    }

    public static float calculateGroundspeed(List<TrackLeg> trackTrail) {
        if (trackTrail == null || trackTrail.isEmpty()) {
            return 0;
        }

        double distanceNm = distance(trackTrail);
        double timeSecs = time(trackTrail);
        double timeHours = timeSecs / 3600;
        return timeSecs > 0.0000001 ? (float) (distanceNm / timeHours) : 0;
    }

    public double getDistance() {
        return distance;
    }

    public double getTime() {
        return time;
    }

    public String toString() {
        return distance + "|" + time;
    }

    public static TrackLeg fromString(final String s) {
        final String[] ss = s.split("\\|");
        return new TrackLeg(
                Double.parseDouble(ss[0]),
                Double.parseDouble(ss[1]));
    }
}
