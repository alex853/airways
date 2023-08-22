package net.simforge.airways.web.backend;

import net.simforge.airways.AirwaysApp;
import net.simforge.airways.model.geo.City;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private static class CityDto {
        private final int cityId;
        private final String cityName;
        private final String countryName;

        public CityDto(City city) {
            this.cityId = city.getId();
            this.cityName = city.getName();
            this.countryName = city.getCountry().getName();
        }

        public int getCityId() {
            return cityId;
        }

        public String getCityName() {
            return cityName;
        }

        public String getCountryName() {
            return countryName;
        }
    }
}
