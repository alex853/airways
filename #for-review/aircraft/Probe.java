/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.legacy.airways.aircraft;

import forge.commons.db.DB;
import forge.commons.TimeMS;

import java.sql.Connection;
import java.sql.SQLException;

import net.simforge.commons.persistence.Persistence;
import org.joda.time.Duration;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;

public class Probe {
    private static final int DEP_SEGMENT = 50;
    private static final int ARR_SEGMENT = 50;
    private static final double DEP_SPEED = 0.6;
    private static final double ARR_SPEED = 0.4;

    public static void main(String[] args) throws SQLException {
        Connection connx = DB.getConnection();

        Model model = Persistence.load(connx, Model.class, 1);
        ModelPayloadToRange modelPayloadToRange = Persistence.load(connx, ModelPayloadToRange.class, 1);

        connx.close();

        for (int range = 100; range <= 5000; range += 100) {
            Duration estimatedDuration = estimateDuration(model, range);

            System.out.println(
                    "Range " + range +
                            "   max seats " + getMaxSeatingCapacityForRange(range, model, modelPayloadToRange) +
                            "   duration " + DateTimeFormat.forPattern("HH:mm").withZone(DateTimeZone.UTC).print(estimatedDuration.getMillis()));
        }
    }

    private static Duration estimateDuration(Model model, int range) {
        int dep = DEP_SEGMENT;
        int arr = ARR_SEGMENT;
        if (range < DEP_SEGMENT + ARR_SEGMENT) {
            dep = range / 2;
            arr = range - dep;
            range = 0;
        } else {
            range = range - dep - arr;
        }

        double depDur = dep / (model.getMaxCruisingSpeed() * DEP_SPEED);
        double arrDur = arr / (model.getMaxCruisingSpeed() * ARR_SPEED);
        double rangeDur = range / (double)(model.getMaxCruisingSpeed());

        double dur = depDur + rangeDur + arrDur;

        return new Duration((long)(dur * TimeMS.HOUR));
    }

    private static int getMaxSeatingCapacityForRange(int range, Model model, ModelPayloadToRange modelPayloadToRange) {
        if (range <= modelPayloadToRange.getP1Range()) {
            return model.getMaxSeatingCapacity();
        }

        if (range <= modelPayloadToRange.getP2Range()) {
            int payload = calcPayload(
                    modelPayloadToRange.getP1Range(), modelPayloadToRange.getP1Payload(),
                    modelPayloadToRange.getP2Range(), modelPayloadToRange.getP2Payload(),
                    range);
            int seatingCapacity = payload / 95;
            return Math.min(model.getMaxSeatingCapacity(), seatingCapacity);
        }

        if (range <= modelPayloadToRange.getP3Range()) {
            int payload = calcPayload(
                    modelPayloadToRange.getP2Range(), modelPayloadToRange.getP2Payload(),
                    modelPayloadToRange.getP3Range(), 0,
                    range);
            int seatingCapacity = payload / 95;
            return Math.min(model.getMaxSeatingCapacity(), seatingCapacity);
        }

        return -1;
    }

    private static int calcPayload(int r1, int p1, int r2, int p2, int r) {
        double dp = p1 - p2;
        double dr = r1 - r2;
        double a = dp / dr;
        double b = p1 - a * r1;
        return (int) (a * r + b);
    }
}
