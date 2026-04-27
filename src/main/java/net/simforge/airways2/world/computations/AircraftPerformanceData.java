package net.simforge.airways2.world.computations;

import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.refdata.aircrafts.apd.AircraftPerformance;
import net.simforge.refdata.aircrafts.apd.AircraftPerformanceDatabase;

import java.util.function.Supplier;

public interface AircraftPerformanceData {

    Integer getTypicalCruiseAltitude();

    Integer getTypicalCruiseSpeed();

    Integer getClimbVerticalSpeed();

    Integer getDescentVerticalSpeed();

    Integer getTakeoffSpeed();

    Integer getLandingSpeed();

    static AircraftPerformanceData getData(final String icaoCode) {
        final AircraftPerformance ap = AircraftPerformanceDatabase.getPerformance(icaoCode).orElseThrow();
        return new AircraftPerformanceData() {
            @Override
            public Integer getTypicalCruiseAltitude() {
                return getOrDefault(ap.getCruiseCeiling(), 35000, "typicalCruiseAltitude");
            }

            @Override
            public Integer getTypicalCruiseSpeed() {
                return getOrDefault(ap.getCruiseTas(), 450, "typicalCruiseSpeed");
            }

            @Override
            public Integer getClimbVerticalSpeed() {
                return getOrDefault(ap.getClimbToFL240Rate(), 2000, "climbVerticalSpeed");
            }

            @Override
            public Integer getDescentVerticalSpeed() {
                return getOrDefault(ap.getDescentToFL100Rate(), 1500, "descentVerticalSpeed");
            }

            @Override
            public Integer getTakeoffSpeed() {
                return getOrDefault(ap.getTakeoffV2Ias(), 140, "takeoffSpeed");
            }

            @Override
            public Integer getLandingSpeed() {
                return getOrDefault(ap.getLandingVatIas(), 130, "landingSpeed");
            }

            private Integer getOrDefault(Integer value, Integer defaultValue, String dataItem) {
                if (value == null) {
                    FlightStats.event("aircraft type performance data missing " + icaoCode + "." + dataItem);
                    return defaultValue;
                }
                return value;
            }
        };
    }
}
