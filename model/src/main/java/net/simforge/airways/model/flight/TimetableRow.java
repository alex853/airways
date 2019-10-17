/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.model.flight;

import net.simforge.airways.model.Airline;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.aircraft.AircraftType;
import net.simforge.airways.model.geo.Airport;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "TimetableRow")
@Table(name = "aw_timetable_row")
public class TimetableRow implements BaseEntity, /*HeartbeatObject,*/ EventLog.Loggable, Auditable {
    @SuppressWarnings("unused")
    public static final String EventLogCode = "ttRow";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_timetable_row_id")
    @SequenceGenerator(name = "aw_timetable_row_id", sequenceName = "aw_timetable_row_id_seq", allocationSize = 1)
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

    @ManyToOne
    @JoinColumn(name = "airline_id")
    private Airline airline;
    private String number;
    @ManyToOne
    @JoinColumn(name = "from_airport_id")
    private Airport fromAirport;
    @ManyToOne
    @JoinColumn(name = "to_airport_id")
    private Airport toAirport;
    @ManyToOne
    @JoinColumn(name = "aircraft_type_id")
    private AircraftType aircraftType;
    private String weekdays;
    @Column(name = "departure_time")
    private String departureTime;
    private String duration;
    private Integer status;
    @Column(name = "total_tickets")
    private Integer totalTickets;
    private Integer horizon;

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

    /*    @Override
    public LocalDateTime getHeartbeatDt() {
        return heartbeatDt;
    }

    @Override
    public void setHeartbeatDt(LocalDateTime heartbeatDt) {
        this.heartbeatDt = heartbeatDt;
    }*/

    public Airline getAirline() {
        return airline;
    }

    public void setAirline(Airline airline) {
        this.airline = airline;
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

    public AircraftType getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(AircraftType aircraftType) {
        this.aircraftType = aircraftType;
    }

    public String getWeekdays() {
        return weekdays;
    }

    public void setWeekdays(String weekdays) {
        this.weekdays = weekdays;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getTotalTickets() {
        return totalTickets;
    }

    public void setTotalTickets(Integer totalTickets) {
        this.totalTickets = totalTickets;
    }

    public Integer getHorizon() {
        return horizon;
    }

    public void setHorizon(Integer horizon) {
        this.horizon = horizon;
    }

    @Override
    public String toString() {
        return "TimetableRow{" +
                "id=" + id +
                ", number='" + number + '\'' +
                '}';
    }

    public static class Status {
        public static final int Active = 0;
        public static final int Stopped = 1;
    }
}
