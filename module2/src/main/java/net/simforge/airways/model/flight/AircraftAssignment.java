package net.simforge.airways.model.flight;

import net.simforge.airways.model.aircraft.Aircraft;

public interface AircraftAssignment /*extends BaseEntity, Auditable*/ {

    Flight getFlight();

    void setFlight(Flight flight);

    Aircraft getAircraft();

    void setAircraft(Aircraft aircraft);

    Integer getStatus();

    void setStatus(Integer status);

    class Status {
        public static final int Assigned = 100;
        public static final int InProgress = 200;
        public static final int Done = 1000;
        public static final int Cancelled = 9999;
    }
}
