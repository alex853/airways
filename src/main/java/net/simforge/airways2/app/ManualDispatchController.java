package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.AircraftPerformanceData;
import net.simforge.airways2.world.computations.AircraftPerformanceDataHelper;
import net.simforge.airways2.world.computations.FlightTimeline;
import net.simforge.airways2.world.computations.SimpleFlight;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.EventLog;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

import static net.simforge.airways2.world.Time.fromLdt;
import static net.simforge.airways2.world.datamodel.EventsToProcess.Type.PilotOnDuty;

@RestController
@RequestMapping("/manual-dispatch")
@CrossOrigin
public class ManualDispatchController {
    private static final Logger log = LoggerFactory.getLogger(ManualDispatchController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/available-aircraft")
    public ResponseEntity<List<AircraftDto>> getAvailableAircraft() {
        final World world = worldBean.world();
        final Collection<Aircrafts.Aircraft> availableAircraft =
                world.aircrafts().allIdleAndParkedAtAirport().stream()
                        .filter(a -> FlightMissions.isFinishedOrCancelledOrEmpty(world.flightMissions().theLatestMissionByAircraftId(a)))
                        .toList();
        return ResponseEntity.ok(availableAircraft.stream()
                .map(a -> new AircraftDto(
                        a.getId(),
                        world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                        a.getRegNo(),
                        world.airports().byId(a.getLocationAirportId()).orElseThrow().getIcao()))
                .toList());
    }

    @PostMapping("/dispatch-flight")
    public void dispatchFlight(
            @RequestParam(name = "aircraftId") final int aircraftId,
            @RequestParam(name = "destinationIcao") final String destinationAirportIcao,
            @RequestParam(name = "departureTimeMode") final String departureTimeMode) {
        final World world = worldBean.world();

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();
        if (!Aircrafts.isIdleAndParkedAtAirport(aircraft)) {
            throw new IllegalArgumentException();
        }

        final Airports.Airport locationAirport = world.airports().byId(aircraft.getLocationAirportId()).orElseThrow();
        final Airports.Airport destinationAirport = world.airports().byIcao(destinationAirportIcao).orElseThrow();

        final int departureTime = switch (departureTimeMode) {
            case "asap" -> world.getWorldTime();
            case "in-1-hour" -> world.getWorldTime() + Time.ONE_HOUR;
            case "in-3-hours" -> world.getWorldTime() + 3*Time.ONE_HOUR;
            case "in-6-hours" -> world.getWorldTime() + 6*Time.ONE_HOUR;
            default -> throw new IllegalArgumentException();
        };

        final AircraftPerformanceData performanceData = AircraftPerformanceDataHelper.getData();
        final SimpleFlight simpleFlight = SimpleFlight.forRoute(locationAirport.getCoords(), destinationAirport.getCoords(), performanceData);
        final FlightTimeline flightTimeline = FlightTimeline.byFlyingTime(simpleFlight.getTotalTime());
        flightTimeline.scheduleDepartureTime(Time.toLdt(departureTime));
        final int arrivalTime = Time.fromLdt(flightTimeline.getBlocksOn().getScheduledTime());

        final FlightMissions.Mission mission = world.flightMissions().createPlannedMission(
                aircraft,
                locationAirport,
                destinationAirport,
                departureTime,
                arrivalTime);

        world.eventsToProcess().sendEvent(
                PilotOnDuty,
                mission.getId(),
                fromLdt(flightTimeline.getStart().getScheduledTime()));

        int pilot = 0; // todo ak2 remove it when pilot is introduced
        world.log(EventLog.EventType.FlightDispatchedManually, EventLog.pilotId(pilot), mission, aircraft);
        log.info("Pilot {}, flight {} - flight dispatched manually, aircraft {}", pilot, mission.getId(), aircraft.getRegNo());
    }

    @Data
    @AllArgsConstructor
    private static class AircraftDto {
        private int id;
        private String type;
        private String regNo;
        private String location;
    }
}
