/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.ops.CommonOps;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.ops.PersonOps;
import net.simforge.airways.persistence.Airways;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.airways.persistence.model.flight.TimetableRow;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.persistence.model.flow.City2CityFlow;
import net.simforge.airways.persistence.model.flow.CityFlow;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.persistence.model.geo.Airport2City;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.airways.persistence.model.geo.Country;
import net.simforge.airways.util.FlightTimeline;
import net.simforge.airways.util.SimpleFlight;
import net.simforge.airways.util.TimeMachine;
import net.simforge.commons.gckls2com.GC;
import net.simforge.commons.gckls2com.GCAirport;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.misc.JavaTime;
import net.simforge.commons.misc.Weekdays;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

public class TestWorld {
    public static final LocalDateTime BEGINNING_OF_TIME = LocalDateTime.of(2018, 1, 1, 0, 0);

    private SessionFactory sessionFactory;
    private Country ukCountry;
    private City londonCity;
    private City manchesterCity;
    private City dublinCity;
    private Airport egllAirport;
    private Airport egccAirport;
    private AircraftType a320type;

    public TestWorld(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void createGeo() {
        ukCountry = createCountry("United kingdom", "GB");

        londonCity = createCity("United kingdom", "London", 51, 0);
        manchesterCity = createCity("United kingdom", "Manchester", 53, -2);
        dublinCity = createCity("United kingdom", "Dublin", 53, -6);

        egllAirport = importAirportFromGC("EGLL");
        egccAirport = importAirportFromGC("EGCC");

        linkAirport2City(egllAirport, londonCity);
        linkAirport2City(egccAirport, manchesterCity);
    }

    public void createAircraftTypes() {
        a320type = TestRefData.getA320Data();
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, a320type);
        }
    }

    public Country getUkCountry() {
        return ukCountry;
    }

    public City getLondonCity() {
        return londonCity;
    }

    public City getManchesterCity() {
        return manchesterCity;
    }

    public City getDublinCity() {
        return dublinCity;
    }

    public Airport getEgllAirport() {
        return egllAirport;
    }

    public Airport getEgccAirport() {
        return egccAirport;
    }

    public AircraftType getA320Type() {
        return a320type;
    }

    @SuppressWarnings("SameParameterValue")
    private City createCity(String countryName, String cityName, double lat, double lon) {
        try (Session session = sessionFactory.openSession()) {
            City city = new City();
            city.setCountry(CommonOps.countryByName(session, countryName));
            city.setName(cityName);
            city.setPopulation(1000);
            city.setLatitude(lat);
            city.setLongitude(lon);
            city.setDataset(Airways.ACTIVE_DATASET);

            HibernateUtils.saveAndCommit(session, city);

            return city;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private Country createCountry(String name, String code) {
        try (Session session = sessionFactory.openSession()) {
            Country country = new Country();
            country.setName(name);
            country.setCode(code);

            HibernateUtils.saveAndCommit(session, country);

            return country;
        }
    }

    public Journey createJourney(City fromCity, City toCity, int groupSize) {
        Journey[] resultedJourney = new Journey[1];
        try (Session session = sessionFactory.openSession()) {
            City2CityFlow city2CityFlow = getOrCreateC2CFlow(fromCity, toCity);

            HibernateUtils.transaction(session, () -> {
                city2CityFlow.setNextGroupSize(groupSize);

                resultedJourney[0] = JourneyOps.create(session, city2CityFlow);
            });
        }

        return resultedJourney[0];
    }

    public void createPerson(City originCity) {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {
                PersonOps.createOrdinalPerson(session, originCity);
            });
        }
    }

    public TimetableRow createTimetableRow(String flightNumber, Airport egll, Airport egcc, String departureTime, AircraftType a320type) {
        SimpleFlight simpleFlight = SimpleFlight.forRoute(egll.getCoords(), egcc.getCoords(), a320type);

        Duration flyingTime = simpleFlight.getTotalTime();
        FlightTimeline timeline = FlightTimeline.byFlyingTime(flyingTime);
        Duration flightDuration = timeline.getScheduledDuration(timeline.getBlocksOff(), timeline.getBlocksOn());

        TimetableRow timetableRow = new TimetableRow();
        timetableRow.setNumber(flightNumber);
        timetableRow.setFromAirport(egll);
        timetableRow.setToAirport(egcc);
        timetableRow.setDepartureTime(departureTime);
        timetableRow.setDuration(JavaTime.toHhmm(flightDuration));
        timetableRow.setWeekdays(Weekdays.wholeWeek().toString());
        timetableRow.setAircraftType(a320type);
        timetableRow.setTotalTickets(160);
        timetableRow.setHorizon(0);

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, timetableRow);
        }

        return timetableRow;
    }

    private City2CityFlow getOrCreateC2CFlow(City fromCity, City toCity) {
        CityFlow fromCityFlow = getOrCreateCityFlow(fromCity);
        CityFlow toCityFlow = getOrCreateCityFlow(toCity);
        try (Session session = sessionFactory.openSession()) {
            City2CityFlow city2cityFlow = (City2CityFlow) session.createQuery("from City2CityFlow f " +
                    "where f.fromFlow = :fromFlow " +
                    "  and f.toFlow = :toFlow")
                    .setEntity("fromFlow", fromCityFlow)
                    .setEntity("toFlow", toCityFlow)
                    .setMaxResults(1)
                    .uniqueResult();
            if (city2cityFlow != null) {
                return city2cityFlow;
            }

            city2cityFlow = new City2CityFlow();
            city2cityFlow.setFromFlow(fromCityFlow);
            city2cityFlow.setToFlow(toCityFlow);
            HibernateUtils.saveAndCommit(session, city2cityFlow);
            return city2cityFlow;
        }
    }

    private CityFlow getOrCreateCityFlow(City city) {
        try (Session session = sessionFactory.openSession()) {
            CityFlow cityFlow = (CityFlow) session.createQuery("from CityFlow f " +
                    "where f.city = :city")
                    .setEntity("city", city)
                    .setMaxResults(1)
                    .uniqueResult();
            if (cityFlow != null) {
                return cityFlow;
            }

            cityFlow = new CityFlow();
            cityFlow.setCity(city);
            HibernateUtils.saveAndCommit(session, cityFlow);
            return cityFlow;
        }
    }

    public TransportFlight createTransportFlight(String flightNumber, Airport fromAirport, Airport toAirport, AircraftType aircraftType, LocalDateTime departureDt, int totalTickets) {
        TransportFlight transportFlight = new TransportFlight();

        SimpleFlight simpleFlight = SimpleFlight.forRoute(fromAirport.getCoords(), toAirport.getCoords(), aircraftType);
        FlightTimeline timeline = FlightTimeline.byFlyingTime(simpleFlight.getTotalTime());
        Duration flightDuration = timeline.getScheduledDuration(timeline.getBlocksOff(), timeline.getBlocksOn());

//        transportFlight.setTimetableRow(timetableRow);
        transportFlight.setDateOfFlight(departureDt.toLocalDate());
        transportFlight.setNumber(flightNumber);
        transportFlight.setFromAirport(fromAirport);
        transportFlight.setToAirport(toAirport);
        transportFlight.setDepartureDt(departureDt);
        transportFlight.setArrivalDt(transportFlight.getDepartureDt().plus(flightDuration));
        transportFlight.setStatus(TransportFlight.Status.Scheduled);
        transportFlight.setTotalTickets(totalTickets);
        transportFlight.setFreeTickets(totalTickets);

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, transportFlight);
        }

        return transportFlight;
    }

    private Airport importAirportFromGC(String icao) {
        GCAirport gcAirport;
        try {
            gcAirport = GC.findAirport(icao);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Airport airport = new Airport();
        airport.setIcao(gcAirport.getIcao());
        airport.setIata(gcAirport.getIata());
        airport.setName(gcAirport.getName());
        airport.setLatitude(gcAirport.getLat());
        airport.setLongitude(gcAirport.getLon());

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, airport);
        }
        return airport;
    }

    private void linkAirport2City(Airport airport, City city) {
        Airport2City airport2City = new Airport2City();
        airport2City.setAirport(airport);
        airport2City.setCity(city);

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, airport2City);
        }
    }
}
