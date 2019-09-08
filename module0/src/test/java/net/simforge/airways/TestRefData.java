/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.persistence.model.aircraft.AircraftType;

public class TestRefData {
    public static AircraftType getA320Data() {
        AircraftType type = new AircraftType();
        type.setIcao("A320");
        type.setTypicalCruiseAltitude(36000);
        type.setTypicalCruiseSpeed(444);
        type.setTakeoffSpeed(160);
        type.setLandingSpeed(150);
        type.setClimbVerticalSpeed(2000);
        type.setDescentVerticalSpeed(1200);
        return type;
    }

    public static AircraftType getC152Data() {
        AircraftType type = new AircraftType();
        type.setIcao("C172");
        type.setTypicalCruiseAltitude(6000);
        type.setTypicalCruiseSpeed(107);
        type.setTakeoffSpeed(60);
        type.setLandingSpeed(60);
        type.setClimbVerticalSpeed(715);
        type.setDescentVerticalSpeed(500);
        return type;
    }
}
