package net.simforge.airways.persistence.model.flow;

import net.simforge.commons.HeartbeatObject;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="aw_city2city_flow_stats")
public class City2CityFlowStats implements HeartbeatObject {
    public static final String EventLogCode = "c2cFStats";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_city2city_flow_stats_id")
    @SequenceGenerator(name = "aw_city2city_flow_stats_id", sequenceName = "aw_city2city_flow_stats_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @ManyToOne
    @JoinColumn(name = "c2c_flow_id")
    private City2CityFlow c2cFlow;
    @Column
    private LocalDate date;
    @Column(name = "heartbeat_dt")
    private LocalDateTime heartbeatDt;
    @Column(name = "availability_before")
    private Double availabilityBefore;
    @Column(name = "availability_after")
    private Double availabilityAfter;
    @Column(name = "availability_delta")
    private Double availabilityDelta;
    @Column(name = "no_tickets")
    private Integer noTickets;
    @Column(name = "tickets_bought")
    private Integer ticketsBought;
    @Column
    private Integer travelled;

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

    public City2CityFlow getC2cFlow() {
        return c2cFlow;
    }

    public void setC2cFlow(City2CityFlow c2cFlow) {
        this.c2cFlow = c2cFlow;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalDateTime getHeartbeatDt() {
        return heartbeatDt;
    }

    public void setHeartbeatDt(LocalDateTime heartbeatDt) {
        this.heartbeatDt = heartbeatDt;
    }

    public Double getAvailabilityBefore() {
        return availabilityBefore;
    }

    public void setAvailabilityBefore(Double availabilityBefore) {
        this.availabilityBefore = availabilityBefore;
    }

    public Double getAvailabilityAfter() {
        return availabilityAfter;
    }

    public void setAvailabilityAfter(Double availabilityAfter) {
        this.availabilityAfter = availabilityAfter;
    }

    public Double getAvailabilityDelta() {
        return availabilityDelta;
    }

    public void setAvailabilityDelta(Double availabilityDelta) {
        this.availabilityDelta = availabilityDelta;
    }

    public Integer getNoTickets() {
        return noTickets;
    }

    public void setNoTickets(Integer noTickets) {
        this.noTickets = noTickets;
    }

    public Integer getTicketsBought() {
        return ticketsBought;
    }

    public void setTicketsBought(Integer ticketsBought) {
        this.ticketsBought = ticketsBought;
    }

    public Integer getTravelled() {
        return travelled;
    }

    public void setTravelled(Integer travelled) {
        this.travelled = travelled;
    }
}
