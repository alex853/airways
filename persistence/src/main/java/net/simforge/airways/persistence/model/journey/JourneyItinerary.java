/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model.journey;

import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "JourneyItinerary")
@Table(name = "aw_journey_itinerary")
public class JourneyItinerary implements BaseEntity, Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_journey_itinerary_id")
    @SequenceGenerator(name = "aw_journey_itinerary_id", sequenceName = "aw_journey_itinerary_id_seq", allocationSize = 1)
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
    @Column(name = "item_order")
    private Integer itemOrder;
    @ManyToOne
    @JoinColumn(name = "flight_id")
    private TransportFlight flight;

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

    public Integer getItemOrder() {
        return itemOrder;
    }

    public void setItemOrder(Integer itemOrder) {
        this.itemOrder = itemOrder;
    }

    public TransportFlight getFlight() {
        return flight;
    }

    public void setFlight(TransportFlight flight) {
        this.flight = flight;
    }
}