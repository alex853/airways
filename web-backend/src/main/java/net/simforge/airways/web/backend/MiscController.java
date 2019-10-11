/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.web.backend;

import net.simforge.airways.AirwaysApp;
import net.simforge.airways.persistence.model.EventLogEntry;
import net.simforge.airways.persistence.model.Person;
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
    public Map<String, Object> getTransportFlightData(@RequestParam(value = "id") int id) {
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

    @RequestMapping("/journey")
    public Map<String, Object> getJourneyData(@RequestParam(value = "id") int id) {
        Map<String, Object> result = new HashMap<>();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            //noinspection unchecked,JpaQlInspection
            List<Person> persons = session
                    .createQuery("from Person " +
                            "where journey.id = :journeyId")
                    .setParameter("journeyId", id)
                    .list();

            List<Map<String, Object>> personsList = new ArrayList<>();

            for (Person person : persons) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", person.getId());
                map.put("name", person.getName() + ' ' + person.getSurname());
                map.put("sex", person.getSex());
                map.put("type", person.getType());
                map.put("status", person.getStatus());
                map.put("origin", person.getOriginCity().getCityWithCountryName());
                map.put("location", person.getLocationCity() != null
                        ? person.getLocationCity().getCityWithCountryName()
                        : (person.getLocationAirport() != null
                        ? person.getLocationAirport().getIcao()
                        : null));
                personsList.add(map);
            }

            result.put("persons", personsList);
        }

        return result;
    }

    @RequestMapping("/person")
    public Map<String, Object> getPersonData(@RequestParam(value = "id") int id) {
        Map<String, Object> result = new HashMap<>();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            //noinspection unchecked,JpaQlInspection
            List<EventLogEntry> logEntries = session
                    .createQuery("from EventLogEntry " +
                            "where primary_id = :id " +
                            "order by dt")
                    .setParameter("id", Person.EventLogCode + ':' + id)
                    .list();

            List<Map<String, Object>> log = new ArrayList<>();

            for (EventLogEntry logEntry : logEntries) {
                Map<String, Object> map = new HashMap<>();
                map.put("dt", logEntry.getDt().toString());
                map.put("msg", logEntry.getMsg());
                log.add(map);
            }

            result.put("log", log);
        }

        return result;
    }
}
