package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.datamodel.Aircrafts;
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
                    .map(j -> new MissionDto( // todo ak0 some filtering by status
                            j.getId(),
                            j.getFromCityId(),
                            world.cities().byId(j.getFromCityId()).get().getName(),
                            j.getToCityId(),
                            world.cities().byId(j.getToCityId()).get().getName(),
                            j.getGroupSize()))
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
