/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model.flight;

import net.simforge.airways.persistence.EventLog;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity(name = "Flight")
@Table(name = "aw_flight")
public class Flight implements BaseEntity, Auditable, EventLog.Loggable {
    @SuppressWarnings("unused")
    public static final String EventLogCode = "flight";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_flight_id")
    @SequenceGenerator(name = "aw_flight_id", sequenceName = "aw_flight_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @SuppressWarnings("unused")
    @Column(name = "create_dt")
    private LocalDateTime createDt;
    @SuppressWarnings("unused")
    @Column(name = "modify_dt")
    private LocalDateTime modifyDt;

    @Column(name = "date_of_flight")
    private LocalDate dateOfFlight;
    private String callsign;
    @ManyToOne
    @JoinColumn(name = "aircraft_type_id")
    private AircraftType aircraftType;
    @ManyToOne
    @JoinColumn(name = "transport_flight_id")
    private TransportFlight transportFlight;
    private String number;
    @ManyToOne
    @JoinColumn(name = "from_airport_id")
    private Airport fromAirport;
    @ManyToOne
    @JoinColumn(name = "to_airport_id")
    private Airport toAirport;
    @ManyToOne
    @JoinColumn(name = "alternative_airport_id")
    private Airport alternativeAirport;

    @Column(name = "scheduled_departure_time")
    private LocalDateTime scheduledDepartureTime;
    @Column(name = "actual_departure_time")
    private LocalDateTime actualDepartureTime;

    @Column(name = "scheduled_takeoff_time")
    private LocalDateTime scheduledTakeoffTime;
    @Column(name = "actual_takeoff_time")
    private LocalDateTime actualTakeoffTime;

    @Column(name = "scheduled_landing_time")
    private LocalDateTime scheduledLandingTime;
    @Column(name = "actual_landing_time")
    private LocalDateTime actualLandingTime;

    @Column(name = "scheduled_arrival_time")
    private LocalDateTime scheduledArrivalTime;
    @Column(name = "actual_arrival_time")
    private LocalDateTime actualArrivalTime;

    private Integer status;
    @Column(name = "status_dt")
    private LocalDateTime statusDt;

    // todo aircraft property in flight and crew assignments instead of flight/aircraft assignments...

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

    public LocalDate getDateOfFlight() {
        return dateOfFlight;
    }

    public void setDateOfFlight(LocalDate dateOfFlight) {
        this.dateOfFlight = dateOfFlight;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public AircraftType getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(AircraftType aircraftType) {
        this.aircraftType = aircraftType;
    }

    public TransportFlight getTransportFlight() {
        return transportFlight;
    }

    public void setTransportFlight(TransportFlight transportFlight) {
        this.transportFlight = transportFlight;
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

    public Airport getAlternativeAirport() {
        return alternativeAirport;
    }

    public void setAlternativeAirport(Airport alternativeAirport) {
        this.alternativeAirport = alternativeAirport;
    }

    public LocalDateTime getScheduledDepartureTime() {
        return scheduledDepartureTime;
    }

    public void setScheduledDepartureTime(LocalDateTime scheduledDepartureTime) {
        this.scheduledDepartureTime = scheduledDepartureTime;
    }

    public LocalDateTime getActualDepartureTime() {
        return actualDepartureTime;
    }

    public void setActualDepartureTime(LocalDateTime actualDepartureTime) {
        this.actualDepartureTime = actualDepartureTime;
    }

    public LocalDateTime getScheduledTakeoffTime() {
        return scheduledTakeoffTime;
    }

    public void setScheduledTakeoffTime(LocalDateTime scheduledTakeoffTime) {
        this.scheduledTakeoffTime = scheduledTakeoffTime;
    }

    public LocalDateTime getActualTakeoffTime() {
        return actualTakeoffTime;
    }

    public void setActualTakeoffTime(LocalDateTime actualTakeoffTime) {
        this.actualTakeoffTime = actualTakeoffTime;
    }

    public LocalDateTime getScheduledLandingTime() {
        return scheduledLandingTime;
    }

    public void setScheduledLandingTime(LocalDateTime scheduledLandingTime) {
        this.scheduledLandingTime = scheduledLandingTime;
    }

    public LocalDateTime getActualLandingTime() {
        return actualLandingTime;
    }

    public void setActualLandingTime(LocalDateTime actualLandingTime) {
        this.actualLandingTime = actualLandingTime;
    }

    public LocalDateTime getScheduledArrivalTime() {
        return scheduledArrivalTime;
    }

    public void setScheduledArrivalTime(LocalDateTime scheduledArrivalTime) {
        this.scheduledArrivalTime = scheduledArrivalTime;
    }

    public LocalDateTime getActualArrivalTime() {
        return actualArrivalTime;
    }

    public void setActualArrivalTime(LocalDateTime actualArrivalTime) {
        this.actualArrivalTime = actualArrivalTime;
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

    @Override
    public String toString() {
        return "Flight{" +
                "id=" + id +
                ", number='" + number + '\'' +
                ", scheduledDepartureTime=" + scheduledDepartureTime +
                '}';
    }

    public class Status {
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
