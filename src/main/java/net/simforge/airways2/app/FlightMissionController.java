package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@RestController
@RequestMapping("/flight-mission")
@CrossOrigin
public class FlightMissionController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public ResponseEntity<List<FlightMissionDto>> getAll() {
        final World world = worldBean.world();
        final Collection<FlightMissions.Mission> flights = world.flightMissions().all();
        return ResponseEntity.ok(flights.stream()
                .map(f -> new FlightMissionDto(
                        f.getId(),
                        f.getAircraftId(),
                        f.getStatusCode() + " - " + f.getStatus(),
                        WebTime.ts(f.getHeartbeatTime()),
                        world.airports().byId(f.getDepartureAirportId()).orElseThrow().getIcao(),
                        world.airports().byId(f.getDestinationAirportId()).orElseThrow().getIcao(),
                        WebTime.ts(f.getPlannedDepartureTime()),
                        WebTime.ts(f.getPlannedArrivalTime()),
                        WebTime.ts(f.getActualDepartureTime()),
                        WebTime.ts(f.getActualTakeoffTime()),
                        WebTime.ts(f.getActualLandingTime()),
                        WebTime.ts(f.getActualArrivalTime())))
                .toList());
    }

    @GetMapping("/current-flights")
    public ResponseEntity<List<EnhancedFlightMissionDto>> getCurrentFlights() {
        final World world = worldBean.world();
        final Collection<FlightMissions.Mission> flights = world.flightMissions().all();
        final int fromTime = world.getWorldTime() - 3 * Time.ONE_HOUR;
        final int toTime = world.getWorldTime() + 21 * Time.ONE_HOUR;
        final Predicate<Integer> condition = time -> fromTime <= time && time <= toTime;
        return ResponseEntity.ok(flights.stream()
                .filter(f -> switch (f.getStatus()) {
                    case PlannedManually, PlannedViaSchedule, Dispatched, Cancelled -> condition.test(f.getPlannedDepartureTime());
                    case Preflight, Departure, Flying, Arrival, Postflight -> true;
                    case Finished -> condition.test(f.getActualArrivalTime());
                })
                .sorted(Comparator.comparing(FlightMissions.Mission::getPlannedDepartureTime))
                .map(f -> EnhancedFlightMissionDto.fromMission(world, f))
                .toList());
    }

    @Data
    @AllArgsConstructor
    private static class FlightMissionDto {
        private int id;
        private int aircraftId;
        private String status;
        private String heartbeatTime;
        private String departureAirport;
        private String destinationAirport;
        private String plannedDepartureTime;
        private String plannedArrivalTime;
        private String actualDepartureTime;
        private String actualTakeoffTime;
        private String actualLandingTime;
        private String actualArrivalTime;
    }
}
