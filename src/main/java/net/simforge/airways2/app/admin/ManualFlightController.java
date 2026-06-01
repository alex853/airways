package net.simforge.airways2.app.admin;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.dto.FlightMinDto;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.google.common.base.Preconditions.checkArgument;

@RestController
@RequestMapping("/admin/manual-flight")
@CrossOrigin
public class ManualFlightController {
    private static final Logger log = LoggerFactory.getLogger(ManualFlightController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/status")
    public FlightMinDto getStatus(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.read(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            return FlightMinDto.from(world, flight);
        });
    }

    @PostMapping("/start")
    public FlightMinDto start(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Dispatched, "flight status is not as expected");
            world.flightMissionControl().startOrCancel(flight);
            return FlightMinDto.from(world, flight);
        });
    }

    @PostMapping("/blocks-off")
    public FlightMinDto depart(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");
            world.flightMissionControl().blocksOff(flight);
            return FlightMinDto.from(world, flight);
        });
    }

    @PostMapping("/takeoff")
    public FlightMinDto takeoff(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Departure, "flight status is not as expected");
            world.flightMissionControl().takeoff(flight);
            return FlightMinDto.from(world, flight);
        });
    }

    @PostMapping("/landing")
    public FlightMinDto landing(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Flying, "flight status is not as expected");
            final Airports.Airport landingAirport = world.airports().byId(flight.getDestinationAirportId()).orElseThrow();
            world.flightMissionControl().landing(flight, landingAirport);
            return FlightMinDto.from(world, flight);
        });
    }

    @PostMapping("/blocks-on")
    public FlightMinDto arrive(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Arrival, "flight status is not as expected");
            world.flightMissionControl().blocksOn(flight);
            return FlightMinDto.from(world, flight);
        });
    }

    @PostMapping("/finish")
    public FlightMinDto finish(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Postflight, "flight status is not as expected");
            world.flightMissionControl().finish(flight);
            return FlightMinDto.from(world, flight);
        });
    }
}
