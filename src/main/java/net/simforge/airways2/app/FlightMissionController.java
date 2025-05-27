package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.commons.misc.JavaTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Comparator;
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
        final Collection<FlightMissions.Mission> flights = world.flightMissions().all();
        return ResponseEntity.ok(flights.stream()
                .map(f -> new FlightMissionDto(
                        f.getId(),
                        f.getAircraftId(),
                        f.getStatusRaw() + " - " + f.getStatus(),
                        WebTime.full(f.getHeartbeatTime()),
                        world.airports().byId(f.getDepartureAirportId()).orElseThrow().getIcao(),
                        world.airports().byId(f.getDestinationAirportId()).orElseThrow().getIcao(),
                        WebTime.full(f.getPlannedDepartureTime()),
                        WebTime.full(f.getPlannedArrivalTime()),
                        WebTime.full(f.getActualDepartureTime()),
                        WebTime.full(f.getActualTakeoffTime()),
                        WebTime.full(f.getActualLandingTime()),
                        WebTime.full(f.getActualArrivalTime())))
                .toList());
    }

    @GetMapping("/current-flights")
    public ResponseEntity<List<EnhancedFlightDto>> getCurrentFlights() {
        final World world = worldBean.world();
        final Collection<FlightMissions.Mission> flights = world.flightMissions().all();
        final int fromTime = world.getWorldTime() - 6 * Time.ONE_HOUR;
        final int toTime = world.getWorldTime() + 18 * Time.ONE_HOUR;
        return ResponseEntity.ok(flights.stream()
                .filter(f -> fromTime <= f.getPlannedDepartureTime() && f.getPlannedDepartureTime() <= toTime) // todo ak0 condition should be improved
                .sorted(Comparator.comparing(FlightMissions.Mission::getPlannedDepartureTime))
                .map(f -> new EnhancedFlightDto(
                        f.getId(),
                        f.getAircraftId(),
                        f.getStatusRaw() + " - " + f.getStatus(),
                        WebTime.full(f.getHeartbeatTime()),
                        world.airports().byId(f.getDepartureAirportId()).orElseThrow().getIcao(),
                        world.airports().byId(f.getDestinationAirportId()).orElseThrow().getIcao(),
                        Time.toLdt(f.getPlannedDepartureTime()).toLocalDate().toString(), // todo ak0 wrap into function
                        JavaTime.toHhmm(Time.toLdt(f.getPlannedDepartureTime()).toLocalTime()), // todo ak0 wrap into function
                        JavaTime.toHhmm(Time.toLdt(f.getPlannedArrivalTime()).toLocalTime()), // todo ak0 wrap into function
                        WebTime.full(f.getActualDepartureTime()),
                        WebTime.full(f.getActualTakeoffTime()),
                        WebTime.full(f.getActualLandingTime()),
                        WebTime.full(f.getActualArrivalTime())))
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

    @Data
    @AllArgsConstructor
    private static class EnhancedFlightDto {
        private int id;
        private int aircraftId;
        private String status;
        private String heartbeatTime;
        private String departureAirport;
        private String destinationAirport;
        private String dof;
        private String pDep;
        private String pArr;
        private String actualDepartureTime;
        private String actualTakeoffTime;
        private String actualLandingTime;
        private String actualArrivalTime;
    }
}
