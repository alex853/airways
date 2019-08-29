package net.simforge.airways.model.flight;

import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.persistence.EventLog;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Deprecated
public interface TransportFlight extends BaseEntity, EventLog.Loggable, Auditable {

    TimetableRow getTimetableRow();

    void setTimetableRow(TimetableRow timetableRow);

    Flight getFlight();

    void setFlight(Flight flight);

    LocalDate getDateOfFlight();

    void setDateOfFlight(LocalDate dateOfFlight);

    String getNumber();

    void setNumber(String number);

    Airport getFromAirport();

    void setFromAirport(Airport fromAirport);

    Airport getToAirport();

    void setToAirport(Airport toAirport);

    LocalDateTime getDepartureDt();

    void setDepartureDt(LocalDateTime departureDt);

    LocalDateTime getArrivalDt();

    void setArrivalDt(LocalDateTime arrivalDt);

    Integer getStatus();

    void setStatus(Integer status);

    LocalDateTime getStatusDt();

    void setStatusDt(LocalDateTime statusDt);

    Integer getTotalTickets();

    void setTotalTickets(Integer totalTickets);

    Integer getFreeTickets();

    void setFreeTickets(Integer freeTickets);

    class Status {
        public static final int Scheduled = 100;
        public static final int Checkin = 1000;
        public static final int WaitingForBoarding = 1100;
        public static final int Boarding = 1200;
        public static final int WaitingForDeparture = 1300;
        public static final int Departure = 1400;
        public static final int Flying = 2000;
        public static final int Arrival = 3000;
        public static final int WaitingForUnboarding = 3100;
        public static final int Unboarding = 3200;
        public static final int Finished = 7777;
        public static final int CancellationRequested = 8000;
        public static final int Cancelled = 8888;
    }
}
