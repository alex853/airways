package net.simforge.airways2.world.computations;

import net.simforge.refdata.aircrafts.apd.AircraftPerformance;
import net.simforge.refdata.aircrafts.apd.AircraftPerformanceDatabase;

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
                return ap.getCruiseCeiling();
            }

            @Override
            public Integer getTypicalCruiseSpeed() {
                return ap.getCruiseTas();
            }

            @Override
            public Integer getClimbVerticalSpeed() {
                return ap.getClimbToFL240Rate();
            }

            @Override
            public Integer getDescentVerticalSpeed() {
                return ap.getDescentToFL100Rate();
            }

            @Override
            public Integer getTakeoffSpeed() {
                return ap.getTakeoffV2Ias();
            }

            @Override
            public Integer getLandingSpeed() {
                return ap.getLandingVatIas();
            }
        };
    }
}
