package net.simforge.airways.model.person;

import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;

public interface Person /*extends BaseHeartbeatEntity, EventLog.Loggable, Auditable */ {
    Integer getType();

    void setType(Integer type);

    Integer getStatus();

    void setStatus(Integer status);

    String getName();

    void setName(String name);

    String getSurname();

    void setSurname(String surname);

    String getSex();

    void setSex(String sex);

    City getOriginCity();

    void setOriginCity(City originCity);

    City getPositionCity();

    void setPositionCity(City positionCity);

    Airport getPositionAirport();

    void setPositionAirport(Airport positionAirport);

//    Journey getJourney();

//    void setJourney(Journey journey);

    class Type {
        public static final int Ordinal = 0;
        public static final int Excluded = 1;
    }

    class Status {
        public static final int ReadyToTravel = 0;
        public static final int Travelling = 1;
        public static final int NoTravel = 2;
    }
}
