/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.model;

import net.simforge.airways.EventLog;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "Pilot")
@Table(name = "aw_pilot")
public class Pilot implements BaseEntity, EventLog.Loggable, Auditable {
    @SuppressWarnings("unused")
    public static final String EventLogCode = "pilot";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_pilot_id")
    @SequenceGenerator(name = "aw_pilot_id", sequenceName = "aw_pilot_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @SuppressWarnings("unused")
    @Column(name = "create_dt")
    private LocalDateTime createDt;
    @SuppressWarnings("unused")
    @Column(name = "modify_dt")
    private LocalDateTime modifyDt;

    private Integer status;
    @ManyToOne
    @JoinColumn(name = "person_id")
    private Person person;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    @Override
    public String toString() {
        return "Pilot{id=" + id + ", status=" + status + '}';
    }

    public static class Status {
        public static final int Idle = 100;
        public static final int IdlePlanned = 101; // temporarily added status for stupid allocation needs
        public static final int OnDuty = 200;
    }

}
