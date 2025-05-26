package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/aircraft")
@CrossOrigin
public class AircraftController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public ResponseEntity<List<FullAircraftDto>> getAll() {
        final World world = worldBean.world();
        final Collection<Aircrafts.Aircraft> aircraft = world.aircrafts().all();
        return ResponseEntity.ok(aircraft.stream()
                .map(a -> new FullAircraftDto(
                        a.getId(),
                        world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                        a.getRegNo(),
                        a.getAircraftOperatorId(),
                        a.getFlightMissionId(),
                        a.getOperationalStatusRaw() + " - " + a.getOperationalStatus(),
                        a.getLocationStatusRaw() + " - " + a.getLocationStatus(),
                        a.getLocationAirportId() != 0 ? world.airports().byId(a.getLocationAirportId()).orElseThrow().getIcao() : null,
                        a.getLocationLatitude(),
                        a.getLocationLongitude()))
                .toList());
    }

    @GetMapping("/flying")
    public ResponseEntity<List<FlyingAircraftDto>> getFlying() {
        final World world = worldBean.world();
        final Collection<Aircrafts.Aircraft> aircraft = world.aircrafts().all();
        return ResponseEntity.ok(aircraft.stream()
                .map(a -> new FlyingAircraftDto(
                        a.getId(),
                        world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                        a.getRegNo(),
                        FlightMissions.formatRoute(world, a.getFlightMissionId()),
                        FlightMissions.calculateHeading(world, a.getFlightMissionId()),
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
