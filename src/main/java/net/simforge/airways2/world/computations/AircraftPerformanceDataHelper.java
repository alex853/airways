package net.simforge.airways2.world.computations;

public class AircraftPerformanceDataHelper {
    // todo ak2 load performance data from somewhere
    public static AircraftPerformanceData getData() {
        return new AircraftPerformanceData() {
            @Override
            public Integer getTypicalCruiseAltitude() {
                return 36000;
            }

            @Override
            public Integer getTypicalCruiseSpeed() {
                return 440;
            }

            @Override
            public Integer getClimbVerticalSpeed() {
                return 2000;
            }

            @Override
            public Integer getDescentVerticalSpeed() {
                return 1500;
            }

            @Override
            public Integer getTakeoffSpeed() {
                return 160;
            }

            @Override
            public Integer getLandingSpeed() {
                return 140;
            }
        };
    }
}
