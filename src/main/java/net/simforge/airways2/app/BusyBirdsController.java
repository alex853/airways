package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.commons.misc.Geo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/busy-birds")
@CrossOrigin
public class BusyBirdsController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/mission/to-book")
    public List<MissionDto> getMissionsToBook() {
        try (final Timing.Timer ignored = Timing.label("BusyBirdsController - getMissionsToBook")) {
            return worldBean.read(world -> world.journeys().filter(world.journeys().bySpecialProcessing())
                    .map(j -> {
                        final Cities.City fromCity = world.cities().byId(j.getFromCityId()).get();
                        final Cities.City toCity = world.cities().byId(j.getToCityId()).get();
                        final int distance = (int) Geo.distance(fromCity.getCoords(), toCity.getCoords());
                        final int pay = (int) (((distance / 500.0) * 5000.0 + 2000.0) * (1 + fromCity.getId()/1000.0) * (1 + toCity.getId()/1000.0));

                        return new MissionDto( // todo ak0 some filtering by status
                                j.getId(),
                                j.getFromCityId(),
                                fromCity.getName(),
                                j.getToCityId(),
                                toCity.getName(),
                                j.getGroupSize(),
                                distance,
                                pay);
                    })
                    .toList());
        }
    }

    @GetMapping("/aircraft/available")
    public List<AircraftDto> getAvailableAircraft() {
        try (final Timing.Timer ignored = Timing.label("BusyBirdsController - getAvailableAircraft")) {
            return worldBean.read(world -> world.aircrafts()
                    .byAircraftOperatorId(3) // todo ak3 this operatorId should go into some constant
                    .filter(Aircrafts::isIdleAndParkedAtAirport)
                    .map(a -> new AircraftDto(
                            a.getId(),
                            world.aircraftTypes().byId(a.getAircraftTypeId()).get().getIcao(),
                            a.getRegNo(),
                            a.getLocationAirportId(),
                            world.airports().byId(a.getLocationAirportId()).get().getIcao()))
                    .toList());
        }
    }

    @Data
    @AllArgsConstructor
    private static class MissionDto {
        private int id;
        private int fromCityId;
        private String fromCityName;
        private int toCityId;
        private String toCityName;
        private int pax;
        private int distance;
        private int pay;
    }

    @Data
    @AllArgsConstructor
    private static class AircraftDto {
        private int id;
        private String typeCode;
        private String regNo;
        private int locationAirportId;
        private String locationAirportIcao;
    }
}
