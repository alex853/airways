/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ticketing;

import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.util.TimeMachine;
import org.hibernate.Session;

public class TicketingRequest {
    private Journey journey;
    private Session session;
    private TimeMachine timeMachine;

    private TicketingRequest() {
    }

    public static TicketingRequest get(Journey journey, Session session, TimeMachine timeMachine) {
        TicketingRequest request = new TicketingRequest();
        request.journey = journey;
        request.session = session;
        request.timeMachine = timeMachine;
        return request;
    }

    public Journey getJourney() {
        return journey;
    }

    public Session getSession() {
        return session;
    }

    public TimeMachine getTimeMachine() {
        return timeMachine;
    }
}
