/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder;

import net.simforge.airways.ops.CommonOps;
import net.simforge.airways.persistence.Airways;
import net.simforge.airways.persistence.model.Airline;
import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.airways.persistence.model.flight.TimetableRow;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.persistence.model.geo.Airport2City;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.airways.persistence.model.geo.Country;
import net.simforge.airways.util.FlightTimeline;
import net.simforge.airways.util.SimpleFlight;
import net.simforge.commons.io.Csv;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;

public class BuildTimetable {
    private static final Logger logger = LoggerFactory.getLogger(BuildTimetable.class.getName());
    private static Map<String, Integer> icao2size;

    private static Random random = new Random();

    public static void main(String[] args) throws IOException {
        Csv csv = Csv.load(new File("./data/icaodata.csv"));
        logger.info("source dataset contains {} airports", csv.rowCount());

        icao2size = new HashMap<>();

        for (int i = 0; i < csv.rowCount(); i++) {
            String icao = csv.value(i, "icao");
            String sizeStr = csv.value(i, "size");
            icao2size.put(icao, Integer.parseInt(sizeStr));
        }

        try (SessionFactory sessionFactory = Airways.buildSessionFactory();
            Session session = sessionFactory.openSession()) {

            buildMidRangeHub(session, "ZZ", "Russia", "Moskva", 200, 799,  "A320", 30);
            buildMidRangeHub(session, "ZZ", "Russia", "Moskva", 800, 1500, "A320", 20);
            buildMidRangeHub(session, "ZZ", "United kingdom", "London", 100, 1500, "A320", 50);
            buildMidRangeHub(session, "ZZ", "United states", "New york", 100, 2000, "A320", 50);
            buildMidRangeHub(session, "ZZ", "United states", "Los angeles", 100, 2000, "A320", 50);
            buildMidRangeHub(session, "ZZ", "Venezuela", "Caracas", 100, 2000, "A320", 50);
            buildMidRangeHub(session, "ZZ", "Brazil", "Sao paulo", 100, 2000, "A320", 50);
            buildMidRangeHub(session, "ZZ", "India", "Dilli", 100, 2000, "A320", 50);
            buildMidRangeHub(session, "ZZ", "China", "Shanghai", 100, 2000, "A320", 50);
            buildMidRangeHub(session, "ZZ", "Singapore", "Singapore", 100, 2000, "A320", 50);
            buildMidRangeHub(session, "ZZ", "Australia", "Sydney", 100, 2000, "A320", 50);

            //B744
            //"United kingdom", "London" -> "United states", "New york"
            addRoundtripTimetableRow(session, "WW", "B744",
                    findAirportForCity(session, "United kingdom", "London"),
                    findAirportForCity(session, "United states", "New york"),
                    "05:00");
            //"United kingdom", "London" -> "United states", "Los angeles"
            addRoundtripTimetableRow(session, "WW", "B744",
                    findAirportForCity(session, "United kingdom", "London"),
                    findAirportForCity(session, "United states", "Los angeles"),
                    "05:10");
            //"United kingdom", "London" -> "Brazil", "Sao paulo"
            addRoundtripTimetableRow(session, "WW", "B744",
                    findAirportForCity(session, "United kingdom", "London"),
                    findAirportForCity(session, "Brazil", "Sao paulo"),
                    "05:20");
            //"United kingdom", "London" -> "China", "Shanghai"
            addRoundtripTimetableRow(session, "WW", "B744",
                    findAirportForCity(session, "United kingdom", "London"),
                    findAirportForCity(session, "China", "Shanghai"),
                    "05:30");
            //"United kingdom", "London" -> "Singapore", "Singapore"
            addRoundtripTimetableRow(session, "WW", "B744",
                    findAirportForCity(session, "United kingdom", "London"),
                    findAirportForCity(session, "Singapore", "Singapore"),
                    "05:40");
            //"Australia", "Sydney" -> "Singapore", "Singapore"
            addRoundtripTimetableRow(session, "WW", "B744",
                    findAirportForCity(session, "Australia", "Sydney"),
                    findAirportForCity(session, "Singapore", "Singapore"),
                    "10:00");
            //"Australia", "Sydney" -> "United states", "Los angeles"
            addRoundtripTimetableRow(session, "WW", "B744",
                    findAirportForCity(session, "Australia", "Sydney"),
                    findAirportForCity(session, "United states", "Los angeles"),
                    "04:00");
            //"China", "Shanghai" -> "India", "Dilli"
            addRoundtripTimetableRow(session, "WW", "B744",
                    findAirportForCity(session, "China", "Shanghai"),
                    findAirportForCity(session, "India", "Dilli"),
                    "06:00");
            //"China", "Shanghai" -> "Singapore", "Singapore"
            addRoundtripTimetableRow(session, "WW", "B744",
                    findAirportForCity(session, "China", "Shanghai"),
                    findAirportForCity(session, "Singapore", "Singapore"),
                    "07:00");
        }
    }

    private static void buildMidRangeHub(Session session, String airlineIata, String countryName, String cityName, int rangeFrom, int rangeTo, String aircraftType, int topCities) {
        Country country = CommonOps.countryByName(session, countryName);
        if (country == null) {
            throw new IllegalArgumentException("Could not find country '" + countryName + "'");
        }

        City city = CommonOps.cityByNameAndCountry(session, cityName, country);
        if (city == null) {
            throw new IllegalArgumentException("Could not find city '" + cityName + "'");
        }

        Airport cityAirport = findAirportForCity(session, city);

        //noinspection unchecked
        List<City> allCities = session
                .createQuery("from City where dataset = :active")
                .setInteger("active", Airways.ACTIVE_DATASET)
                .list();

        List<City> cities = new ArrayList<>();

        for (City eachCity : allCities) {
            if (eachCity.getId().equals(city.getId())) {
                continue;
            }

            double distance = Geo.distance(new Geo.Coords(city.getLatitude(), city.getLongitude()), new Geo.Coords(eachCity.getLatitude(), eachCity.getLongitude()));
            if (distance < rangeFrom || distance > rangeTo) {
                continue;
            }

            cities.add(eachCity);
        }

        cities.sort((city1, city2) -> Integer.compare(city2.getPopulation(), city1.getPopulation()));

        while (cities.size() > topCities) {
            cities.remove(cities.size() - 1);
        }

        for (City eachCity : cities) {
            Airport eachCityAirport = findAirportForCity(session, eachCity);

            if (eachCityAirport != null) {
                logger.info("Flight {}, {} -> {}, {} [{}-{}]", city.getName(), country.getName(), eachCity.getName(), eachCity.getCountry().getName(), cityAirport.getIcao(), eachCityAirport.getIcao());

                String departureTime = JavaTime.toHhmm(LocalTime.of(random.nextInt(24), 5 * random.nextInt(12)));
                addRoundtripTimetableRow(session, airlineIata, aircraftType, cityAirport, eachCityAirport, departureTime);
            } else {
                logger.info("Flight {}, {} -> {}, {} AIRPORT NOT FOUND", city.getName(), country.getName(), eachCity.getName(), eachCity.getCountry().getName());
            }
        }
    }

    private static Airport findAirportForCity(Session session, String countryName, String cityName) {
        Country country = CommonOps.countryByName(session, countryName);
        if (country == null) {
            throw new IllegalArgumentException("Could not find country '" + countryName + "'");
        }

        City city = CommonOps.cityByNameAndCountry(session, cityName, country);
        if (city == null) {
            throw new IllegalArgumentException("Could not find city '" + cityName + "'");
        }

        return findAirportForCity(session, city);
    }

    private static void addRoundtripTimetableRow(Session session, String airlineIata, String aircraftTypeIcao, Airport fromAirport, Airport toAirport, String departureTime) {
        session.getTransaction().begin();

        Airline airline = CommonOps.airlineByIata(session, airlineIata);


        //noinspection JpaQlInspection
        TimetableRow latestAirlineTimetableRow = (TimetableRow) session
                .createQuery("select t from TimetableRow t where t.airline = :airline order by t.number desc")
                .setEntity("airline", airline)
                .setMaxResults(1)
                .uniqueResult();
        String number = latestAirlineTimetableRow != null
                ? CommonOps.increaseFlightNumber(latestAirlineTimetableRow.getNumber())
                : CommonOps.makeFlightNumber(airlineIata, 100);


        AircraftType aircraftType = CommonOps.aircraftTypeByIcao(session, aircraftTypeIcao);

        SimpleFlight simpleFlight = SimpleFlight.forRoute(
                new Geo.Coords(fromAirport.getLatitude(), fromAirport.getLongitude()),
                new Geo.Coords(toAirport.getLatitude(), toAirport.getLongitude()),
                aircraftType);

        Duration flyingTime = simpleFlight.getTotalTime();
        FlightTimeline timeline = FlightTimeline.byFlyingTime(flyingTime);
        Duration flightDuration = timeline.getScheduledDuration(timeline.getBlocksOff(), timeline.getBlocksOn());

        TimetableRow flight1row = new TimetableRow();
        flight1row.setAirline(airline);
        flight1row.setNumber(number);
        flight1row.setFromAirport(fromAirport);
        flight1row.setToAirport(toAirport);
        flight1row.setAircraftType(aircraftType);
        flight1row.setWeekdays("1234567");
        flight1row.setDepartureTime(departureTime);
        flight1row.setDuration(JavaTime.toHhmm(flightDuration));
        flight1row.setStatus(TimetableRow.Status.Active);
        //flight1row.setHeartbeatDt(JavaTime.nowUtc());
        flight1row.setTotalTickets(aircraftTypeIcao.equals("B744") ? 350 : 160); // todo AK

        session.save(flight1row);


        LocalTime flight2departureTime = LocalTime.parse(departureTime).plus(flightDuration).plusMinutes(90);
        int step = 5;
        int remainder = flight2departureTime.getMinute() % step;
        flight2departureTime = flight2departureTime.plusMinutes(remainder != 0 ? (step - remainder) : 0);

        TimetableRow flight2row = new TimetableRow();
        flight2row.setAirline(airline);
        flight2row.setNumber(CommonOps.increaseFlightNumber(number));
        flight2row.setFromAirport(toAirport);
        flight2row.setToAirport(fromAirport);
        flight2row.setAircraftType(aircraftType);
        flight2row.setWeekdays("1234567");
        flight2row.setDepartureTime(JavaTime.toHhmm(flight2departureTime));
        flight2row.setDuration(JavaTime.toHhmm(flightDuration));
        flight2row.setStatus(TimetableRow.Status.Active);
        //flight2row.setHeartbeatDt(JavaTime.nowUtc());
        flight2row.setTotalTickets(aircraftTypeIcao.equals("B744") ? 350 : 160); // todo AK

        session.save(flight2row);


        session.getTransaction().commit();
    }


    private static Airport findAirportForCity(Session session, City city) {
        //noinspection JpaQlInspection,unchecked
        List<Airport2City> airport2cityList = session
                .createQuery("from Airport2City where city = :city and dataset = :active")
                .setEntity("city", city)
                .setInteger("active", Airways.ACTIVE_DATASET)
                .list();

        List<Airport> airports = new ArrayList<>();

        for (Airport2City airport2City : airport2cityList) {
            Airport airport = airport2City.getAirport();
            String icao = airport.getIcao();

            if (!isIcaoCompliant(icao)) {
                continue;
            }

            if (airport.getDataset() != Airways.ACTIVE_DATASET) {
                continue;
            }

            airports.add(airport);
        }

        airports.sort((airport1, airport2) -> Integer.compare(icao2size.get(airport2.getIcao()), icao2size.get(airport1.getIcao())));

        return !airports.isEmpty() ? airports.get(0) : null;
    }

    private static boolean isIcaoCompliant(String icao) {
        if (icao.length() != 4) {
            return false;
        }

        for (int i = 0; i < 4; i++) {
            char c = icao.charAt(i);
            if (!('A' <= c && c <= 'Z')) {
                return false;
            }
        }

        return true;
    }
}
