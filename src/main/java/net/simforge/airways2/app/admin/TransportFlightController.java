package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.FlightMissionToTimeline;
import net.simforge.airways2.world.computations.FlightTimeline;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.world.datamodel.ScheduledFlights;
import net.simforge.airways2.world.processors.ScheduledFlightMissionGenerator;
import net.simforge.airways2.worldbuilder.World25;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(TransportFlightController.class);

    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<FlightDto> getAll() {
        return worldBean.read(world -> world.transportFlights().all()
                .map(f -> from(world, f))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep))
                .toList());
    }

    @GetMapping("/actual")
    public List<FlightDto> getActual() {
        return worldBean.read(world -> world.transportFlights().all()
                .filter(f -> world.flightMissions()
                        .byId(f.getFlightMissionId())
                        .map(ff -> ff.getPlannedArrivalWorldTime() >= world.getWorldTime() - 12 * Time.ONE_HOUR)
                        .orElse(false))
                .map(f -> from(world, f))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep))
                .toList());
    }

    @GetMapping("/actual-manual")
    public List<FlightDto> getActualManual() {
        return worldBean.read(world -> world.transportFlights().all()
                .filter(f -> world.flightMissions()
                        .byId(f.getFlightMissionId())
                        .map(ff -> (ff.getPlannedArrivalWorldTime() >= world.getWorldTime() - 12 * Time.ONE_HOUR)
                                && ff.isModePlayerCharacter())
                        .orElse(false))
                .map(f -> from(world, f))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep))
                .toList());
    }

    @GetMapping("/shadow-jet")
    public List<FlightDto> getShadowJet() {
        return worldBean.read(world -> world.transportFlights().all()
                .filter(f -> world.flightMissions()
                        .byId(f.getFlightMissionId())
                        .map(ff -> (ff.getPlannedArrivalWorldTime() >= world.getWorldTime() - 12 * Time.ONE_HOUR)
                                && world.aircrafts().byId(ff.getAircraftId()).orElseThrow().getAircraftOperatorId() == World25.ShadowJetOperatorId)
                        .orElse(false))
                .map(f -> from(world, f))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep))
                .toList());
    }

    @GetMapping("/busy-birds")
    public List<FlightDto> getBusyBirds() {
        return worldBean.read(world -> world.transportFlights().all()
                .filter(f -> world.flightMissions()
                        .byId(f.getFlightMissionId())
                        .map(ff -> (ff.getPlannedArrivalWorldTime() >= world.getWorldTime() - 12 * Time.ONE_HOUR)
                                && world.aircrafts().byId(ff.getAircraftId()).orElseThrow().getAircraftOperatorId() == World25.BusyBirdsOperatorId)
                        .orElse(false))
                .map(f -> from(world, f))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep))
                .toList());
    }

    private static FlightDto from(final World world,
                                  final TransportFlights.Flight flight) {
        Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flight.getFlightMissionId());
        Optional<FlightTimeline> timeline = mission.map(FlightMissionToTimeline::byMission);
        Optional<ScheduledFlights.Flight> scheduledFlight = world.scheduledFlights().byId(flight.getScheduledFlightId());

        Optional<String> scheduledFlightNumber = scheduledFlight.map(sf -> ScheduledFlightMissionGenerator.getFlightNumberById(sf.getScheduleId()));
        String flightNumber = mission.map(m -> scheduledFlightNumber.orElse("Op:" + world.aircrafts().byId(mission.orElseThrow().getAircraftId()).orElseThrow().getAircraftOperatorId())).orElse(null);

        return new FlightDto(
                flight.getId(),
                flight.getStatus().name(),
                TimeTools.ts(flight.getHeartbeatTime()),
                flight.getFlightMissionId(),
                flightNumber,
                mission.map(FlightMissions.Mission::isModePlayerCharacter).orElse(false),
                mission.map(m -> world.airports().byId(m.getDepartureAirportId()).orElseThrow().getIcao()).orElse("n/a"),
                mission.map(m -> world.airports().byId(m.getDestinationAirportId()).orElseThrow().getIcao()).orElse("n/a"),
                mission.map(m -> m.getDateOfFlight().toString()).orElse("n/a"),
                mission.map(m -> TimeTools.hhmmPlusDaysOrNull(m.getPlannedDepartureWorldTime())).orElse("n/a"),
                mission.map(m -> TimeTools.hhmmPlusDaysOrNull(m.getActualDepartureWorldTime())).orElse(null),
                mission.map(m -> TimeTools.hhmmPlusDaysOrNull(m.getPlannedArrivalWorldTime())).orElse("n/a"),
                mission.map(m -> TimeTools.hhmmPlusDaysOrNull(m.getActualArrivalWorldTime())).orElse(null),
                timeline.map(t -> TimeTools.hhmmPlusDaysOrNull(t.getBlocksOn().getEstimatedTime())).orElse(null),
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
        private String sfNo;
        private boolean pcMode;
        private String dep;
        private String dest;
        private String dof;
        private String pDep;
        private String aDep;
        private String pArr;
        private String aArr;
        private String eArr;
        private String tTkts;
        private Integer sTkts;
        private Integer ckdIn;
        private Integer pOnBrd;
    }
}
