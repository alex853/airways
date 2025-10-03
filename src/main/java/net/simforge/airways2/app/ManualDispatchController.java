package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.world.processors.TransportFlightControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/manual-dispatch")
@CrossOrigin
public class ManualDispatchController {
    private static final Logger log = LoggerFactory.getLogger(ManualDispatchController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/available-aircraft")
    public List<AircraftDto> getAvailableAircraft() {
        return worldBean.read(world -> world.aircrafts().allIdleAndParkedAtAirportAndNoOperatorAssigned().stream()
                .filter(a -> FlightMissionHelper.isFinishedOrCancelledOrEmpty(world.flightMissions().theLatestMissionByAircraftId(a)))
                .map(a -> new AircraftDto(
                        a.getId(),
                        world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                        a.getRegNo(),
                        world.airports().byId(a.getLocationAirportId()).orElseThrow().getIcao()))
                .toList());
    }

    @PostMapping("/dispatch-flight")
    public DispatchFlightResponseDto dispatchFlight(
            @RequestParam(name = "aircraftId") final int aircraftId,
            @RequestParam(name = "destinationIcao") final String destinationAirportIcao,
            @RequestParam(name = "departureTimeMode") final String departureTimeMode,
            @RequestParam(name = "flightMode") final String flightMode,
            @RequestParam(name = "tfMode") final String tfMode) {
        return worldBean.modifySync(world -> {
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();
            if (!Aircrafts.isIdleAndParkedAtAirportAndNoOperatorAssigned(aircraft)) {
                throw new IllegalArgumentException();
            }
            if (!FlightMissionHelper.isFinishedOrCancelledOrEmpty(world.flightMissions().theLatestMissionByAircraftId(aircraft))) {
                throw new IllegalArgumentException();
            }

            final Airports.Airport destinationAirport = world.airports().byIcao(destinationAirportIcao).orElseThrow();

            final int departureTime = switch (departureTimeMode) {
                case "asap" -> world.getWorldTime();
                case "in-1-hour" -> world.getWorldTime() + Time.ONE_HOUR;
                case "in-3-hours" -> world.getWorldTime() + 3 * Time.ONE_HOUR;
                case "in-6-hours" -> world.getWorldTime() + 6 * Time.ONE_HOUR;
                case "in-12-hours" -> world.getWorldTime() + 12 * Time.ONE_HOUR;
                case "in-18-hours" -> world.getWorldTime() + 18 * Time.ONE_HOUR;
                case "in-24-hours" -> world.getWorldTime() + 24 * Time.ONE_HOUR;
                default -> throw new IllegalArgumentException();
            };

            final boolean pcMode = "manual".equals(flightMode) || "flight-dashboard".equals(flightMode);

            final FlightMissions.Mission mission = FlightMissionHelper.scheduleDispatchedMissionFromCurrentLocationAirport(world, aircraft, destinationAirport, departureTime);
            mission.setModePc(pcMode);

            world.log(EventLog.EventType.FlightDispatchedManually, EventLog.pilotId(0), mission, aircraft);
            log.info("f/m #{} - flight dispatched via web-page, aircraft {}, flight mode {}", mission.getId(), aircraft.getRegNo(), flightMode);

            if ("schedule".equals(tfMode)) {
                final TransportFlights.Flight transportFlight = TransportFlightControl.instance(world).createTransportFlight(mission);
                world.log(EventLog.EventType.TransportFlightCreated, EventLog.id(transportFlight), mission);
                log.info("f/m #{} - created t/f #{} for the mission dispatched via web-page", mission.getId(), transportFlight.getId());
            }

            return new DispatchFlightResponseDto(mission.getId());
        });
    }

    @Data
    @AllArgsConstructor
    private static class AircraftDto {
        private int id;
        private String type;
        private String regNo;
        private String location;
    }

    @Data
    @AllArgsConstructor
    private static class DispatchFlightResponseDto {
        private int flightId;
    }
}
