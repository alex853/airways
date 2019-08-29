package net.simforge.airways.model.aircraft;

import net.simforge.airways.model.Airline;
import net.simforge.airways.model.geo.Airport;

import java.time.LocalDateTime;

public interface Aircraft /*extends BaseEntity, Auditable, EventLog.Loggable*/ {
    AircraftType getType();

    void setType(AircraftType type);

    String getRegNo();

    void setRegNo(String regNo);

    Airline getAirline();

    void setAirline(Airline airline);

    Integer getStatus();

    void setStatus(Integer status);

    LocalDateTime getHeartbeatDt();

    void setHeartbeatDt(LocalDateTime heartbeatDt);

    Double getPositionLatitude();

    void setPositionLatitude(Double positionLatitude);

    Double getPositionLongitude();

    void setPositionLongitude(Double positionLongitude);

    Airport getPositionAirport();

    void setPositionAirport(Airport positionAirport);

    class Status {
        public final static int Idle = 100;
        public final static int IdlePlanned = 101; // temporarily added status for stupid allocation needs
        public final static int PreFlight = 200;
        public final static int TaxiingOut = 300;
        public final static int Flying = 400;
        public final static int TaxiingIn = 500;
        public final static int PostFlight = 600;
    }
}
