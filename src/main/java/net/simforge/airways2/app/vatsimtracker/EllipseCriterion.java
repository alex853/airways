package net.simforge.airways2.app.vatsimtracker;

import net.simforge.commons.misc.Geo;
import net.simforge.networkview.core.Position;

import java.text.DecimalFormat;

public class EllipseCriterion {
    private static final DecimalFormat df1 = new DecimalFormat("#.#");
    private final double distanceBetweenAirports;
    private final double airportRange = 50;
    private final double c;
    private final double a;
    private final double b;
    private final double sumOfDistancesToPosition;
    private final boolean withinEllipse;

    public EllipseCriterion(final Geo.Coords departureCoords, final Geo.Coords destinationCoords, final Position newPosition) {
        final Geo.Coords positionCoords = newPosition.getCoords();

        distanceBetweenAirports = Geo.distance(departureCoords, destinationCoords);

        // https://ru.wikipedia.org/wiki/%D0%AD%D0%BB%D0%BB%D0%B8%D0%BF%D1%81
        c = distanceBetweenAirports / 2;
        a = c + airportRange;
        b = Math.sqrt(a * a - c * c);

        sumOfDistancesToPosition = Geo.distance(departureCoords, positionCoords) + Geo.distance(positionCoords, destinationCoords);

        withinEllipse = sumOfDistancesToPosition <= 2 * a;
    }

    public boolean isWithinEllipse() {
        return withinEllipse;
    }

    @Override
    public String toString() {
        return "EllipseCriterion{" +
                "distanceBetweenAirports=" + df1.format(distanceBetweenAirports) +
                ", airportRange=" + df1.format(airportRange) +
                ", c=" + df1.format(c) +
                ", a(half-length)=" + df1.format(a) +
                ", b(half-width)=" + df1.format(b) +
                ", sumOfDistancesToPosition=" + sumOfDistancesToPosition +
                ", withinEllipse(sum<=2*a)=" + withinEllipse +
                '}';
    }
}
