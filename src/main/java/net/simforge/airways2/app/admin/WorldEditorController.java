package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AirportFacilities;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.CityFlows;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/**
 * The world editor (airways/admin/map.html): everything the editor knows about a city or an airport, and the few things it may change. The map itself
 * (cities, airports, links) comes from the public /geo endpoints.
 */
@RestController
@RequestMapping("/admin/world")
@CrossOrigin
public class WorldEditorController {
    private static final Logger log = LoggerFactory.getLogger(WorldEditorController.class);

    private static final int MAX_POPULATION = 100_000_000;
    private static final float MAX_ATTRACTION = 65f;

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/city")
    public CityInfoDto getCity(@RequestParam("id") final String idStr) {
        return worldBean.read(world -> toCityInfo(world, findCity(world, idStr)));
    }

    @GetMapping("/airport")
    public AirportInfoDto getAirport(@RequestParam("icao") final String icao) {
        return worldBean.read(world -> toAirportInfo(world, findAirport(world, icao)));
    }

    /**
     * The attraction of the city (the flow of passengers to it). The redistribution of the flows of the city is started again.
     */
    @PostMapping("/city/attraction")
    public CityInfoDto setAttraction(@RequestParam("id") final String idStr, @RequestParam("value") final float value) {
        if (!(value >= 0f && value <= MAX_ATTRACTION)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attraction should be from 0 to " + MAX_ATTRACTION);
        }
        return worldBean.modifySync(world -> {
            final Cities.City city = findCity(world, idStr);
            final CityFlows.Flow flow = world.cityFlows().byCityId(city.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "City flow of " + city.getName() + " not found"));
            flow.setAttractionFactor(value);
            flow.setLastRedistributionTime(0);
            log.info("world editor - the attraction of city {} is set to {}", city.getName(), value);
            return toCityInfo(world, city);
        });
    }

    /**
     * The population of the city. The redistribution of the flows of the city is started again.
     */
    @PostMapping("/city/population")
    public CityInfoDto setPopulation(@RequestParam("id") final String idStr, @RequestParam("value") final int value) {
        if (value < 0 || value > MAX_POPULATION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Population should be from 0 to " + MAX_POPULATION);
        }
        return worldBean.modifySync(world -> {
            final Cities.City city = findCity(world, idStr);
            city.setPopulation(value);
            world.cityFlows().byCityId(city.getId()).ifPresent(flow -> flow.setLastRedistributionTime(0));
            log.info("world editor - the population of city {} is set to {}", city.getName(), value);
            return toCityInfo(world, city);
        });
    }

    /**
     * Creates or removes the business aviation terminal of the airport.
     */
    @PostMapping("/airport/business-terminal")
    public AirportInfoDto setBusinessTerminal(@RequestParam("icao") final String icao, @RequestParam("enabled") final boolean enabled) {
        return worldBean.modifySync(world -> {
            final Airports.Airport airport = findAirport(world, icao);
            if (enabled) {
                world.airportFacilities().createIfAbsent(airport, AirportFacilities.Type.BusinessAviationTerminal);
            } else {
                world.airportFacilities().delete(airport, AirportFacilities.Type.BusinessAviationTerminal);
            }
            log.info("world editor - the business aviation terminal of airport {} is {}", airport.getIcao(), enabled ? "created" : "removed");
            return toAirportInfo(world, airport);
        });
    }

    private static Cities.City findCity(final World world, final String idStr) {
        final int id;
        try {
            id = Id.decode(idStr);
        } catch (final RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City " + idStr + " not found");
        }
        return world.cities().byId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "City " + idStr + " not found"));
    }

    private static Airports.Airport findAirport(final World world, final String icao) {
        return world.airports().byIcao(icao)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Airport " + icao + " not found"));
    }

    static CityInfoDto toCityInfo(final World world, final Cities.City city) {
        final Countries.Country country = world.countries().byId(city.getCountryId()).orElse(null);
        final CityFlows.Flow flow = world.cityFlows().byCityId(city.getId()).orElse(null);
        final List<LinkedAirportDto> airports = world.airport2city().allByCityId(city.getId())
                .map(link -> world.airports().byId(link.getAirportId()).orElseThrow())
                .map(a -> new LinkedAirportDto(a.getIcao(), a.getIata(), a.getName(), Geo.distance(city.getCoords(), a.getCoords()),
                        world.airportFacilities().hasFacility(a, AirportFacilities.Type.BusinessAviationTerminal)))
                .sorted(Comparator.comparing(LinkedAirportDto::getIcao))
                .toList();
        return new CityInfoDto(
                Id.encode(city.getId()),
                city.getName(),
                country != null ? country.getCode() : null,
                country != null ? country.getName() : null,
                city.getLatitude(),
                city.getLongitude(),
                city.getPopulation(),
                flow != null ? flow.getAttractionFactor() : null,
                flow != null ? flow.getMobilityFactor() : null,
                flow != null ? flow.getLastRedistributionTime() : null,
                airports);
    }

    static AirportInfoDto toAirportInfo(final World world, final Airports.Airport airport) {
        final List<LinkedCityDto> cities = world.airport2city().allByAirportId(airport.getId())
                .map(link -> world.cities().byId(link.getCityId()).orElseThrow())
                .map(c -> new LinkedCityDto(Id.encode(c.getId()), c.getName(), world.countries().byId(c.getCountryId()).map(Countries.Country::getCode).orElse(null),
                        c.getPopulation(), Geo.distance(airport.getCoords(), c.getCoords())))
                .sorted(Comparator.comparing(LinkedCityDto::getPopulation).reversed())
                .toList();
        final List<FacilityDto> facilities = world.airportFacilities().by(airport)
                .map(f -> new FacilityDto(
                        f.getType() != null ? f.getType().name() : "?",
                        f.getAircraftOperatorId() > 0
                                ? world.aircraftOperators().byId(f.getAircraftOperatorId()).map(o -> o.getName()).orElse("operator #" + f.getAircraftOperatorId())
                                : null))
                .sorted(Comparator.comparing(FacilityDto::getType))
                .toList();
        return new AirportInfoDto(
                Id.encode(airport.getId()),
                airport.getIcao(),
                airport.getIata(),
                airport.getName(),
                airport.getLatitude(),
                airport.getLongitude(),
                airport.isExcluded(),
                world.airportFacilities().hasFacility(airport, AirportFacilities.Type.BusinessAviationTerminal),
                cities,
                facilities);
    }

    @Data
    @AllArgsConstructor
    public static class CityInfoDto {
        private String id;
        private String name;
        private String countryCode;
        private String country;
        private float latitude;
        private float longitude;
        private int population;
        // null when the city has no flow (it is created at the start of the world)
        private Float attraction;
        private Float mobility;
        private Integer lastRedistributionTime;
        private List<LinkedAirportDto> airports;
    }

    @Data
    @AllArgsConstructor
    public static class LinkedAirportDto {
        private String icao;
        private String iata;
        private String name;
        private double distance;
        private boolean businessTerminal;
    }

    @Data
    @AllArgsConstructor
    public static class AirportInfoDto {
        private String id;
        private String icao;
        private String iata;
        private String name;
        private float latitude;
        private float longitude;
        private boolean excluded;
        private boolean businessTerminal;
        private List<LinkedCityDto> cities;
        private List<FacilityDto> facilities;
    }

    @Data
    @AllArgsConstructor
    public static class LinkedCityDto {
        private String id;
        private String name;
        private String countryCode;
        private int population;
        private double distance;
    }

    @Data
    @AllArgsConstructor
    public static class FacilityDto {
        private String type;
        // the operator whose facility it is, null for a facility of the airport itself
        private String operator;
    }
}
