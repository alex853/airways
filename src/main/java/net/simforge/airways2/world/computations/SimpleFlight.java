package net.simforge.airways2.world.computations;

import net.simforge.commons.misc.Geo;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class SimpleFlight {
    private Geo.Coords fromCoords;
    private Geo.Coords toCoords;
    private AircraftPerformanceData performanceData;

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

    public static SimpleFlight forRoute(Geo.Coords from, Geo.Coords to, AircraftPerformanceData performanceData) {
        SimpleFlight result = new SimpleFlight();

        result.fromCoords = from;
        result.toCoords = to;
        result.performanceData = performanceData;

        result.totalDistance = Geo.distance(from, to);

        calc25PercentCase(result, performanceData);

        if (result.cruiseAltitude > performanceData.getTypicalCruiseAltitude()) {
            calcLongCruiseCase(result, performanceData);
        }

        return result;
    }

    /**
     * The flight from a point in the air (or on the ground) to the target: how long it takes from where the aircraft is now, at the altitude it is at now. The same
     * profile as forRoute but it starts in the middle: the speed depends on the altitude and grows linearly with it, from the speed of the takeoff on the ground to the
     * typical cruise speed at the typical cruise altitude (it is how the climb of forRoute goes, the climb rate is constant). Then:
     * <ul>
     * <li>if the aircraft is below the altitude which a flight of this length reaches (the cruise altitude of forRoute), it climbs to it first;</li>
     * <li>the cruise at that altitude;</li>
     * <li>the descent from that altitude to the landing speed, at the descent rate of the type.</li>
     * </ul>
     * When the target is too close for the whole profile the aircraft does not climb, and when it is closer than the descent from the current altitude needs, it is one
     * descent at the average speed of it. The result is for the times and the distances only: getAircraftPosition is of the flights which start on the ground.
     *
     * @param altitudeFeet the altitude of the aircraft now, 0 on the ground
     */
    public static SimpleFlight fromPosition(final Geo.Coords from, final double altitudeFeet, final Geo.Coords to, final AircraftPerformanceData performanceData) {
        final SimpleFlight result = new SimpleFlight();
        result.fromCoords = from;
        result.toCoords = to;
        result.performanceData = performanceData;
        result.totalDistance = Geo.distance(from, to);

        final double D = Geo.nmToKm(result.totalDistance);
        final double altTypical = performanceData.getTypicalCruiseAltitude();
        final double vCruiseTypical = Geo.nmToKm(performanceData.getTypicalCruiseSpeed());
        final double vsClimb = performanceData.getClimbVerticalSpeed();
        final double vsDescent = performanceData.getDescentVerticalSpeed();
        final double vTakeoff = Geo.nmToKm(performanceData.getTakeoffSpeed());
        final double vLanding = Geo.nmToKm(performanceData.getLandingSpeed());

        final double altNow = Math.max(0, Math.min(altitudeFeet, altTypical));
        final double vNow = speedAt(altNow, altTypical, vTakeoff, vCruiseTypical);

        // the altitude which a flight of this length gets to
        final double altPlanned = Math.max(altNow, forRoute(from, to, performanceData).getCruiseAltitude());
        final double vPlanned = speedAt(altPlanned, altTypical, vTakeoff, vCruiseTypical);

        double altCruise = altPlanned;
        double vCruise = vPlanned;
        double tClimb = (altCruise - altNow) / vsClimb / 60.0;
        double dClimb = (vNow + vCruise) / 2 * tClimb;
        double tDescent = altCruise / vsDescent / 60.0;
        double dDescent = (vCruise + vLanding) / 2 * tDescent;

        if (dClimb + dDescent > D) {
            // no room for the climb: the cruise at the altitude of now
            altCruise = altNow;
            vCruise = vNow;
            tClimb = 0;
            dClimb = 0;
            tDescent = altCruise / vsDescent / 60.0;
            dDescent = (vCruise + vLanding) / 2 * tDescent;
        }

        final double tCruise;
        final double dCruise;
        if (dDescent > D) {
            // closer than the descent needs: one descent, the average speed of it
            dCruise = 0;
            tCruise = 0;
            tDescent = D / ((vCruise + vLanding) / 2);
            dDescent = D;
        } else {
            dCruise = D - dClimb - dDescent;
            tCruise = dCruise / vCruise;
        }

        result.totalTime = toDuration(tClimb + tCruise + tDescent);
        result.climbDistance = Geo.kmToNm(dClimb);
        result.climbTime = toDuration(tClimb);
        result.cruiseDistance = Geo.kmToNm(dCruise);
        result.cruiseTime = toDuration(tCruise);
        result.cruiseAltitude = altCruise;
        result.cruiseSpeed = Geo.kmToNm(vCruise);
        result.descentDistance = Geo.kmToNm(dDescent);
        result.descentTime = toDuration(tDescent);
        return result;
    }

    // the speed (km/h) at the altitude: from the speed of the takeoff on the ground to the typical cruise speed at the typical cruise altitude, linearly
    private static double speedAt(final double altitude, final double typicalAltitude, final double takeoffSpeed, final double typicalCruiseSpeed) {
        return takeoffSpeed + (typicalCruiseSpeed - takeoffSpeed) * Math.min(1.0, altitude / typicalAltitude);
    }

    private static void calc25PercentCase(SimpleFlight result, AircraftPerformanceData performanceData) {
        double D = Geo.nmToKm(result.totalDistance);

        double ALTcruiseTypical = performanceData.getTypicalCruiseAltitude();
        double VcruiseTypical = Geo.nmToKm(performanceData.getTypicalCruiseSpeed());
        double VSclimb = performanceData.getClimbVerticalSpeed();
        double VSdescent = performanceData.getDescentVerticalSpeed();
        double Vtakeoff = Geo.nmToKm(performanceData.getTakeoffSpeed());
        double Vlanding = Geo.nmToKm(performanceData.getLandingSpeed());

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

    private static void calcLongCruiseCase(SimpleFlight result, AircraftPerformanceData performanceData) {
        double D = Geo.nmToKm(result.totalDistance);

        double ALTcruiseTypical = performanceData.getTypicalCruiseAltitude();
        double VcruiseTypical = Geo.nmToKm(performanceData.getTypicalCruiseSpeed());
        double VSclimb = performanceData.getClimbVerticalSpeed();
        double VSdescent = performanceData.getDescentVerticalSpeed();
        double Vtakeoff = Geo.nmToKm(performanceData.getTakeoffSpeed());
        double Vlanding = Geo.nmToKm(performanceData.getLandingSpeed());

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
            double takeoffSpeed = performanceData.getTakeoffSpeed();
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
            double landingSpeed = performanceData.getLandingSpeed();

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
