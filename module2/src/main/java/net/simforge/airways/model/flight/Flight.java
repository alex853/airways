package net.simforge.airways.model.flight;

import net.simforge.airways.model.aircraft.AircraftType;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.persistence.EventLog;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Deprecated
public interface Flight extends BaseEntity, EventLog.Loggable, Auditable {

    LocalDate getDateOfFlight();

    void setDateOfFlight(LocalDate dateOfFlight);

    String getCallsign();

    void setCallsign(String callsign);

    AircraftType getAircraftType();

    void setAircraftType(AircraftType aircraftType);

    TransportFlight getTransportFlight();

    void setTransportFlight(TransportFlight transportFlight);

    String getNumber();

    void setNumber(String number);

    Airport getFromAirport();

    void setFromAirport(Airport fromAirport);

    Airport getToAirport();

    void setToAirport(Airport toAirport);

    Airport getAlternativeAirport();

    void setAlternativeAirport(Airport alternativeAirport);

    LocalDateTime getScheduledDepartureTime();

    void setScheduledDepartureTime(LocalDateTime scheduledDepartureTime);

    LocalDateTime getActualDepartureTime();

    void setActualDepartureTime(LocalDateTime actualDepartureTime);

    LocalDateTime getScheduledTakeoffTime();

    void setScheduledTakeoffTime(LocalDateTime scheduledTakeoffTime);

    LocalDateTime getActualTakeoffTime();

    void setActualTakeoffTime(LocalDateTime actualTakeoffTime);

    LocalDateTime getScheduledLandingTime();

    void setScheduledLandingTime(LocalDateTime scheduledLandingTime);

    LocalDateTime getActualLandingTime();

    void setActualLandingTime(LocalDateTime actualLandingTime);

    LocalDateTime getScheduledArrivalTime();

    void setScheduledArrivalTime(LocalDateTime scheduledArrivalTime);

    LocalDateTime getActualArrivalTime();

    void setActualArrivalTime(LocalDateTime actualArrivalTime);

    Integer getStatus();

    void setStatus(Integer status);

    LocalDateTime getStatusDt();

    void setStatusDt(LocalDateTime statusDt);

    class Status {
        public final static int Planned = 100;
        public final static int Assigned = 200;
        public final static int PreFlight = 300;
        public final static int Departure = 400;
        public final static int Flying = 500;
        public final static int Arrival = 600;
        public final static int PostFlight = 700;
        public final static int Finished = 1000;
        public final static int Cancelled = 9999;
    }
}
