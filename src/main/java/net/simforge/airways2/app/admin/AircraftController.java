package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/aircraft")
@CrossOrigin
public class AircraftController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<FullAircraftDto> getAll() {
        try (final Timing.Timer ignored = Timing.label("AircraftController - getAll")) {
            return worldBean.read(world -> world.aircrafts().all()
                    .map(a -> new FullAircraftDto(
                            a.getId(),
                            world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                            a.getRegNo(),
                            a.getAircraftOperatorId(),
                            a.getFlightMissionId(),
                            a.getFlightMissionId() != 0 ? FlightMissionHelper.formatRouteOrNull(world, a.getFlightMissionId()) : null,
                            a.getOperationalStatus().name(),
                            a.getLocationStatus().name(),
                            a.getLocationAirportId() != 0 ? world.airports().byId(a.getLocationAirportId()).orElseThrow().getIcao() : null,
                            a.getLocationLatitude(),
                            a.getLocationLongitude()))
                    .toList());
        }
    }

    // todo ak0 deprecate it and replace by another dto
    @Data
    @AllArgsConstructor
    private static class FullAircraftDto {
        private int id;
        private String type;
        private String regNo;
        private int aircraftOperatorId;
        private int flightMissionId;
        private String flightRoute;
        private String operationalStatus;
        private String locationStatus;
        private String locationAirport;
        private float locationLatitude;
        private float locationLongitude;
    }

}
