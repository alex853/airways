/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.model.journey;

import net.simforge.airways.EventLog;
import net.simforge.airways.model.flow.City2CityFlow;
import net.simforge.airways.model.geo.City;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "Journey")
@Table(name = "aw_journey")
public class Journey implements BaseEntity, Auditable, EventLog.Loggable {
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
    @ManyToOne
    @JoinColumn(name = "itinerary_id")
    private Itinerary itinerary;
    @ManyToOne
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

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

    public Status getStatus() {
        return status != null ? Status.byCode(status) : null;
    }

    public void setStatus(Status status) {
        this.status = status != null ? status.code() : null;
    }

    public Itinerary getItinerary() {
        return itinerary;
    }

    public void setItinerary(Itinerary itinerary) {
        this.itinerary = itinerary;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public void setTransfer(Transfer transfer) {
        this.transfer = transfer;
    }

    @Override
    public String toString() {
        return "Journey{" +
                "id = " + id +
                ", groupSize = " + groupSize +
                ", status = " + status +
                '}';
    }

    public enum Status {
        LookingForPersons(1000),
        LookingForTickets(2000),
        WaitingForFlight(3000),
        TransferToAirport(3100),
        WaitingForCheckin(3200),
        WaitingForBoarding(3300),
        OnBoard(4000),
        JustArrived(5000),
        TransferToCity(5100),
        ItinerariesDone(6000),
        Finished(7777),
        CouldNotFindPersons(9991),
        CouldNotFindTickets(9992),
        TooLateToBoard(9993),
        Terminated(9999);

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
