/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ticketing;

import net.simforge.airways.Airways;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.processengine.TimeMachine;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;

import java.util.Collections;
import java.util.List;

public class DirectConnectionsTicketing {
    public static List<TransportFlight> search(TicketingRequest request) {
        Journey journey = request.getJourney();
        Session session = request.getSession();
        TimeMachine timeMachine = request.getTimeMachine();

        BM.start("DirectConnectionsTicketing.search");
        try {
            //noinspection unchecked
            List<TransportFlight> flights = session.createQuery("select tp " +
                    "from TransportFlight tp " +
                    "where tp.fromAirport in (select ac.airport from Airport2City ac where ac.city = :fromCity and ac.dataset = :active) " +
                    "  and tp.toAirport in (select ac.airport from Airport2City ac where ac.city = :toCity and ac.dataset = :active) " +
                    "  and tp.departureDt >= :departureTimeSince " +
                    "  and tp.status = :scheduled " +
                    "  and tp.freeTickets >= :groupSize " +
                    "order by tp.departureDt")
                    .setEntity("fromCity", journey.getFromCity())
                    .setEntity("toCity", journey.getToCity())
                    .setParameter("active", Airways.ACTIVE_DATASET)
                    .setParameter("departureTimeSince", timeMachine.now().plusHours(3)) // three hours as reserve for all organizational matter
                    .setInteger("scheduled", TransportFlight.Status.Scheduled.code())
                    .setInteger("groupSize", journey.getGroupSize())
                    .list();

            if (flights.isEmpty()) {
                return Collections.emptyList();
            } else {
                TransportFlight flight = flights.get(0);
                return Collections.singletonList(flight);
            }
        } finally {
            BM.stop();
        }
    }
}
