/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.util;

import net.simforge.airways.persistence.model.aircraft.AircraftType;

public class TestRefData {
    public static AircraftType getA320Data() {
        AircraftType data = new AircraftType();
        data.setTypicalCruiseAltitude(36000);
        data.setTypicalCruiseSpeed(444);
        data.setTakeoffSpeed(160);
        data.setLandingSpeed(150);
        data.setClimbVerticalSpeed(2000);
        data.setDescentVerticalSpeed(1200);
        return data;
    }

    public static AircraftType getC152Data() {
        AircraftType data = new AircraftType();
        data.setTypicalCruiseAltitude(6000);
        data.setTypicalCruiseSpeed(107);
        data.setTakeoffSpeed(60);
        data.setLandingSpeed(60);
        data.setClimbVerticalSpeed(715);
        data.setDescentVerticalSpeed(500);
        return data;
    }
}
