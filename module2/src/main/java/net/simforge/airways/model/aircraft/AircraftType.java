package net.simforge.airways.model.aircraft;

@Deprecated
public interface AircraftType /*extends BaseEntity*/ {

    String getIcao();

    void setIcao(String icao);

    String getIata();

    void setIata(String iata);

    Integer getTypicalCruiseAltitude();

    void setTypicalCruiseAltitude(Integer typicalCruiseAltitude);

    Integer getTypicalCruiseSpeed();

    void setTypicalCruiseSpeed(Integer typicalCruiseSpeed);

    Integer getClimbVerticalSpeed();

    void setClimbVerticalSpeed(Integer climbVerticalSpeed);

    Integer getDescentVerticalSpeed();

    void setDescentVerticalSpeed(Integer descentVerticalSpeed);

    Integer getTakeoffSpeed();

    void setTakeoffSpeed(Integer takeoffSpeed);

    Integer getLandingSpeed();

    void setLandingSpeed(Integer landingSpeed);

}
