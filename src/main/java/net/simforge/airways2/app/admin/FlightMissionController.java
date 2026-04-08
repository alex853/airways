package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.app.tools.EnhancedFlightMissionDto;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public List<FlightMissionDto> getAll() {
        return worldBean.read(world -> world.flightMissions().all()
                .map(f -> new FlightMissionDto(
                        f.getId(),
                        f.getAircraftId(),
                        f.getStatusCode() + " - " + f.getStatus(),
                        (f.isModePc() ? "P" : "n") + (f.isUnusedMode() ? "+" : "_"),
                        TimeTools.ts(f.getHeartbeatTime()),
                        world.airports().byId(f.getDepartureAirportId()).orElseThrow().getIcao(),
                        world.airports().byId(f.getDestinationAirportId()).orElseThrow().getIcao(),
                        TimeTools.ts(f.getPlannedDepartureWorldTime()),
                        TimeTools.ts(f.getPlannedArrivalWorldTime()),
                        TimeTools.ts(f.getActualDepartureWorldTime()),
                        TimeTools.ts(f.getActualTakeoffWorldTime()),
                        TimeTools.ts(f.getActualLandingWorldTime()),
                        TimeTools.ts(f.getActualArrivalWorldTime()),
                        world.airports().byId(f.getActualLandingAirportId()).map(Airports.Airport::getIcao).orElse(null)
                        ))
                .toList());
    }

    @GetMapping("/current-flights")
    public List<EnhancedFlightMissionDto> getCurrentFlights() {
        return worldBean.read(world -> {
            final int fromTime = world.getWorldTime() - 3 * Time.ONE_HOUR;
            final int toTime = world.getWorldTime() + 21 * Time.ONE_HOUR;
            final Predicate<Integer> condition = time -> fromTime <= time && time <= toTime;
            return world.flightMissions().all()
                    .filter(f -> switch (f.getStatus()) {
                        case PlannedManually, PlannedViaSchedule, Cancelled -> condition.test(f.getPlannedDepartureWorldTime());
                        case Dispatched -> f.getPlannedDepartureWorldTime() <= toTime;
                        case Preflight, Departure, Flying, Arrival, Postflight -> true;
                        case Finished -> condition.test(f.getActualArrivalWorldTime());
                    })
                    .sorted(Comparator.comparing(FlightMissions.Mission::getPlannedDepartureWorldTime))
                    .map(f -> EnhancedFlightMissionDto.fromMission(world, f))
                    .toList();
        });
    }

    @Data
    @AllArgsConstructor
    private static class FlightMissionDto {
        private int id;
        private int aircraftId;
        private String status;
        private String modes;
        private String heartbeatTime;
        private String departureAirport;
        private String destinationAirport;
        private String plannedDepartureTime;
        private String plannedArrivalTime;
        private String actualDepartureTime;
        private String actualTakeoffTime;
        private String actualLandingTime;
        private String actualArrivalTime;
        private String actualLandingAirport;
    }
}
