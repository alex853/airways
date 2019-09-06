/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model;

import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.flow.City2CityFlow;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "Journey")
@Table(name="aw_journey")
public class Journey implements BaseEntity, /*HeartbeatObject,*/ Auditable, EventLog.Loggable {
    public static final String EventLogCode = "journey";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_journey_id")
    @SequenceGenerator(name = "aw_journey_id", sequenceName = "aw_journey_id_seq", allocationSize = 1)
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
    @JoinColumn(name = "c2c_flow_id")
    private City2CityFlow c2cFlow;
    @ManyToOne
    @JoinColumn(name = "from_city_id")
    private City fromCity;
    @ManyToOne
    @JoinColumn(name = "to_city_id")
    private City toCity;
    @Column(name = "group_size")
    private Integer groupSize;
    @Column
    private Integer status;
//    @Column(name = "heartbeat_dt")
//    private LocalDateTime heartbeatDt;
    @Column(name = "expiration_dt")
    private LocalDateTime expirationDt;
//    @Column
//    private int itineraryId;
    @ManyToOne
    @JoinColumn(name = "current_city_id")
    private City currentCity;
    @ManyToOne
    @JoinColumn(name = "current_airport_id")
    private Airport currentAirport;
//    @Column
//    private Flight currentFlight;

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

    public City2CityFlow getC2cFlow() {
        return c2cFlow;
    }

    public void setC2cFlow(City2CityFlow c2cFlow) {
        this.c2cFlow = c2cFlow;
    }

    public City getFromCity() {
        return fromCity;
    }

    public void setFromCity(City fromCity) {
        this.fromCity = fromCity;
    }

    public City getToCity() {
        return toCity;
    }

    public void setToCity(City toCity) {
        this.toCity = toCity;
    }

    public Integer getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(Integer groupSize) {
        this.groupSize = groupSize;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

/*    public LocalDateTime getHeartbeatDt() {
        return heartbeatDt;
    }

    public void setHeartbeatDt(LocalDateTime heartbeatDt) {
        this.heartbeatDt = heartbeatDt;
    }*/

    public LocalDateTime getExpirationDt() {
        return expirationDt;
    }

    public void setExpirationDt(LocalDateTime expirationDt) {
        this.expirationDt = expirationDt;
    }

    public City getCurrentCity() {
        return currentCity;
    }

    public void setCurrentCity(City currentCity) {
        this.currentCity = currentCity;
    }

    public Airport getCurrentAirport() {
        return currentAirport;
    }

    public void setCurrentAirport(Airport currentAirport) {
        this.currentAirport = currentAirport;
    }

    public static class Status {
        public static final int LookingForPersons   = 1000;
        public static final int LookingForTickets   = 2000;
        public static final int CouldNotFindTickets = 2900;
        public static final int WaitingForFlight    = 3000;
        public static final int TransferToAirport = 0;
        public static final int WaitingForCheckin = 0;
        public static final int WaitingForBoarding = 0;
        public static final int TooLateToBoard      = 3900;
        public static final int OnBoard             = 4000;
        public static final int JustArrived             = 5000;
        public static final int TransferToCity = 0;
        public static final int ItinerariesDone     = 6000;
        public static final int Done                = 9000;
        public static final int Died                = 9999;
    }
}
