/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model.flight;

import net.simforge.airways.persistence.model.aircraft.Aircraft;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "AircraftAssignment")
@Table(name = "aw_aircraft_assignment")
public class AircraftAssignment implements BaseEntity, Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_aircraft_assignment_id")
    @SequenceGenerator(name = "aw_aircraft_assignment_id", sequenceName = "aw_aircraft_assignment_id_seq", allocationSize = 1)
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
    @JoinColumn(name = "flight_id")
    private Flight flight;
    @ManyToOne
    @JoinColumn(name = "aircraft_id")
    private Aircraft aircraft;

    private Integer status;

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

    public Flight getFlight() {
        return flight;
    }

    public void setFlight(Flight flight) {
        this.flight = flight;
    }

    public Aircraft getAircraft() {
        return aircraft;
    }

    public void setAircraft(Aircraft aircraft) {
        this.aircraft = aircraft;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "AircraftAssignment{" +
                "id=" + id +
                '}';
    }

    public static class Status {
        public static final int Assigned = 100;
        public static final int InProgress = 200;
        public static final int Done = 1000;
        public static final int Cancelled = 9999;
    }
}
