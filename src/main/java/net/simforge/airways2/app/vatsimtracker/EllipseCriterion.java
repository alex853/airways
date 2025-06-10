package net.simforge.airways2.app.vatsimtracker;

import net.simforge.commons.misc.Geo;
import net.simforge.networkview.core.Position;

public class EllipseCriterion {

    private final double dist;
    private final double c;
    private final double a;
    private final double b;
    private final double summedDist;
    private final boolean withinEllipse;

    public EllipseCriterion(final Geo.Coords departureCoords, final Geo.Coords destinationCoords, final Position newPosition) {
        final Geo.Coords positionCoords = newPosition.getCoords();

        dist = Geo.distance(departureCoords, destinationCoords);

        // https://ru.wikipedia.org/wiki/%D0%AD%D0%BB%D0%BB%D0%B8%D0%BF%D1%81
        c = dist / 2;
        a = c + 100; // 100 nm
        b = Math.sqrt(a * a - c * c);

        summedDist = Geo.distance(departureCoords, positionCoords) + Geo.distance(positionCoords, destinationCoords);

        withinEllipse = summedDist <= 2 * a;
    }

    public boolean isWithinEllipse() {
        return withinEllipse;
    }

    @Override
    public String toString() {
        return "EllipseCriterion{" +
                "dist=" + dist +
                ", c=" + c +
                ", a=" + a +
                ", b=" + b +
                ", summedDist=" + summedDist +
                ", withinEllipse=" + withinEllipse +
                '}';
    }
}
