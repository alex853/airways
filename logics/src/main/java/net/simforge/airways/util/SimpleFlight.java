/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.util;

import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.commons.misc.Geo;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class SimpleFlight {
    private Geo.Coords fromCoords;
    private Geo.Coords toCoords;
    private AircraftType aircraftType;

    private double totalDistance;
    private Duration totalTime;

    private double climbDistance;
    private Duration climbTime;

    private double cruiseDistance;
    private Duration cruiseTime;
    private double cruiseAltitude;
    private double cruiseSpeed;

    private double descentDistance;
    private Duration descentTime;

    private SimpleFlight() {
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public Duration getTotalTime() {
        return totalTime;
    }

    public double getClimbDistance() {
        return climbDistance;
    }

    public Duration getClimbTime() {
        return climbTime;
    }

    public double getCruiseDistance() {
        return cruiseDistance;
    }

    public Duration getCruiseTime() {
        return cruiseTime;
    }

    public double getCruiseAltitude() {
        return cruiseAltitude;
    }

    public double getCruiseSpeed() {
        return cruiseSpeed;
    }

    public double getDescentDistance() {
        return descentDistance;
    }

    public Duration getDescentTime() {
        return descentTime;
    }

    public static SimpleFlight forRoute(Geo.Coords from, Geo.Coords to, AircraftType data) {
        SimpleFlight result = new SimpleFlight();

        result.fromCoords = from;
        result.toCoords = to;
        result.aircraftType = data;

        result.totalDistance = Geo.distance(from, to);

        calc25PercentCase(result, data);

        if (result.cruiseAltitude > data.getTypicalCruiseAltitude()) {
            calcLongCruiseCase(result, data);
        }

        return result;
    }

    private static void calc25PercentCase(SimpleFlight result, AircraftType data) {
        double D = Geo.nmToKm(result.totalDistance);

        double ALTcruiseTypical = data.getTypicalCruiseAltitude();
        double VcruiseTypical = Geo.nmToKm(data.getTypicalCruiseSpeed());
        double VSclimb = data.getClimbVerticalSpeed();
        double VSdescent = data.getDescentVerticalSpeed();
        double Vtakeoff = Geo.nmToKm(data.getTakeoffSpeed());
        double Vlanding = Geo.nmToKm(data.getLandingSpeed());

        double Dcruise = 0.25 * D;

        double Aclimb = (VcruiseTypical - Vtakeoff) / (ALTcruiseTypical / VSclimb / 60.0);
        double Adescent = (VcruiseTypical - Vlanding) / (ALTcruiseTypical / VSdescent / 60.0);

        double VcruiseSqrt = (2 * 0.75 * D + Math.pow(Vtakeoff, 2) / Aclimb + Math.pow(Vlanding, 2) / Adescent) / (1 / Aclimb + 1 / Adescent);
        double Vcruise = Math.sqrt(VcruiseSqrt);

        double Tclimb = (Vcruise - Vtakeoff) / Aclimb;
        double Dclimb = (Vtakeoff + Vcruise) / 2 * Tclimb;

        double Tdescent = (Vcruise - Vlanding) / Adescent;
        double Ddescent = (Vcruise + Vlanding) / 2 * Tdescent;

        double ALTcruise = VSclimb * (Tclimb * 60.0);
        double Tcruise = Dcruise / Vcruise;

        double T = Tclimb + Tcruise + Tdescent;

        result.totalTime = toDuration(T);

        result.climbDistance = Geo.kmToNm(Dclimb);
        result.climbTime = toDuration(Tclimb);

        result.cruiseDistance = Geo.kmToNm(Dcruise);
        result.cruiseTime = toDuration(Tcruise);
        result.cruiseAltitude = ALTcruise;
        result.cruiseSpeed = Geo.kmToNm(Vcruise);

        result.descentDistance = Geo.kmToNm(Ddescent);
        result.descentTime = toDuration(Tdescent);
    }

    private static void calcLongCruiseCase(SimpleFlight result, AircraftType data) {
        double D = Geo.nmToKm(result.totalDistance);

        double ALTcruiseTypical = data.getTypicalCruiseAltitude();
        double VcruiseTypical = Geo.nmToKm(data.getTypicalCruiseSpeed());
        double VSclimb = data.getClimbVerticalSpeed();
        double VSdescent = data.getDescentVerticalSpeed();
        double Vtakeoff = Geo.nmToKm(data.getTakeoffSpeed());
        double Vlanding = Geo.nmToKm(data.getLandingSpeed());

        //noinspection UnnecessaryLocalVariable
        double ALTcruise = ALTcruiseTypical;
        //noinspection UnnecessaryLocalVariable
        double Vcruise = VcruiseTypical;

        double Tclimb = ALTcruiseTypical / VSclimb / 60.0;
        double Dclimb = (Vtakeoff + Vcruise) / 2 * Tclimb;

        double Tdescent = ALTcruiseTypical / VSdescent / 60.0;
        double Ddescent = (Vcruise + Vlanding) / 2 * Tdescent;

        double Dcruise = D - Dclimb - Ddescent;
        double Tcruise = Dcruise / Vcruise;

        double T = Tclimb + Tcruise + Tdescent;

        result.totalTime = toDuration(T);

        result.climbDistance = Geo.kmToNm(Dclimb);
        result.climbTime = toDuration(Tclimb);

        result.cruiseDistance = Geo.kmToNm(Dcruise);
        result.cruiseTime = toDuration(Tcruise);
        result.cruiseAltitude = ALTcruise;
        result.cruiseSpeed = Geo.kmToNm(Vcruise);

        result.descentDistance = Geo.kmToNm(Ddescent);
        result.descentTime = toDuration(Tdescent);
    }

    public Position getAircraftPosition(Duration time) {
        if (time.getSeconds() < 0) {
            return new Position(Position.Stage.BeforeTakeoff, fromCoords);
        } else if (time.compareTo(climbTime) < 0) {
            double takeoffSpeed = aircraftType.getTakeoffSpeed();
            double cruiseSpeed = getCruiseSpeed();

            double a = (cruiseSpeed - takeoffSpeed) / toHours(climbTime);

            double t = toHours(time);
            double dist = takeoffSpeed * t + a * t * t / 2;

            double bearing = Geo.bearing(fromCoords, toCoords);
            Geo.Coords position = Geo.destination(fromCoords, bearing, dist);

            return new Position(Position.Stage.Climb, position);
        } else if (time.compareTo(climbTime.plus(cruiseTime)) < 0) {
            Duration timeSinceTOC = time.minus(climbTime);

            double cruiseSpeed = getCruiseSpeed();

            double dist = toHours(timeSinceTOC) * cruiseSpeed + climbDistance;

            double bearing = Geo.bearing(fromCoords, toCoords);
            Geo.Coords position = Geo.destination(fromCoords, bearing, dist);

            return new Position(Position.Stage.Cruise, position);
        } else if (time.compareTo(totalTime) <= 0) {
            Duration timeSinceTOD = time.minus(climbTime).minus(cruiseTime);

            double cruiseSpeed = getCruiseSpeed();
            double landingSpeed = aircraftType.getLandingSpeed();

            double a = (landingSpeed - cruiseSpeed) / toHours(descentTime);

            double t = toHours(timeSinceTOD);
            double dist = cruiseSpeed * t + a * t * t / 2 + climbDistance + cruiseDistance;

            double bearing = Geo.bearing(fromCoords, toCoords);
            Geo.Coords position = Geo.destination(fromCoords, bearing, dist);

            return new Position(Position.Stage.Descent, position);
        } else {
            return new Position(Position.Stage.AfterLanding, toCoords);
        }
    }

    public static class Position {
        public enum Stage {BeforeTakeoff, Climb, Cruise, Descent, AfterLanding}

        private Stage stage;
        private Geo.Coords coords;

        private Position(Stage stage, Geo.Coords coords) {
            this.stage = stage;
            this.coords = coords;
        }

        public Stage getStage() {
            return stage;
        }

        public Geo.Coords getCoords() {
            return coords;
        }
    }

    private double toHours(Duration duration) {
        return duration.toMillis() / (3600000.0);
    }

    private static Duration toDuration(double t) {
        return Duration.ofSeconds((int) (t * TimeUnit.HOURS.toSeconds(1)));
    }

}
