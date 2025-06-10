package net.simforge.airways2.app.vatsimtracker;

import java.util.LinkedList;
import java.util.Queue;

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

    public static double distance(final Queue<TrackLeg> trackTail) {
        return trackTail.stream().map(TrackLeg::getDistance).reduce(0.0, Double::sum);
    }

    public static double time(final Queue<TrackLeg> trackTail) {
        return trackTail.stream().map(TrackLeg::getTime).reduce(0.0, Double::sum);
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
