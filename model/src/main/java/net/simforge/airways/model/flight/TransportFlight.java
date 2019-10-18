/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.model.flight;

import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.geo.Airport;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity(name = "TransportFlight")
@Table(name = "aw_transport_flight")
public class TransportFlight implements BaseEntity, EventLog.Loggable, Auditable {
    @SuppressWarnings("unused")
    public static final String EventLogCode = "trFlight";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_transport_flight_id")
    @SequenceGenerator(name = "aw_transport_flight_id", sequenceName = "aw_transport_flight_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @SuppressWarnings("unused")
    @Column(name = "create_dt")
    private LocalDateTime createDt;
    @SuppressWarnings("unused")
    @Column(name = "modify_dt")
    private LocalDateTime modifyDt;

    @ManyToOne
    @JoinColumn(name = "timetable_row_id")
    private TimetableRow timetableRow;
    @ManyToOne
    @JoinColumn(name = "flight_id")
    private Flight flight;
    @Column(name = "date_of_flight")
    private LocalDate dateOfFlight;
    @Column(name = "flight_number")
    private String flightNumber;
    @ManyToOne
    @JoinColumn(name = "from_airport_id")
    private Airport fromAirport;
    @ManyToOne
    @JoinColumn(name = "to_airport_id")
    private Airport toAirport;
    @Column(name = "departure_dt")
    private LocalDateTime departureDt;
    @Column(name = "arrival_dt")
    private LocalDateTime arrivalDt;
    private Integer status;
    @Column(name = "status_dt")
    private LocalDateTime statusDt;
    @Column(name = "total_tickets")
    private Integer totalTickets;
    @Column(name = "free_tickets")
    private Integer freeTickets;

    @Override
    public String getEventLogCode() {
        return EventLogCode;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getVersion() {
        return version;
    }

    @Override
    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public LocalDateTime getCreateDt() {
        return createDt;
    }

    @Override
    public LocalDateTime getModifyDt() {
        return modifyDt;
    }

    public TimetableRow getTimetableRow() {
        return timetableRow;
    }

    public void setTimetableRow(TimetableRow timetableRow) {
        this.timetableRow = timetableRow;
    }

    public Flight getFlight() {
        return flight;
    }

    public void setFlight(Flight flight) {
        this.flight = flight;
    }

    public LocalDate getDateOfFlight() {
        return dateOfFlight;
    }

    public void setDateOfFlight(LocalDate dateOfFlight) {
        this.dateOfFlight = dateOfFlight;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public Airport getFromAirport() {
        return fromAirport;
    }

    public void setFromAirport(Airport fromAirport) {
        this.fromAirport = fromAirport;
    }

    public Airport getToAirport() {
        return toAirport;
    }

    public void setToAirport(Airport toAirport) {
        this.toAirport = toAirport;
    }

    public LocalDateTime getDepartureDt() {
        return departureDt;
    }

    public void setDepartureDt(LocalDateTime departureDt) {
        this.departureDt = departureDt;
    }

    public LocalDateTime getArrivalDt() {
        return arrivalDt;
    }

    public void setArrivalDt(LocalDateTime arrivalDt) {
        this.arrivalDt = arrivalDt;
    }

    public Status getStatus() {
        return status != null ? Status.byCode(status) : null;
    }

    public void setStatus(Status status) {
        this.status = status != null ? status.code() : null;
    }

    public LocalDateTime getStatusDt() {
        return statusDt;
    }

    public void setStatusDt(LocalDateTime statusDt) {
        this.statusDt = statusDt;
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public void setTotalTickets(int totalTickets) {
        this.totalTickets = totalTickets;
    }

    public int getFreeTickets() {
        return freeTickets;
    }

    public void setFreeTickets(int freeTickets) {
        this.freeTickets = freeTickets;
    }

    @Override
    public String toString() {
        return "TransportFlight{" +
                "id=" + id +
                ", number='" + flightNumber + '\'' +
                ", dateOfFlight=" + dateOfFlight +
                '}';
    }

    public enum Status {
        Scheduled(100),
        Checkin(1000),
        WaitingForBoarding(1100),
        Boarding(1200),
        WaitingForDeparture(1300),
        Departure(1400),
        Flying(2000),
        Arrival(3000),
        WaitingForDeboarding(3100),
        Deboarding(3200),
        Finished(7777),
        CancellationRequested(9001),
        Cancelled(9999);

        private final int code;

        Status(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Status byCode(int code) {
            for (Status value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return code + " - " + name();
        }
    }
}
