/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.web.backend;

import com.google.common.collect.Lists;
import net.simforge.airways.AirwaysApp;
import net.simforge.airways.model.EventLogEntry;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.model.journey.Journey;
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
                result.add(transportFlight2map(transportFlight));
            }
        }

        return result;
    }

    private Map<String, Object> transportFlight2map(TransportFlight transportFlight) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", transportFlight.getId());
        map.put("dateOfFlight", transportFlight.getDateOfFlight().toString());
        map.put("flightNumber", transportFlight.getFlightNumber());
        map.put("departureDt", transportFlight.getDepartureDt().toString());
        map.put("departureTime", transportFlight.getDepartureDt().toLocalTime().toString());
        map.put("arrivalTime", transportFlight.getArrivalDt().toLocalTime().toString());
        map.put("fromIcao", transportFlight.getFromAirport().getIcao());
        map.put("toIcao", transportFlight.getToAirport().getIcao());
        map.put("status", transportFlight.getStatus().toString());
        map.put("soldTickets", transportFlight.getTotalTickets() - transportFlight.getFreeTickets());
        map.put("freeTickets", transportFlight.getFreeTickets());
        map.put("totalTickets", transportFlight.getTotalTickets());
        return map;
    }

    @RequestMapping("/transport-flight")
    public Map<String, Object> getTransportFlightData(@RequestParam(value = "id") int id) {
        Map<String, Object> result = new HashMap<>();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            TransportFlight transportFlight = session.load(TransportFlight.class, id);
            result.put("transportFlight", transportFlight2map(transportFlight));

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

            loadEventLogTail(session, TransportFlight.EventLogCode + ':' + id, result);
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
                personsList.add(person2map(person));
            }

            result.put("persons", personsList);
        }

        return result;
    }

    private Map<String, Object> person2map(Person person) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", person.getId());
        map.put("name", person.getName() + ' ' + person.getSurname());
        map.put("sex", person.getSex());
        map.put("type", person.getType().toString());
        map.put("status", person.getStatus().toString());
        map.put("origin", person.getOriginCity().getCityWithCountryName());
        map.put("location", person.getLocationCity() != null
                ? person.getLocationCity().getCityWithCountryName()
                : (person.getLocationAirport() != null
                ? person.getLocationAirport().getIcao()
                : null));
        return map;
    }

    @RequestMapping("/person")
    public Map<String, Object> getPersonData(@RequestParam(value = "id") int id) {
        Map<String, Object> result = new HashMap<>();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            Person person = session.load(Person.class, id);
            result.put("person", person2map(person));

            loadEventLogTail(session, Person.EventLogCode + ':' + id, result);
        }

        return result;
    }

    private void loadEventLogTail(Session session, String primaryId, Map<String, Object> result) {
        //noinspection unchecked,JpaQlInspection
        List<EventLogEntry> logEntries = session
                .createQuery("from EventLogEntry " +
                        "where primary_id = :id " +
                        "order by dt desc")
                .setMaxResults(16)
                .setParameter("id", primaryId)
                .list();

        List<EventLogEntry> normalOrder = Lists.reverse(logEntries);
        boolean hasMore = normalOrder.size() >= 16;
        if (hasMore) {
            normalOrder.remove(0);
        }

        result.put("log", logEntries2list(normalOrder));
        result.put("logHasMore", hasMore);
    }

    private List<Map<String, Object>> logEntries2list(List<EventLogEntry> normalOrder) {
        List<Map<String, Object>> log = new ArrayList<>();
        for (EventLogEntry logEntry : normalOrder) {
            Map<String, Object> map = new HashMap<>();
            map.put("dt", logEntry.getDt().toString());
            map.put("msg", logEntry.getMsg());
            log.add(map);
        }
        return log;
    }

    @RequestMapping("/get-full-log")
    public Map<String, Object> getFullLog(@RequestParam(value = "primary_id") String primaryId) {
        Map<String, Object> result = new HashMap<>();

        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            //noinspection unchecked,JpaQlInspection
            List<EventLogEntry> logEntries = session
                    .createQuery("from EventLogEntry " +
                            "where primary_id = :id " +
                            "order by dt")
                    .setParameter("id", primaryId)
                    .list();

            result.put("log", logEntries2list(logEntries));
        }

        return result;
    }
}
