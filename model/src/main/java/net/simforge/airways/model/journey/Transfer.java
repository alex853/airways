/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.model.journey;

import net.simforge.airways.EventLog;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "JourneyTransfer")
@Table(name = "aw_journey_transfer")
public class Transfer implements BaseEntity, Auditable, EventLog.Loggable {
    public static final String EventLogCode = "journey-transfer";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_journey_transfer_id")
    @SequenceGenerator(name = "aw_journey_transfer_id", sequenceName = "aw_journey_transfer_id_seq", allocationSize = 1)
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
    @JoinColumn(name = "journey_id")
    private Journey journey;

    @ManyToOne
    @JoinColumn(name = "to_city_id")
    private City toCity;
    @ManyToOne
    @JoinColumn(name = "to_airport_id")
    private Airport toAirport;
    @Column
    private Double distance;
    @Column
    private Integer duration;
    @Column(name = "on_started_status")
    private Integer onStartedStatus;
    @Column(name = "on_finished_status")
    private Integer onFinishedStatus;
    @Column(name = "on_finished_event")
    private String onFinishedEvent;

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

    public Journey getJourney() {
        return journey;
    }

    public void setJourney(Journey journey) {
        this.journey = journey;
    }

    public City getToCity() {
        return toCity;
    }

    public void setToCity(City toCity) {
        this.toCity = toCity;
    }

    public Airport getToAirport() {
        return toAirport;
    }

    public void setToAirport(Airport toAirport) {
        this.toAirport = toAirport;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Integer getOnStartedStatus() {
        return onStartedStatus;
    }

    public void setOnStartedStatus(Integer onStartedStatus) {
        this.onStartedStatus = onStartedStatus;
    }

    public Integer getOnFinishedStatus() {
        return onFinishedStatus;
    }

    public void setOnFinishedStatus(Integer onFinishedStatus) {
        this.onFinishedStatus = onFinishedStatus;
    }

    public String getOnFinishedEvent() {
        return onFinishedEvent;
    }

    public void setOnFinishedEvent(String onFinishedEvent) {
        this.onFinishedEvent = onFinishedEvent;
    }
}
