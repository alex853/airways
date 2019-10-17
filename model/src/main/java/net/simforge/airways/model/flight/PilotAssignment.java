/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.model.flight;

import net.simforge.airways.model.Pilot;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "PilotAssignment")
@Table(name = "aw_pilot_assignment")
public class PilotAssignment implements BaseEntity, Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_pilot_assignment_id")
    @SequenceGenerator(name = "aw_pilot_assignment_id", sequenceName = "aw_pilot_assignment_id_seq", allocationSize = 1)
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
    @JoinColumn(name = "pilot_id")
    private Pilot pilot;
    private String role; // Captain, First Officer, etc

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

    public Pilot getPilot() {
        return pilot;
    }

    public void setPilot(Pilot pilot) {
        this.pilot = pilot;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "PilotAssignment{" +
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
