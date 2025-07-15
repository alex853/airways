package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transport-flight")
@CrossOrigin
public class TransportFlightController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<FlightDto> getAll() {
        return worldBean.read(world -> world.transportFlights().all().stream()
                .map(f -> new FlightDto(
                        f.getId(),
                        f.getStatus().name(),
                        WebTime.ts(f.getHeartbeatTime()),
                        f.getFlightMissionId(),
                        f.getScheduledFlightId(),
                        world.airports().byId(world.flightMissions().byId(f.getFlightMissionId()).orElseThrow().getDepartureAirportId()).orElseThrow().getIcao(),
                        world.airports().byId(world.flightMissions().byId(f.getFlightMissionId()).orElseThrow().getDestinationAirportId()).orElseThrow().getIcao()))
                .toList());
    }

    @Data
    @AllArgsConstructor
    private static class FlightDto {
        private int id;
        private String status;
        private String heartbeatTime;
        private int flightMissionId;
        private int scheduledFlightId;
        private String departureAirport;
        private String destinationAirport;
    }
}
