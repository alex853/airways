package net.simforge.airways2.app.admin;

import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.app.dto.FlightUltraDto;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.app.vatsimtracker.VatsimTrackerBean;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
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
    @Autowired
    private VatsimTrackerBean vatsimTrackerBean;

    @GetMapping("/all")
    public List<FlightUltraDto> getAll() {
        return worldBean.read(world -> getFlights(world,
                        fm -> true))
                .sorted(Comparator.comparing(FlightUltraDto::getDof).thenComparing(FlightUltraDto::getPDep).reversed())
                .toList();
    }

    @GetMapping("/actual")
    public List<FlightUltraDto> getActual() {
        return worldBean.read(world -> getFlights(world,
                        fm -> isPlannedArrivalTimeWithinNHours(world, fm, 3)
                                && isPlannedDepartureTimeWithinNHours(world, fm, 3)))
                .sorted(Comparator.comparing(FlightUltraDto::getDof).thenComparing(FlightUltraDto::getPDep).reversed())
                .toList();
    }

    @GetMapping("/actual-manual")
    public List<FlightUltraDto> getActualManual() {
        return worldBean.read(world -> getFlights(world,
                        fm -> isPlannedArrivalTimeWithinNHours(world, fm, 3)
                                && isPlannedDepartureTimeWithinNHours(world, fm, 3)
                                && fm.isModePlayerCharacter()))
                .sorted(Comparator.comparing(FlightUltraDto::getDof).thenComparing(FlightUltraDto::getPDep).reversed())
                .toList();
    }

    @GetMapping("/shadow-jet")
    public List<FlightUltraDto> getShadowJet() {
        return worldBean.read(world -> getFlights(world,
                        fm -> isPlannedArrivalTimeWithinNHours(world, fm, 3)
                                && isShadowJetFlight(world, fm)
                                && hasTransportFlight(world, fm)))
                .sorted(Comparator.comparing(FlightUltraDto::getDof).thenComparing(FlightUltraDto::getPDep).reversed())
                .toList();
    }

    @GetMapping("/busy-birds")
    public List<FlightUltraDto> getBusyBirds() {
        return worldBean.read(world -> getFlights(world,
                        fm -> isPlannedArrivalTimeWithinNHours(world, fm, 3)
                                && isBusyBirdsFlight(world, fm)))
                .sorted(Comparator.comparing(FlightUltraDto::getDof).thenComparing(FlightUltraDto::getPDep))
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

    private Stream<FlightUltraDto> getFlights(World world,
                                              Predicate<FlightMissions.Mission> flightMissionCondition) {
        return world.flightMissions().all()
                .filter(flightMissionCondition)
                .map(fm -> {
                    FlightUltraDto dto = FlightUltraDto.from(world, fm);
                    PilotContext vc = vatsimTrackerBean.getContextByFlightMissionId(fm.getId()).orElse(null);
                    return dto.applyVatsimContext(vc);
                });
    }
}
