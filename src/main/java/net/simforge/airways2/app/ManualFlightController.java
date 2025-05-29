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
    public ResponseEntity<?> getStatus(@RequestParam(name = "flightId") final int flightId) {
// todo ak0 npc/pc
        throw new UnsupportedOperationException();
    }

    @PostMapping("/start")
    public void start(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Dispatched, "flight status is not as expected");
        world.flightMissionControl().startOrCancel(flight);
    }

    @PostMapping("/blocks-off")
    public void depart(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");
        world.flightMissionControl().blocksOff(flight);
    }

    @PostMapping("/takeoff")
    public void takeoff(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Departure, "flight status is not as expected");
        world.flightMissionControl().takeoff(flight);
    }

    @PostMapping("/landing")
    public void landing(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Flying, "flight status is not as expected");
        world.flightMissionControl().landing(flight);
    }

    @PostMapping("/blocks-on")
    public void arrive(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Arrival, "flight status is not as expected");
        world.flightMissionControl().blocksOn(flight);
    }

    @PostMapping("/finish")
    public void finish(@RequestParam(name = "flightId") final int flightId) {
        final World world = worldBean.world();
        final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
        checkArgument(flight.isModePc(), "flight should be in manual mode");
        checkArgument(flight.getStatus() == FlightMissions.Status.Postflight, "flight status is not as expected");
        world.flightMissionControl().finish(flight);
    }
}
