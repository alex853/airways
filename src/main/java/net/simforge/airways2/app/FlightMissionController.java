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
                        f.getStatusRaw() + " - " + f.getStatus(),
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
    public ResponseEntity<List<EnhancedFlightDto>> getCurrentFlights() {
        final World world = worldBean.world();
        final Collection<FlightMissions.Mission> flights = world.flightMissions().all();
        final int fromTime = world.getWorldTime() - 3 * Time.ONE_HOUR;
        final int toTime = world.getWorldTime() + 21 * Time.ONE_HOUR;
        final Predicate<Integer> condition = time -> fromTime <= time && time <= toTime;
        return ResponseEntity.ok(flights.stream()
                .filter(f -> switch (f.getStatus()) {
                    case PlannedManually, PlannedViaSchedule, Dispatched, Preflight, Cancelled
                            -> condition.test(f.getPlannedDepartureTime());
                    case Departure, Flying, Arrival, Postflight, Finished
                            -> condition.test(f.getPlannedDepartureTime())
                            || condition.test(f.getPlannedArrivalTime())
                            || condition.test(f.getActualDepartureTime())
                            || condition.test(f.getActualTakeoffTime())
                            || condition.test(f.getActualLandingTime())
                            || condition.test(f.getActualArrivalTime());
                })
                .sorted(Comparator.comparing(FlightMissions.Mission::getPlannedDepartureTime))
                .map(f -> new EnhancedFlightDto(
                        f.getId(),
                        f.getAircraftId(),
                        world.aircrafts().byId(f.getAircraftId()).orElseThrow().getRegNo(),
                        f.getStatus().name(),
                        world.airports().byId(f.getDepartureAirportId()).orElseThrow().getIcao(),
                        world.airports().byId(f.getDestinationAirportId()).orElseThrow().getIcao(),
                        WebTime.ymdOrNull(f.getPlannedDepartureTime()),
                        WebTime.hmOrNull(f.getPlannedDepartureTime()),
                        WebTime.hmOrNull(f.getPlannedArrivalTime()),
                        WebTime.hmOrNull(f.getActualDepartureTime()),
                        WebTime.hmOrNull(f.getActualTakeoffTime()),
                        WebTime.hmOrNull(f.getActualLandingTime()),
                        WebTime.hmOrNull(f.getActualArrivalTime())))
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
        private int acId;
        private String acReg;
        private String st;
        private String dep;
        private String dest;
        private String dof;
        private String pDep;
        private String pArr;
        private String aDep;
        private String aTof;
        private String aLdg;
        private String aArr;
    }
}
