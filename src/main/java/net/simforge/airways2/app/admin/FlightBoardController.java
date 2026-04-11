package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.FlightMissionToTimeline;
import net.simforge.airways2.world.computations.FlightTimeline;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.ScheduledFlights;
import net.simforge.airways2.world.datamodel.TransportFlights;
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

/*    @GetMapping("/all")
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
    }*/

    @GetMapping("/shadow-jet")
    public List<FlightDto> getShadowJet() {
        return worldBean.read(world -> getFlights(world,
                        fm -> isPlannedArrivalTimeWithin6hours(world, fm)
                                && isShadowJetFlight(world, fm)
                                && hasTransportFlight(world, fm)))
                .sorted(Comparator.comparing(FlightDto::getDof).thenComparing(FlightDto::getPDep))
                .toList();
    }

    private boolean hasTransportFlight(World world, FlightMissions.Mission fm) {
        return world.transportFlights().byFlightMissionId(fm.getId()).isPresent();
    }

    private static boolean isShadowJetFlight(World world, FlightMissions.Mission fm) {
        return world.aircrafts().byId(fm.getAircraftId()).orElseThrow().getAircraftOperatorId() == World25.ShadowJetOperatorId;
    }

    private static boolean isPlannedArrivalTimeWithin6hours(World world, FlightMissions.Mission fm) {
        return fm.getPlannedArrivalWorldTime() >= world.getWorldTime() - 6 * Time.ONE_HOUR;
    }

    private static boolean isPlannedArrivalTimeWithin12hours(World world, FlightMissions.Mission fm) {
        return fm.getPlannedArrivalWorldTime() >= world.getWorldTime() - 12 * Time.ONE_HOUR;
    }

/*    @GetMapping("/busy-birds")
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
    }*/

    private static Stream<FlightDto> getFlights(World world,
                                                Predicate<FlightMissions.Mission> flightMissionCondition) {
        List<FlightMissions.Mission> flightMissions = world.flightMissions().all().filter(flightMissionCondition).toList();

        return flightMissions.stream().map(fm -> {
            FlightTimeline timeline = FlightMissionToTimeline.byMission(fm);
            TransportFlights.Flight tf = world.transportFlights().byFlightMissionId(fm.getId()).orElse(null);
            ScheduledFlights.Flight sf = tf != null ? world.scheduledFlights().byId(tf.getScheduledFlightId()).orElse(null) : null;
            Aircrafts.Aircraft aircraft = world.aircrafts().byId(fm.getAircraftId()).orElse(null);

            return new FlightDto(
                    fm.getId(),
                    fm.getStatus().name(),
//                    TimeTools.ts(flight.getHeartbeatTime()),
                    tf != null ? tf.getId() : 0,
                    tf != null ? tf.getStatus().name() : null,
                    sf != null ? ScheduledFlightMissionGenerator.getFlightNumberById(sf.getScheduleId()) : null,
//                    mission.map(FlightMissions.Mission::isModePlayerCharacter).orElse(false),
                    world.airports().getIcao(fm.getDepartureAirportId()).orElse(null),
                    world.airports().getIcao(fm.getDestinationAirportId()).orElse(null),
                    fm.getDateOfFlight().toString(),
                    TimeTools.hhmmPlusDaysOrNull(fm.getPlannedDepartureWorldTime()),
                    TimeTools.hhmmPlusDaysOrNull(fm.getPlannedArrivalWorldTime()),
                    TimeTools.hhmmPlusDaysOrNull(fm.getActualDepartureWorldTime()),
                    TimeTools.hhmmPlusDaysOrNull(fm.getActualArrivalWorldTime()),
                    TimeTools.hhmmPlusDaysOrNull(timeline.getBlocksOn().getEstimatedTime()),
                    tf != null ? tf.getTotalTickets().toString() : null,
                    tf != null ? (tf.getTotalTickets().getTotal() - tf.getRemainedTickets().getTotal()) : null,
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
//        private String hrtBt;
        private int tfId;
        private String tfSt;
        private String sfNo;
        //private boolean pcMode;
        private String dep;
        private String dest;
        private String dof;
        private String pDep;
        private String pArr;
        private String aDep;
        private String aArr;
        private String eArr;
        private String tTkts;
        private Integer sTkts;
        private Integer ckdIn;
        private Integer pOnBrd;
    }
}
