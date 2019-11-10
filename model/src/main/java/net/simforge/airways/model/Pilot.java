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

    private Integer type;
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

    public enum Type {
        NonPlayerCharacter(0),
        PlayerCharacter(1);

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
        Idle(100),
        IdlePlanned(101), // temporarily added status for stupid allocation needs
        OnDuty(200);

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
