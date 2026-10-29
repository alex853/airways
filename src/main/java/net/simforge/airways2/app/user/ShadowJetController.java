package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.refdata.aircrafts.apd.AircraftPerformanceDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * The booking of ShadowJet flights: the page "Book Flight" of the site. For now only the reference of the aircraft types.
 */
@RestController
@RequestMapping("/shadow-jet")
@CrossOrigin
public class ShadowJetController {
    @Autowired
    private WorldRunnerBean worldBean;

    /**
     * The reference of the aircraft types a ShadowJet flight can be booked on: the types of the world which have performance data, because the time of the
     * arrival of a flight is calculated from it. The types by the ICAO code.
     */
    @GetMapping("/aircraft-types")
    public List<AircraftTypeDto> getAircraftTypes() {
        return worldBean.read(world -> world.aircraftTypes().all()
                .filter(type -> AircraftPerformanceDatabase.getPerformance(type.getIcao()).isPresent())
                .sorted(Comparator.comparing(AircraftTypes.AircraftType::getIcao))
                .map(type -> new AircraftTypeDto(type.getIcao()))
                .toList());
    }

    @Data
    @AllArgsConstructor
    public static class AircraftTypeDto {
        private String icao;
        // TODO the name of the type
    }
}
