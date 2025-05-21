package net.simforge.airways2.app;

import net.simforge.airways2.app.dto.CityDto;
import net.simforge.airways2.app.dto.CountryDto;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/geo")
@CrossOrigin
public class GeoController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/countries")
    public ResponseEntity<List<CountryDto>> getCountries() {
        final World world = worldBean.world();
        final Collection<Countries.Country> countries = world.countries().all();
        return ResponseEntity.ok(countries.stream()
                .map(c -> new CountryDto(
                        c.getId(),
                        c.getCode(),
                        c.getName())).toList());
    }

    @GetMapping("/cities")
    public ResponseEntity<List<CityDto>> getCities() {
        final World world = worldBean.world();
        final Collection<Cities.City> cities = world.cities().all();
        return ResponseEntity.ok(cities.stream()
                .map(c -> new CityDto(
                        c.getId(),
                        c.getCountryId(),
                        c.getName(),
                        c.getPopulation(),
                        c.getLatitude(),
                        c.getLongitude())).toList());
    }

    @GetMapping("/airports")
    public ResponseEntity<List<CityDto>> getAirports() {
        return null;
    }
}
