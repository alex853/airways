package net.simforge.airways2.app;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.google.common.base.Preconditions.checkArgument;

@RestController
@RequestMapping("/manual-flight")
@CrossOrigin
public class ManualFlightController {
    private static final Logger log = LoggerFactory.getLogger(ManualFlightController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/status")
    public EnhancedFlightMissionDto getStatus(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        return EnhancedFlightMissionDto.fromMission(world, flight);
    }

    @PostMapping("/start")
    public EnhancedFlightMissionDto start(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Dispatched, "flight status is not as expected");
        world.flightMissionControl().startOrCancel(flight);
        return EnhancedFlightMissionDto.fromMission(world, flight);
    }

    @PostMapping("/blocks-off")
    public EnhancedFlightMissionDto depart(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");
        world.flightMissionControl().blocksOff(flight);
        return EnhancedFlightMissionDto.fromMission(world, flight);
    }

    @PostMapping("/takeoff")
    public EnhancedFlightMissionDto takeoff(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Departure, "flight status is not as expected");
        world.flightMissionControl().takeoff(flight);
        return EnhancedFlightMissionDto.fromMission(world, flight);
    }

    @PostMapping("/landing")
    public EnhancedFlightMissionDto landing(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Flying, "flight status is not as expected");
        world.flightMissionControl().landing(flight);
        return EnhancedFlightMissionDto.fromMission(world, flight);
    }

    @PostMapping("/blocks-on")
    public EnhancedFlightMissionDto arrive(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Arrival, "flight status is not as expected");
        world.flightMissionControl().blocksOn(flight);
        return EnhancedFlightMissionDto.fromMission(world, flight);
    }

    @PostMapping("/finish")
    public EnhancedFlightMissionDto finish(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Postflight, "flight status is not as expected");
        world.flightMissionControl().finish(flight);
        return EnhancedFlightMissionDto.fromMission(world, flight);
    }
}
