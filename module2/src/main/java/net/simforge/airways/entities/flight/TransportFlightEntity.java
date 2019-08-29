package net.simforge.airways.entities.flight;

import net.simforge.airways.entities.geo.AirportEntity;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.model.flight.TimetableRow;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.model.geo.Airport;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Deprecated
@Entity(name = "TransportFlight")
@Table(name = "aw_transport_flight")
public class TransportFlightEntity implements TransportFlight {
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

//    @Column(name = "heartbeat_dt")
//    private LocalDateTime heartbeatDt;

    @ManyToOne(targetEntity = TimetableRowEntity.class)
    @JoinColumn(name = "timetable_row_id")
    private TimetableRow timetableRow;
    @ManyToOne(targetEntity = FlightEntity.class)
    @JoinColumn(name = "flight_id")
    private Flight flight;
    @Column(name = "date_of_flight")
    private LocalDate dateOfFlight;
    private String number;
    @ManyToOne(targetEntity = AirportEntity.class)
    @JoinColumn(name = "from_airport_id")
    private Airport fromAirport;
    @ManyToOne(targetEntity = AirportEntity.class)
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

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

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

//    public LocalDateTime getHeartbeatDt() {
//        return heartbeatDt;
//    }

//    public void setHeartbeatDt(LocalDateTime heartbeatDt) {
//        this.heartbeatDt = heartbeatDt;
//    }

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

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getStatusDt() {
        return statusDt;
    }

    public void setStatusDt(LocalDateTime statusDt) {
        this.statusDt = statusDt;
    }

    public Integer getTotalTickets() {
        return totalTickets;
    }

    public void setTotalTickets(Integer totalTickets) {
        this.totalTickets = totalTickets;
    }

    public Integer getFreeTickets() {
        return freeTickets;
    }

    public void setFreeTickets(Integer freeTickets) {
        this.freeTickets = freeTickets;
    }

    @Override
    public String toString() {
        return "TransportFlight{" +
                "id=" + id +
                ", number='" + number + '\'' +
                ", dateOfFlight=" + dateOfFlight +
                '}';
    }
}
