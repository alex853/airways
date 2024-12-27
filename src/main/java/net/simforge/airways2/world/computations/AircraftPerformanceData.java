package net.simforge.airways2.world.computations;

public interface AircraftPerformanceData {

    Integer getTypicalCruiseAltitude();

    Integer getTypicalCruiseSpeed();

    Integer getClimbVerticalSpeed();

    Integer getDescentVerticalSpeed();

    Integer getTakeoffSpeed();

    Integer getLandingSpeed();

}
