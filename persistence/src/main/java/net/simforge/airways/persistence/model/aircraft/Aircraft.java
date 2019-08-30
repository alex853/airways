/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model.aircraft;

import net.simforge.airways.persistence.model.Airline;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "Aircraft")
@Table(name = "aw_aircraft")
public class Aircraft implements BaseEntity, Auditable, EventLog.Loggable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_aircraft_id")
    @SequenceGenerator(name = "aw_aircraft_id", sequenceName = "aw_aircraft_id_seq", allocationSize = 1)
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
    @JoinColumn(name = "aircraft_type_id")
    private AircraftType type;
    @Column(name = "reg_no")
    private String regNo;
    @ManyToOne
    @JoinColumn(name = "airline_id")
    private Airline airline;
    private Integer status;
    @Column(name = "position_latitude")
    private Double positionLatitude;
    @Column(name = "position_longitude")
    private Double positionLongitude;
    @ManyToOne
    @JoinColumn(name = "position_airport_id")
    private Airport positionAirport;

    @Override
    public String getEventLogCode() {
        return "aircraft";
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

    public AircraftType getType() {
        return type;
    }

    public void setType(AircraftType type) {
        this.type = type;
    }

    public String getRegNo() {
        return regNo;
    }

    public void setRegNo(String regNo) {
        this.regNo = regNo;
    }

    public Airline getAirline() {
        return airline;
    }

    public void setAirline(Airline airline) {
        this.airline = airline;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Double getPositionLatitude() {
        return positionLatitude;
    }

    public void setPositionLatitude(Double positionLatitude) {
        this.positionLatitude = positionLatitude;
    }

    public Double getPositionLongitude() {
        return positionLongitude;
    }

    public void setPositionLongitude(Double positionLongitude) {
        this.positionLongitude = positionLongitude;
    }

    public Airport getPositionAirport() {
        return positionAirport;
    }

    public void setPositionAirport(Airport positionAirport) {
        this.positionAirport = positionAirport;
    }

    public static class Status {
        public final static int Idle = 100;
        public final static int IdlePlanned = 101; // temporarily added status for stupid allocation needs
        public final static int PreFlight = 200;
        public final static int TaxiingOut = 300;
        public final static int Flying = 400;
        public final static int TaxiingIn = 500;
        public final static int PostFlight = 600;
    }
}
