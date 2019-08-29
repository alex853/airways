package net.simforge.airways.persistence.model.flow;

import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.commons.HeartbeatObject;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="aw_city_flow")
public class CityFlow implements HeartbeatObject, EventLog.Loggable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_city_flow_id")
    @SequenceGenerator(name = "aw_city_flow_id", sequenceName = "aw_city_flow_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @ManyToOne
    @JoinColumn(name = "city_id")
    private City city;
    @Column(name = "heartbeat_dt")
    private LocalDateTime heartbeatDt;
    @Column
    private Integer status;
    @Column(name = "last_redistribution_dt")
    private LocalDateTime lastRedistributionDt;
    @Column
    private Double attraction;
    @Column(name = "units_threshold")
    private Double unitsThreshold;
    @Column(name = "default_availability")
    private Double defaultAvailability;

    @Override
    public String getEventLogCode() {
        return "cityFlow";
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

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public LocalDateTime getHeartbeatDt() {
        return heartbeatDt;
    }

    public void setHeartbeatDt(LocalDateTime heartbeatDt) {
        this.heartbeatDt = heartbeatDt;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getLastRedistributionDt() {
        return lastRedistributionDt;
    }

    public void setLastRedistributionDt(LocalDateTime lastRedistributionDt) {
        this.lastRedistributionDt = lastRedistributionDt;
    }

    public Double getAttraction() {
        return attraction;
    }

    public void setAttraction(Double attraction) {
        this.attraction = attraction;
    }

    public Double getUnitsThreshold() {
        return unitsThreshold;
    }

    public void setUnitsThreshold(Double unitsThreshold) {
        this.unitsThreshold = unitsThreshold;
    }

    public Double getDefaultAvailability() {
        return defaultAvailability;
    }

    public void setDefaultAvailability(Double defaultAvailability) {
        this.defaultAvailability = defaultAvailability;
    }

    public static class Status {
        public static final int Active = 0;
        public static final int Inactive = 1;
        public static final int RedistributeThenActivate = 2;
        public static final int ActiveNeedsRedistribution = 3;
    }
}
