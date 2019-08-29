package net.simforge.airways.model.flight;

import net.simforge.airways.model.Airline;
import net.simforge.airways.model.aircraft.AircraftType;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.persistence.EventLog;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

@Deprecated
public interface TimetableRow extends BaseEntity, EventLog.Loggable, Auditable {

    Airline getAirline();

    void setAirline(Airline airline);

    String getNumber();

    void setNumber(String number);

    Airport getFromAirport();

    void setFromAirport(Airport fromAirport);

    Airport getToAirport();

    void setToAirport(Airport toAirport);

    AircraftType getAircraftType();

    void setAircraftType(AircraftType aircraftType);

    String getWeekdays();

    void setWeekdays(String weekdays);

    String getDepartureTime();

    void setDepartureTime(String departureTime);

    String getDuration();

    void setDuration(String duration);

    Integer getStatus();

    void setStatus(Integer status);

    Integer getTotalTickets();

    void setTotalTickets(Integer totalTickets);

    Integer getHorizon();

    void setHorizon(Integer horizon);

    class Status {
        public static final int Active = 0;
        public static final int Stopped = 1;
    }
}
