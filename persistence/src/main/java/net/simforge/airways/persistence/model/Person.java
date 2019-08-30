/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model;

import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "Person")
@Table(name = "aw_person")
public class Person implements BaseEntity, /*HeartbeatObject,*/ EventLog.Loggable, Auditable {
    @SuppressWarnings("unused")
    public static final String EventLogCode = "person";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_person_id")
    @SequenceGenerator(name = "aw_person_id", sequenceName = "aw_person_id_seq", allocationSize = 1)
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

    @Column
    private Integer type;
    @Column
    private Integer status;
    @Column
    private String name;
    @Column
    private String surname;
    @Column
    private String sex;
    @ManyToOne
    @JoinColumn(name = "origin_city_id")
    private City originCity;
    @ManyToOne
    @JoinColumn(name = "journey_id")
    private Journey journey;
    @ManyToOne
    @JoinColumn(name = "position_city_id")
    private City positionCity;
    @ManyToOne
    @JoinColumn(name = "position_airport_id")
    private Airport positionAirport;

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

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public City getOriginCity() {
        return originCity;
    }

    public void setOriginCity(City originCity) {
        this.originCity = originCity;
    }

    public City getPositionCity() {
        return positionCity;
    }

    public void setPositionCity(City positionCity) {
        this.positionCity = positionCity;
    }

    public Journey getJourney() {
        return journey;
    }

    public void setJourney(Journey journey) {
        this.journey = journey;
    }

    public Airport getPositionAirport() {
        return positionAirport;
    }

    public void setPositionAirport(Airport positionAirport) {
        this.positionAirport = positionAirport;
    }

    public static class Type {
        public static final int Ordinal = 0;
        public static final int Excluded = 1;
    }

    public static class Status {
        public static final int ReadyToTravel = 0;
        public static final int Travelling    = 1;
        public static final int NoTravel      = 2;
    }
}
