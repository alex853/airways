package net.simforge.airways.model;

//import net.simforge.commons.persistence.BaseEntity;
//import net.simforge.commons.persistence.Column;
//import net.simforge.commons.persistence.Table;

//@Table(name = "aw_pg_itinerary")
public class PassengerGroupItinerary /*extends BaseEntity*/ {
    //    @Column
    private int groupId;

    //    @Column
    private int itemOrder;

    //    @Column
    private int flightId;

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public int getItemOrder() {
        return itemOrder;
    }

    public void setItemOrder(int itemOrder) {
        this.itemOrder = itemOrder;
    }

    public int getFlightId() {
        return flightId;
    }

    public void setFlightId(int flightId) {
        this.flightId = flightId;
    }
}