/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model;

import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "Person")
@Table(name = "aw_person")
public class Person implements BaseEntity, EventLog.Loggable, Auditable {
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
    @JoinColumn(name = "location_city_id")
    private City locationCity;
    @ManyToOne
    @JoinColumn(name = "location_airport_id")
    private Airport locationAirport;

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

    public Type getType() {
        return type != null ? Type.byCode(type) : null;
    }

    public void setType(Type type) {
        this.type = type != null ? type.code() : null;
    }

    public Status getStatus() {
        return status != null ? Status.byCode(status) : null;
    }

    public void setStatus(Status status) {
        this.status = status != null ? status.code() : null;
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

    public Journey getJourney() {
        return journey;
    }

    public void setJourney(Journey journey) {
        this.journey = journey;
    }

    public City getLocationCity() {
        return locationCity;
    }

    public void setLocationCity(City locationCity) {
        this.locationCity = locationCity;
    }

    public Airport getLocationAirport() {
        return locationAirport;
    }

    public void setLocationAirport(Airport locationAirport) {
        this.locationAirport = locationAirport;
    }

    @Override
    public String toString() {
        return "Person{id = " + id + ", name=" + name + " " + surname + "}";
    }

    public enum Type {
        Ordinal(0),
        Excluded(1);

        private final int code;

        Type(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Type byCode(int code) {
            for (Type value : values()) {
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

    public enum Status {
        Idle(0),
        OnJourney(1);

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
