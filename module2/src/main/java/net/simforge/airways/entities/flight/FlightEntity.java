package net.simforge.airways.entities.flight;

import net.simforge.airways.entities.aircraft.AircraftTypeEntity;
import net.simforge.airways.entities.geo.AirportEntity;
import net.simforge.airways.model.aircraft.AircraftType;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.model.geo.Airport;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Deprecated
@Entity(name = "Flight")
@Table(name = "aw_flight")
public class FlightEntity implements Flight {
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
    @ManyToOne(targetEntity = AircraftTypeEntity.class)
    @JoinColumn(name = "aircraft_type_id")
    private AircraftType aircraftType;
    @ManyToOne(targetEntity = TransportFlightEntity.class)
    @JoinColumn(name = "transport_flight_id")
    private TransportFlight transportFlight;
    private String number;
    @ManyToOne(targetEntity = AirportEntity.class)
    @JoinColumn(name = "from_airport_id")
    private Airport fromAirport;
    @ManyToOne(targetEntity = AirportEntity.class)
    @JoinColumn(name = "to_airport_id")
    private Airport toAirport;
    @ManyToOne(targetEntity = AirportEntity.class)
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
//    @Column(name = "heartbeat_dt")
//    private LocalDateTime heartbeatDt;

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

    @Override
    public LocalDate getDateOfFlight() {
        return dateOfFlight;
    }

    @Override
    public void setDateOfFlight(LocalDate dateOfFlight) {
        this.dateOfFlight = dateOfFlight;
    }

    @Override
    public String getCallsign() {
        return callsign;
    }

    @Override
    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    @Override
    public AircraftType getAircraftType() {
        return aircraftType;
    }

    @Override
    public void setAircraftType(AircraftType aircraftType) {
        this.aircraftType = aircraftType;
    }

    @Override
    public TransportFlight getTransportFlight() {
        return transportFlight;
    }

    @Override
    public void setTransportFlight(TransportFlight transportFlight) {
        this.transportFlight = transportFlight;
    }

    @Override
    public String getNumber() {
        return number;
    }

    @Override
    public void setNumber(String number) {
        this.number = number;
    }

    @Override
    public Airport getFromAirport() {
        return fromAirport;
    }

    @Override
    public void setFromAirport(Airport fromAirport) {
        this.fromAirport = fromAirport;
    }

    @Override
    public Airport getToAirport() {
        return toAirport;
    }

    @Override
    public void setToAirport(Airport toAirport) {
        this.toAirport = toAirport;
    }

    @Override
    public Airport getAlternativeAirport() {
        return alternativeAirport;
    }

    @Override
    public void setAlternativeAirport(Airport alternativeAirport) {
        this.alternativeAirport = alternativeAirport;
    }

    @Override
    public LocalDateTime getScheduledDepartureTime() {
        return scheduledDepartureTime;
    }

    @Override
    public void setScheduledDepartureTime(LocalDateTime scheduledDepartureTime) {
        this.scheduledDepartureTime = scheduledDepartureTime;
    }

    @Override
    public LocalDateTime getActualDepartureTime() {
        return actualDepartureTime;
    }

    @Override
    public void setActualDepartureTime(LocalDateTime actualDepartureTime) {
        this.actualDepartureTime = actualDepartureTime;
    }

    @Override
    public LocalDateTime getScheduledTakeoffTime() {
        return scheduledTakeoffTime;
    }

    @Override
    public void setScheduledTakeoffTime(LocalDateTime scheduledTakeoffTime) {
        this.scheduledTakeoffTime = scheduledTakeoffTime;
    }

    @Override
    public LocalDateTime getActualTakeoffTime() {
        return actualTakeoffTime;
    }

    @Override
    public void setActualTakeoffTime(LocalDateTime actualTakeoffTime) {
        this.actualTakeoffTime = actualTakeoffTime;
    }

    @Override
    public LocalDateTime getScheduledLandingTime() {
        return scheduledLandingTime;
    }

    @Override
    public void setScheduledLandingTime(LocalDateTime scheduledLandingTime) {
        this.scheduledLandingTime = scheduledLandingTime;
    }

    @Override
    public LocalDateTime getActualLandingTime() {
        return actualLandingTime;
    }

    @Override
    public void setActualLandingTime(LocalDateTime actualLandingTime) {
        this.actualLandingTime = actualLandingTime;
    }

    @Override
    public LocalDateTime getScheduledArrivalTime() {
        return scheduledArrivalTime;
    }

    @Override
    public void setScheduledArrivalTime(LocalDateTime scheduledArrivalTime) {
        this.scheduledArrivalTime = scheduledArrivalTime;
    }

    @Override
    public LocalDateTime getActualArrivalTime() {
        return actualArrivalTime;
    }

    @Override
    public void setActualArrivalTime(LocalDateTime actualArrivalTime) {
        this.actualArrivalTime = actualArrivalTime;
    }

    @Override
    public Integer getStatus() {
        return status;
    }

    @Override
    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    public LocalDateTime getStatusDt() {
        return statusDt;
    }

    @Override
    public void setStatusDt(LocalDateTime statusDt) {
        this.statusDt = statusDt;
    }

/*    @Override
    public LocalDateTime getHeartbeatDt() {
        return heartbeatDt;
    }

    @Override
    public void setHeartbeatDt(LocalDateTime heartbeatDt) {
        this.heartbeatDt = heartbeatDt;
    }*/

    @Override
    public String toString() {
        return "Flight{" +
                "id=" + id +
                ", number='" + number + '\'' +
                ", scheduledDepartureTime=" + scheduledDepartureTime +
                '}';
    }
}
