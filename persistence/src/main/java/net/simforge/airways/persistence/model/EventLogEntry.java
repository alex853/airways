package net.simforge.airways.persistence.model;

import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "aw_event_log_entry")
public class EventLogEntry implements BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_event_log_entry_id")
    @SequenceGenerator(name = "aw_event_log_entry_id", sequenceName = "aw_event_log_entry_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @Column
    private LocalDateTime dt;
    @Column(name = "primary_id")
    private String primaryId;
    @Column
    private String msg;
    @Column(name = "secondary_id_1")
    private String secondaryId1;
    @Column(name = "secondary_id_2")
    private String secondaryId2;
    @Column(name = "secondary_id_3")
    private String secondaryId3;

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

    public LocalDateTime getDt() {
        return dt;
    }

    public void setDt(LocalDateTime dt) {
        this.dt = dt;
    }

    public String getPrimaryId() {
        return primaryId;
    }

    public void setPrimaryId(String primaryId) {
        this.primaryId = primaryId;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getSecondaryId1() {
        return secondaryId1;
    }

    public void setSecondaryId1(String secondaryId1) {
        this.secondaryId1 = secondaryId1;
    }

    public String getSecondaryId2() {
        return secondaryId2;
    }

    public void setSecondaryId2(String secondaryId2) {
        this.secondaryId2 = secondaryId2;
    }

    public String getSecondaryId3() {
        return secondaryId3;
    }

    public void setSecondaryId3(String secondaryId3) {
        this.secondaryId3 = secondaryId3;
    }
}
