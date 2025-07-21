package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/transport-flight")
@CrossOrigin
public class TransportFlightController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<FlightDto> getAll() {
        return worldBean.read(world -> world.transportFlights()
                .all().stream()
                .map(f -> from(world, f))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep))
                .toList());
    }

    @GetMapping("/actual")
    public List<FlightDto> getActual() {
        return worldBean.read(world -> world.transportFlights()
                .filter(f -> world.flightMissions().byId(f.getFlightMissionId()).orElseThrow().getPlannedDepartureWorldTime() >= world.getWorldTime() - Time.ONE_DAY).stream()
                .map(f -> from(world, f))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep))
                .toList());
    }

    private static FlightDto from(final World world,
                                  final TransportFlights.Flight flight) {
        final FlightMissions.Mission mission = world.flightMissions().byId(flight.getFlightMissionId()).orElseThrow();
        return new FlightDto(
                flight.getId(),
                flight.getStatus().name(),
                WebTime.ts(flight.getHeartbeatTime()),
                flight.getFlightMissionId(),
                flight.getScheduledFlightId(),
                world.airports().byId(mission.getDepartureAirportId()).orElseThrow().getIcao(),
                world.airports().byId(mission.getDestinationAirportId()).orElseThrow().getIcao(),
                mission.getDateOfFlight().toString(),
                WebTime.hhmmOrNull(mission.getPlannedDepartureWorldTime()),
                WebTime.hhmmOrNull(mission.getPlannedArrivalWorldTime())
                );
    }

    @Data
    @AllArgsConstructor
    private static class FlightDto {
        private int id;
        private String st;
        private String hbt;
        private int fmId;
        private int sfId;
        private String dep;
        private String dest;
        private String dof;
        private String pDep;
        private String pArr;
    }
}
