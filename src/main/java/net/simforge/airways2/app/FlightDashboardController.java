package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.world.processors.TransportFlightControl;
import net.simforge.airways2.world.processors.TransportFlightHelper;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.google.common.base.Preconditions.checkArgument;
import static net.simforge.airways2.world.datamodel.FlightMissions.Status.*;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.Checkin;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.Boarding;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.WaitingForDeparture;

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
                    getNextPlannedFlightMissionStatus(flight),
                    getFlightMissionPermittedActions(flight, transportFlight),
                    world.airports().getIcao(flight.getDepartureAirportId()),
                    world.airports().getIcao(flight.getDestinationAirportId()),
                    WebTime.hhmmOrNull(flight.getPlannedDepartureWorldTime()),
                    WebTime.hhmmOrNull(flight.getPlannedArrivalWorldTime())
            );

            final TransportFlightDto transportFlightDto = transportFlight != null ? new TransportFlightDto(
                    transportFlight.getId(),
                    transportFlight.getStatus().name(),
                    getNextPlannedTransportFlightStatus(transportFlight, flight),
                    getTransportFlightPermittedActions(transportFlight, flight)
            ) : null;

            return new StatusDto(aircraftDto, flightDto, transportFlightDto);
        });
    }

    private NextPlannedStatusDto getNextPlannedFlightMissionStatus(final FlightMissions.Mission flight) {
        return switch (flight.getStatus()) {
            case Dispatched -> new NextPlannedStatusDto(
                    Preflight.name(),
                    WebTime.hhmmOrNull(FlightMissionHelper.calcPreflightStartTime(flight)));
            case Preflight -> new NextPlannedStatusDto(
                    Departure.name(),
                    "the Captain's instruction");
            case Departure -> new NextPlannedStatusDto(
                    Flying.name(),
                    "the Captain's instruction");
            default -> null;
        };
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")
    private String getFlightMissionPermittedActions(final FlightMissions.Mission flight, final TransportFlights.Flight transportFlight) {
        return switch (flight.getStatus()) {
            case PlannedManually, PlannedViaSchedule -> null;
            case Dispatched -> "start"; // todo ak0 deny start too early
            case Preflight -> (transportFlight == null || transportFlight.getStatus() == WaitingForDeparture) ? "blocks-off" : null;
            case Departure -> "takeoff";
            case Flying -> "landing"; // todo ak0 deny if it elapsed less than 75% of ideal time
            case Arrival -> "blocks-on";
            case Postflight -> "finish"; // todo ak0 deny if deboarding has not been completed
            case Finished -> null;
            case Cancelled -> null;
        };
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")
    private NextPlannedStatusDto getNextPlannedTransportFlightStatus(final TransportFlights.Flight transportFlight, final FlightMissions.Mission flight) {
        return switch (transportFlight.getStatus()) {
            case Scheduled -> new NextPlannedStatusDto(
                    Checkin.name(),
                    WebTime.hhmmOrNull(TransportFlightHelper.calcCheckinStartTime(flight)));
            case Checkin -> null;
            case WaitingForBoarding -> new NextPlannedStatusDto(
                    Boarding.name(),
                    "the Captain's instruction");
            case Boarding -> new NextPlannedStatusDto(
                    WaitingForDeparture.name(),
                    "??:??");
            case WaitingForDeparture -> null;
            case Departure -> null;
            case Flying -> null;
            case Arrival -> null;
            case WaitingForDeboarding -> null;
            case Deboarding -> null;
            case Finished -> null;
            case Cancelled -> null;
        };
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")
    private String getTransportFlightPermittedActions(final TransportFlights.Flight transportFlight, final FlightMissions.Mission flight) {
        return switch (transportFlight.getStatus()) {
            case Scheduled, Checkin -> null;
            case WaitingForBoarding -> flight.getStatus() == Preflight ? "start-boarding" : null;
            case Boarding, WaitingForDeparture, Departure, Flying, Arrival -> null;
            case WaitingForDeboarding -> "start-deboarding";
            case Deboarding, Finished -> null;
            case Cancelled -> null;
        };
    }

    @PostMapping("/start-flight")
    public StatusDto startFlight(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();

            checkArgument(flight.isModePc(), "flight should be in the manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Dispatched, "flight status is not as expected");

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);
            final String permitted = getFlightMissionPermittedActions(flight, transportFlight); // todo ak1 permitted actions review
            if (!"start".equals(permitted)) {
                throw new IllegalStateException("start is not permitted");
            }

            // todo ak1 event-logging
            world.flightMissionControl().startOrCancel(flight);
            return getStatus(flightId);
        });
    }

    @PostMapping("/start-boarding")
    public StatusDto startBoarding(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkArgument(flight.isModePc(), "flight should be in the manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForBoarding, "transport flight status is not as expected");

            final String permitted = getTransportFlightPermittedActions(transportFlight, flight); // todo ak1 permitted actions review
            if (!"start-boarding".equals(permitted)) {
                throw new IllegalStateException("start-boarding is not permitted");
            }

            // todo ak1 event-logging
            TransportFlightControl.instance(world).startBoarding(transportFlight);
            return getStatus(flightId);
        });
    }

    @PostMapping("/blocks-off")
    public EnhancedFlightMissionDto depart(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == Preflight, "flight status is not as expected");
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
            checkArgument(flight.getStatus() == Flying, "flight status is not as expected");
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
        private NextPlannedStatusDto nextPlannedStatus;
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
