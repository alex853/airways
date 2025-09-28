package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.world.processors.TransportFlightHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.google.common.base.Preconditions.checkArgument;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.Checkin;

@RestController
@RequestMapping("/flight-dashboard")
@CrossOrigin
public class FlightDashboardController {
    private static final Logger log = LoggerFactory.getLogger(FlightDashboardController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/status")
    public StatusDto getStatus(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.read(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.isModePc(), "flight should be in manual mode");

            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(flight.getAircraftId()).orElseThrow();
            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            final AircraftDto aircraftDto = new AircraftDto(
                    aircraft.getId(),
                    world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao(),
                    aircraft.getRegNo(),
                    aircraft.getLocationStatus().name(),
                    aircraft.getOperationalStatus().name()
            );

            final FlightDto flightDto = new FlightDto(
                    flightId,
                    flight.getStatus().name(),
                    "start",
                    world.airports().getIcao(flight.getDepartureAirportId()),
                    world.airports().getIcao(flight.getDestinationAirportId()),
                    WebTime.hhmmOrNull(flight.getPlannedDepartureWorldTime()),
                    WebTime.hhmmOrNull(flight.getPlannedArrivalWorldTime())
            );

            final TransportFlightDto transportFlightDto = transportFlight != null ? new TransportFlightDto(
                    transportFlight.getId(),
                    transportFlight.getStatus().name(),
                    getNextTransportFlightPlannedStatus(transportFlight, flight),
                    null
            ) : null;

            return new StatusDto(aircraftDto, flightDto, transportFlightDto);
        });
    }

    private NextPlannedStatusDto getNextTransportFlightPlannedStatus(final TransportFlights.Flight transportFlight, final FlightMissions.Mission flight) {
        return switch (transportFlight.getStatus()) {
            case Scheduled -> new NextPlannedStatusDto(
                    Checkin.name(),
                    WebTime.hhmmOrNull(TransportFlightHelper.calcCheckinStartTime(flight)));
            default -> null;
        };
    }

    @PostMapping("/start")
    public EnhancedFlightMissionDto start(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Dispatched, "flight status is not as expected");
            world.flightMissionControl().startOrCancel(flight);
            return EnhancedFlightMissionDto.fromMission(world, flight);
        });
    }

    @PostMapping("/blocks-off")
    public EnhancedFlightMissionDto depart(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");
            world.flightMissionControl().blocksOff(flight);
            return EnhancedFlightMissionDto.fromMission(world, flight);
        });
    }

    @PostMapping("/takeoff")
    public EnhancedFlightMissionDto takeoff(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Departure, "flight status is not as expected");
            world.flightMissionControl().takeoff(flight);
            return EnhancedFlightMissionDto.fromMission(world, flight);
        });
    }

    @PostMapping("/landing")
    public EnhancedFlightMissionDto landing(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Flying, "flight status is not as expected");
            final Airports.Airport landingAirport = world.airports().byId(flight.getDestinationAirportId()).orElseThrow();
            world.flightMissionControl().landing(flight, landingAirport);
            return EnhancedFlightMissionDto.fromMission(world, flight);
        });
    }

    @PostMapping("/blocks-on")
    public EnhancedFlightMissionDto arrive(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Arrival, "flight status is not as expected");
            world.flightMissionControl().blocksOn(flight);
            return EnhancedFlightMissionDto.fromMission(world, flight);
        });
    }

    @PostMapping("/finish")
    public EnhancedFlightMissionDto finish(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Postflight, "flight status is not as expected");
            world.flightMissionControl().finish(flight);
            return EnhancedFlightMissionDto.fromMission(world, flight);
        });
    }

    @Data
    @AllArgsConstructor
    public static class StatusDto {
        private AircraftDto aircraft;
        private FlightDto flight;
        private TransportFlightDto transportFlight;
    }

    @Data
    @AllArgsConstructor
    public static class AircraftDto {
        private int id;
        private String type;
        private String regNo;
        private String locationStatus;
        private String operationalStatus;
    }

    @Data
    @AllArgsConstructor
    public static class FlightDto {
        private int id;
        private String status;
        private String permittedActions;
        private String depIcao;
        private String destIcao;
        private String planDepTime;
        private String planArrTime;
    }

    @Data
    @AllArgsConstructor
    public static class TransportFlightDto {
        private int id;
        private String status;
        private NextPlannedStatusDto nextPlannedStatus;
        private String permittedActions;
    }

    @Data
    @AllArgsConstructor
    public static class NextPlannedStatusDto {
        private String status;
        private String time;
    }
}
