package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/aircraft")
@CrossOrigin
public class AircraftController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<FullAircraftDto> getAll() {
        return worldBean.read(world -> world.aircrafts().all().stream()
                .map(a -> new FullAircraftDto(
                        a.getId(),
                        world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                        a.getRegNo(),
                        a.getAircraftOperatorId(),
                        a.getFlightMissionId(),
                        a.getFlightMissionId() != 0 ? FlightMissionHelper.formatRoute(world, a.getFlightMissionId()) : null,
                        a.getOperationalStatus().name(),
                        a.getLocationStatus().name(),
                        a.getLocationAirportId() != 0 ? world.airports().byId(a.getLocationAirportId()).orElseThrow().getIcao() : null,
                        a.getLocationLatitude(),
                        a.getLocationLongitude()))
                .toList());
    }

    @GetMapping("/flying")
    public List<FlyingAircraftDto> getFlying() {
        return worldBean.read(world ->  world.aircrafts().all().stream()
                .filter(a -> a.getLocationStatus() == Aircrafts.LocationStatus.Flying)
                .map(a -> new FlyingAircraftDto(
                        a.getId(),
                        world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                        a.getRegNo(),
                        FlightMissionHelper.formatRoute(world, a.getFlightMissionId()),
                        FlightMissionHelper.calculateHeading(world, a.getFlightMissionId()),
                        a.getLocationLatitude(),
                        a.getLocationLongitude()))
                .toList());
    }

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

    @Data
    @AllArgsConstructor
    private static class FlyingAircraftDto {
        private int id;
        private String type;
        private String regNo;
        private String route;
        private float hdg;
        private float lat;
        private float lon;
    }
}
