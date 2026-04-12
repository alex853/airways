package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.app.vatsimtracker.VatsimTrackerBean;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.FlightMissionToTimeline;
import net.simforge.airways2.world.computations.FlightTimeline;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.ScheduledFlightMissionGenerator;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.JavaTime;
import net.simforge.networkview.core.report.ReportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

@RestController
@RequestMapping("/flight-board")
@CrossOrigin
public class FlightBoardController {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(FlightBoardController.class);

    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired
    private WorldRunnerBean worldBean;
    @Autowired
    private VatsimTrackerBean vatsimTrackerBean;

    @GetMapping("/all")
    public List<FlightDto> getAll() {
        return worldBean.read(world -> getFlights(world,
                        fm -> true))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep).reversed())
                .toList();
    }

    @GetMapping("/actual")
    public List<FlightDto> getActual() {
        return worldBean.read(world -> getFlights(world,
                        fm -> isPlannedArrivalTimeWithinNHours(world, fm, 3)
                                && isPlannedDepartureTimeWithinNHours(world, fm, 3)))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep).reversed())
                .toList();
    }

    @GetMapping("/actual-manual")
    public List<FlightDto> getActualManual() {
        return worldBean.read(world -> getFlights(world,
                        fm -> isPlannedArrivalTimeWithinNHours(world, fm, 3)
                                && isPlannedDepartureTimeWithinNHours(world, fm, 3)
                                && fm.isModePlayerCharacter()))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep).reversed())
                .toList();
    }

    @GetMapping("/shadow-jet")
    public List<FlightDto> getShadowJet() {
        return worldBean.read(world -> getFlights(world,
                        fm -> isPlannedArrivalTimeWithinNHours(world, fm, 3)
                                && isShadowJetFlight(world, fm)
                                && hasTransportFlight(world, fm)))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep).reversed())
                .toList();
    }

    @GetMapping("/busy-birds")
    public List<FlightDto> getBusyBirds() {
        return worldBean.read(world -> getFlights(world,
                        fm -> isPlannedArrivalTimeWithinNHours(world, fm, 3)
                                && isBusyBirdsFlight(world, fm)))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep))
                .toList();
    }

    private boolean hasTransportFlight(World world, FlightMissions.Mission fm) {
        return world.transportFlights().byFlightMissionId(fm.getId()).isPresent();
    }

    private static boolean isBusyBirdsFlight(World world, FlightMissions.Mission fm) {
        return world.aircrafts().byId(fm.getAircraftId()).orElseThrow().getAircraftOperatorId() == World25.BusyBirdsOperatorId;
    }

    private static boolean isShadowJetFlight(World world, FlightMissions.Mission fm) {
        return world.aircrafts().byId(fm.getAircraftId()).orElseThrow().getAircraftOperatorId() == World25.ShadowJetOperatorId;
    }

    private static boolean isPlannedDepartureTimeWithinNHours(World world, FlightMissions.Mission fm, int hours) {
        return fm.getPlannedDepartureWorldTime() <= world.getWorldTime() + hours * Time.ONE_HOUR;
    }

    private static boolean isPlannedArrivalTimeWithinNHours(World world, FlightMissions.Mission fm, int hours) {
        return fm.getPlannedArrivalWorldTime() >= world.getWorldTime() - hours * Time.ONE_HOUR;
    }

    private Stream<FlightDto> getFlights(World world,
                                                Predicate<FlightMissions.Mission> flightMissionCondition) {
        List<FlightMissions.Mission> flightMissions = world.flightMissions().all().filter(flightMissionCondition).toList();

        return flightMissions.stream().map(fm -> {
            FlightTimeline timeline = FlightMissionToTimeline.byMission(fm);
            TransportFlights.Flight tf = world.transportFlights().byFlightMissionId(fm.getId()).orElse(null);
            ScheduledFlights.Flight sf = tf != null ? world.scheduledFlights().byId(tf.getScheduledFlightId()).orElse(null) : null;
            Aircrafts.Aircraft ac = world.aircrafts().byId(fm.getAircraftId()).orElse(null);
            AircraftTypes.AircraftType act = ac != null ? world.aircraftTypes().byId(ac.getAircraftTypeId()).orElse(null) : null;
            PilotContext vc = vatsimTrackerBean.getContextByFlightMissionId(fm.getId()).orElse(null);

            return new FlightDto(
                    fm.getId(),
                    fm.getStatus().name(),

                    ac != null ? ac.getId() : 0,
                    ac != null ? ac.getRegNo() : null,
                    act != null ? act.getIcao() : null,

                    vc != null ? vc.getFlightStage().name() : null,
                    vc != null ? Duration.between(ReportUtils.fromTimestampJava(vc.getPositionLastSeen()), JavaTime.nowUtc()).toSeconds() : 0,

                    tf != null ? tf.getId() : 0,
                    tf != null ? tf.getStatus().name() : null,
                    sf != null ? ScheduledFlightMissionGenerator.getFlightNumberById(sf.getScheduleId()) : null,

                    world.airports().getIcao(fm.getDepartureAirportId()).orElse(null),
                    world.airports().getIcao(fm.getDestinationAirportId()).orElse(null),

                    fm.getDateOfFlight().toString(),

                    TimeTools.hhmmPlusDaysOrNull(fm.getPlannedDepartureWorldTime()),
                    TimeTools.hhmmPlusDaysOrNull(fm.getPlannedArrivalWorldTime()),

                    TimeTools.hhmmPlusDaysOrNull(fm.getActualDepartureWorldTime()),
                    TimeTools.hhmmPlusDaysOrNull(fm.getActualTakeoffWorldTime()),
                    TimeTools.hhmmPlusDaysOrNull(fm.getActualLandingWorldTime()),
                    TimeTools.hhmmPlusDaysOrNull(fm.getActualArrivalWorldTime()),

                    TimeTools.hhmmPlusDaysOrNull(timeline.getTakeoff().getEstimatedTime()),
                    TimeTools.hhmmPlusDaysOrNull(timeline.getLanding().getEstimatedTime()),
                    TimeTools.hhmmPlusDaysOrNull(timeline.getBlocksOn().getEstimatedTime()),

                    tf != null ? tf.getTotalTickets().toString() : null,
                    tf != null ? (tf.getSoldTickets() > 0 ? tf.getSoldTickets() : null) : null,
                    tf != null ? (tf.getPaxCheckedIn() > 0 ? tf.getPaxCheckedIn() : null) : null,
                    tf != null ? (tf.getPaxOnBoard() > 0 ? tf.getPaxOnBoard() : null) :null
            );
        });
    }

    @Data
    @AllArgsConstructor
    public static class FlightDto {
        private int fmId;
        private String fmSt;
        private int acId;
        private String acRg;
        private String acTp;
        private String vcSt;
        private long vcLs;
        private int tfId;
        private String tfSt;
        private String sfNo;
        private String dep;
        private String dest;
        private String dof;
        private String pDep;
        private String pArr;
        private String aDep;
        private String aTkf;
        private String aLdg;
        private String aArr;
        private String eTkf;
        private String eLdg;
        private String eArr;
        private String tTkts;
        private Integer sTkts;
        private Integer ckdIn;
        private Integer pOnBrd;
    }
}
