/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.web.backend;

import net.simforge.airways.AirwaysApp;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.persistence.model.journey.Journey;
import org.hibernate.Session;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/misc")
@CrossOrigin
public class MiscController {

    @RequestMapping("/ticket-sales")
    public List<Map<String, Object>> getTicketSales() {
        List<Map<String, Object>> result = new ArrayList<>();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            //noinspection unchecked,JpaQlInspection
            List<TransportFlight> transportFlights = session
                    .createQuery("from TransportFlight order by departureDt")
                    .list();

            for (TransportFlight transportFlight : transportFlights) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", transportFlight.getId());
                map.put("dateOfFlight", transportFlight.getDateOfFlight().toString());
                map.put("flightNumber", transportFlight.getNumber());
                map.put("departureDt", transportFlight.getDepartureDt().toString());
                map.put("departureTime", transportFlight.getDepartureDt().toLocalTime().toString());
                map.put("fromIcao", transportFlight.getFromAirport().getIcao());
                map.put("toIcao", transportFlight.getToAirport().getIcao());
                map.put("status", transportFlight.getStatus());
                map.put("soldTickets", transportFlight.getTotalTickets() - transportFlight.getFreeTickets());
                map.put("freeTickets", transportFlight.getFreeTickets());
                map.put("totalTickets", transportFlight.getTotalTickets());
                result.add(map);
            }
        }

        return result;
    }

    @RequestMapping("/transport-flight")
    public Map<String, Object> getTransportFlightData(@RequestParam(value="id") int id) {
        Map<String, Object> result = new HashMap<>();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            //noinspection unchecked,JpaQlInspection
            List<Journey> journeys = session
                    .createQuery("select i.journey from JourneyItinerary i " +
                            "where i.flight.id = :flightId")
                    .setParameter("flightId", id)
                    .list();

            List<Map<String, Object>> journeysList = new ArrayList<>();

            for (Journey journey : journeys) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", journey.getId());
                map.put("fromCity", journey.getFromCity().getName());
                map.put("toCity", journey.getToCity().getName());
                map.put("groupSize", journey.getGroupSize());
                map.put("status", journey.getStatus());
                map.put("itineraryCheck", journey.getItinerary() == null
                        ? "EMPTY"
                        : (journey.getItinerary().getFlight().getId() == id
                            ? "ID OK" : "Another ID"));
                journeysList.add(map);
            }

            result.put("journeys", journeysList);
        }

        return result;
    }
}
