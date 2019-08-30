/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.model.flight;

import net.simforge.airways.model.person.Pilot;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;

@Deprecated
public interface PilotAssignment extends BaseEntity, Auditable {

    Flight getFlight();

    void setFlight(Flight flight);

    Pilot getPilot();

    void setPilot(Pilot pilot);

    String getRole();

    void setRole(String role);

    Integer getStatus();

    void setStatus(Integer status);

    class Status {
        public static final int Assigned = 100;
        public static final int InProgress = 200;
        public static final int Done = 1000;
        public static final int Cancelled = 9999;
    }
}
