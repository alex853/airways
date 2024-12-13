/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.model.flow;

import net.simforge.airways.EventLog;
import net.simforge.commons.HeartbeatObject;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="aw_city2city_flow")
public class City2CityFlow implements BaseEntity, HeartbeatObject, EventLog.Loggable, Auditable {
    public static final String EventLogCode = "c2cFlow";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_city2city_flow_id")
    @SequenceGenerator(name = "aw_city2city_flow_id", sequenceName = "aw_city2city_flow_id_seq", allocationSize = 1)
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
    @JoinColumn(name = "from_flow_id")
    private CityFlow fromFlow;
    @ManyToOne
    @JoinColumn(name = "to_flow_id")
    private CityFlow toFlow;
    @Column(name = "heartbeat_dt")
    private LocalDateTime heartbeatDt;
    @Column
    private Boolean active;
    @Column
    private Double units;
    @Column
    private Double percentage;
    @Column
    private Double availability;
    @Column(name = "next_group_size")
    private Integer nextGroupSize;
    @Column(name = "accumulated_flow")
    private Double accumulatedFlow;
    @Column(name = "accumulated_flow_dt")
    private LocalDateTime accumulatedFlowDt;

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

    public CityFlow getFromFlow() {
        return fromFlow;
    }

    public void setFromFlow(CityFlow fromFlow) {
        this.fromFlow = fromFlow;
    }

    public CityFlow getToFlow() {
        return toFlow;
    }

    public void setToFlow(CityFlow toFlow) {
        this.toFlow = toFlow;
    }

    @Override
    public LocalDateTime getHeartbeatDt() {
        return heartbeatDt;
    }

    @Override
    public void setHeartbeatDt(LocalDateTime heartbeatDt) {
        this.heartbeatDt = heartbeatDt;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Double getUnits() {
        return units;
    }

    public void setUnits(Double units) {
        this.units = units;
    }

    public Double getPercentage() {
        return percentage;
    }

    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }

    public Double getAvailability() {
        return availability;
    }

    public void setAvailability(Double availability) {
        this.availability = availability;
    }

    public Integer getNextGroupSize() {
        return nextGroupSize;
    }

    public void setNextGroupSize(Integer nextGroupSize) {
        this.nextGroupSize = nextGroupSize;
    }

    public Double getAccumulatedFlow() {
        return accumulatedFlow;
    }

    public void setAccumulatedFlow(Double accumulatedFlow) {
        this.accumulatedFlow = accumulatedFlow;
    }

    public LocalDateTime getAccumulatedFlowDt() {
        return accumulatedFlowDt;
    }

    public void setAccumulatedFlowDt(LocalDateTime accumulatedFlowDt) {
        this.accumulatedFlowDt = accumulatedFlowDt;
    }
}
