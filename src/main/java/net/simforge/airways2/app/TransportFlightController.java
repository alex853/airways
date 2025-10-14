package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.FlightMissionToTimeline;
import net.simforge.airways2.world.computations.FlightTimeline;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
                .filter(f -> world.flightMissions()
                        .byId(f.getFlightMissionId())
                        .map(ff -> ff.getPlannedArrivalWorldTime() >= world.getWorldTime() - 12 * Time.ONE_HOUR)
                        .orElse(false)).stream()
                .map(f -> from(world, f))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep))
                .toList());
    }

    @GetMapping("/actual-manual")
    public List<FlightDto> getActualManual() {
        return worldBean.read(world -> world.transportFlights()
                .filter(f -> world.flightMissions()
                        .byId(f.getFlightMissionId())
                        .map(ff -> (ff.getPlannedArrivalWorldTime() >= world.getWorldTime() - 12 * Time.ONE_HOUR)
                            && ff.isModePc())
                        .orElse(false)).stream()
                .map(f -> from(world, f))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep))
                .toList());
    }

    private static FlightDto from(final World world,
                                  final TransportFlights.Flight flight) {
        final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flight.getFlightMissionId());
        final Optional<FlightTimeline> timeline mission.flatMap(m -> FlightMissionToTimeline.byMission(m));
        return new FlightDto(
                flight.getId(),
                flight.getStatus().name(),
                WebTime.ts(flight.getHeartbeatTime()),
                flight.getFlightMissionId(),
                flight.getScheduledFlightId(),
                mission.map(m -> m.isModePc()).orElse(false),
                mission.map(m -> world.airports().byId(m.getDepartureAirportId()).orElseThrow().getIcao()).orElse("n/a"),
                mission.map(m -> world.airports().byId(m.getDestinationAirportId()).orElseThrow().getIcao()).orElse("n/a"),
                mission.map(m -> m.getDateOfFlight().toString()).orElse("n/a"),
                mission.map(m -> WebTime.hhmmOrNull(m.getPlannedDepartureWorldTime())).orElse("n/a"),
                mission.map(m -> WebTime.hhmmOrNull(m.getPlannedArrivalWorldTime())).orElse("n/a"),
                flight.getTotalTickets().toString(),
                flight.getTotalTickets().getTotal() - flight.getRemainedTickets().getTotal() > 0 ? flight.getTotalTickets().getTotal() - flight.getRemainedTickets().getTotal() : null,
                flight.getPaxCheckedIn() > 0 ? flight.getPaxCheckedIn() : null,
                flight.getPaxOnBoard() > 0 ? flight.getPaxOnBoard() : null
            );
    }

    @Data
    @AllArgsConstructor
    private static class FlightDto {
        private int id;
        private String st;
        private String hrtBt;
        private int fmId;
        private int sfId;
        private boolean pcMode;
        private String dep;
        private String dest;
        private String dof;
        private String pDep;
        private String pArr;
        private String tTkts;
        private Integer sTkts;
        private Integer ckdIn;
        private Integer pOnBrd;
    }
}
