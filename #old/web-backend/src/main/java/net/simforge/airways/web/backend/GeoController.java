package net.simforge.airways.web.backend;

import net.simforge.airways.AirwaysApp;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.model.geo.City;
import net.simforge.airways.ops.GeoOps;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/geo")
@CrossOrigin
public class GeoController {
    private static final Logger log = LoggerFactory.getLogger(GeoController.class);

    @GetMapping("/city/all")
    public List<CityDto> loadAllCities() {
        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            //noinspection unchecked
            final List<City> cities = session
                    .createQuery("from City")
                    .list();

            return cities.stream().map(CityDto::new).collect(Collectors.toList());
        }
    }

    @GetMapping("/city/by/airport")
    public List<CityDto> loadCitiesLinkedToAirport(@RequestParam(value = "airportId") final int airportId) {
        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            final List<City> cities = GeoOps.loadCitiesLinkedToAirport(session, airportId);

            return cities.stream().map(CityDto::new).collect(Collectors.toList());
        }
    }

    @GetMapping("/airport/by/city")
    public List<AirportDto> loadAirportsLinkedToCity(@RequestParam(value = "cityId") final int cityId) {
        try (Session session = AirwaysApp.getSessionFactory().openSession()) {
            final List<Airport> airports = GeoOps.loadAirportsLinkedToCity(session, cityId);

            return airports.stream().map(AirportDto::new).collect(Collectors.toList());
        }
    }

    private static class CityDto {
        private final int id;
        private final String name;

        public CityDto(City city) {
            this.id = city.getId();
            this.name = city.getCityWithCountryName();
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    private static class AirportDto {
        private final int id;
        private final String name;

        public AirportDto(Airport airport) {
            this.id = airport.getId();
            this.name = airport.getIcao() + " " + airport.getName();
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
