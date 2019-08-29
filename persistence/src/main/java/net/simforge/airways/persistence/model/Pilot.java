package net.simforge.airways.persistence.model;

import net.simforge.airways.persistence.EventLog;
import net.simforge.commons.HeartbeatObject;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "aw_pilot")
public class Pilot implements HeartbeatObject, EventLog.Loggable {
    @SuppressWarnings("unused")
    public static final String EventLogCode = "pilot";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_pilot_id")
    @SequenceGenerator(name = "aw_pilot_id", sequenceName = "aw_pilot_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    private Integer status;
    @Column(name = "heartbeat_dt")
    private LocalDateTime heartbeatDt;
    @ManyToOne
    @JoinColumn(name = "person_id")
    private Person person;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getHeartbeatDt() {
        return heartbeatDt;
    }

    public void setHeartbeatDt(LocalDateTime heartbeatDt) {
        this.heartbeatDt = heartbeatDt;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    @Override
    public String toString() {
        return "Pilot{" +
                "id=" + id +
                ", status=" + status +
                '}';
    }

    public static class Status {
        public static final int Idle = 100;
        public static final int OnDuty = 200;
    }

}
