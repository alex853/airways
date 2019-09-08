/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ticketing;

import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.util.TimeMachine;
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
                    "where tp.fromAirport = (select ac.airport from Airport2City ac where ac.city = :fromCity) " +
                    "and tp.toAirport = (select ac.airport from Airport2City ac where ac.city = :toCity) " +
                    "and tp.departureDt >= :departureTime " +
                    "and tp.status = :scheduled " +
                    "and tp.freeTickets >= :groupSize " +
                    "order by tp.departureDt")
                    .setEntity("fromCity", journey.getFromCity())
                    .setEntity("toCity", journey.getToCity())
                    .setParameter("departureTime", timeMachine.now().plusHours(3))
                    .setInteger("scheduled", TransportFlight.Status.Scheduled)
                    .setInteger("groupSize", journey.getGroupSize())
                    .list();

            if (flights.isEmpty()) {
                return null;
            } else {
                TransportFlight flight = flights.get(0);
                return Collections.singletonList(flight);
            }
        } finally {
            BM.stop();
        }
    }
}
