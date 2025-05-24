package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.World;
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
@RequestMapping("/flight-mission")
@CrossOrigin
public class FlightMissionController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public ResponseEntity<List<FlightMissionDto>> getAll() {
        final World world = worldBean.world();
        final Collection<FlightMissions.Mission> aircraft = world.flightMissions().all();
        return ResponseEntity.ok(aircraft.stream()
                .map(f -> new FlightMissionDto(
                        f.getId(),
                        f.getAircraftId(),
                        f.getStatusRaw() + " - " + f.getStatus(),
                        f.getHeartbeatTime(),
                        world.airports().byId(f.getDepartureAirportId()).orElseThrow().getIcao(),
                        world.airports().byId(f.getDestinationAirportId()).orElseThrow().getIcao(),
                        f.getPlannedDepartureTime(),
                        f.getPlannedArrivalTime(),
                        f.getActualDepartureTime(),
                        f.getActualTakeoffTime(),
                        f.getActualLandingTime(),
                        f.getActualArrivalTime()))
                .toList());
    }

    @Data
    @AllArgsConstructor
    private static class FlightMissionDto {
        private int id;
        private int aircraftId;
        private String status;
        private int heartbeatTime;
        private String departureAirport;
        private String destinationAirport;
        private int plannedDepartureTime;
        private int plannedArrivalTime;
        private int actualDepartureTime;
        private int actualTakeoffTime;
        private int actualLandingTime;
        private int actualArrivalTime;
    }
}
