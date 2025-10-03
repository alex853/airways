package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.World;
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
import static com.google.common.base.Preconditions.checkNotNull;
import static net.simforge.airways2.world.datamodel.FlightMissions.Status.*;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.*;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.Arrival;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.Departure;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.Finished;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.Flying;

@RestController
@RequestMapping("/flight-dashboard")
@CrossOrigin
public class FlightDashboardController { // todo ak2 migrate ids to sqids
    private static final Logger log = LoggerFactory.getLogger(FlightDashboardController.class);

    @Autowired
    private WorldRunnerBean worldBean;
// todo ak1 add 'disabled' buttons with explanations, correct all states
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
                    getFlightMissionPermittedActions(flight, transportFlight, world),
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

    private String getNextPlannedFlightMissionStatus(final FlightMissions.Mission flight) {
        return switch (flight.getStatus()) {
            case Dispatched -> Preflight.name() + " at " + WebTime.hhmmOrNull(FlightMissionHelper.calcPreflightStartTime(flight));
            case Preflight -> Departure.name() + " when Captain decides";
            case Departure -> Flying.name() + " when Captain decides";
            case Flying -> Arrival.name() + " not earlier than " + WebTime.hhmmOrNull(FlightMissionHelper.calcEarliestAllowedLandingTime(flight));
            case Arrival -> Postflight.name() + " just after Blocks On";
            case Postflight -> Finished.name() + " after Deboarding";
            default -> null;
        };
    }

    private String getFlightMissionPermittedActions(final FlightMissions.Mission flight, final TransportFlights.Flight transportFlight, final World world) {
        return switch (flight.getStatus()) {
            case Dispatched -> "start"; // todo ak1 deny start too early
            case Preflight -> (transportFlight == null || transportFlight.getStatus() == WaitingForDeparture) ? "blocks-off" : null;
            case Departure -> "takeoff";
            case Flying -> (FlightMissionHelper.calcEarliestAllowedLandingTime(flight) <= world.getWorldTime()) ? "landing" : null;
            case Arrival -> "blocks-on";
            case Postflight -> (transportFlight == null || transportFlight.getStatus() == Finished) ? "finish" : null;
            default -> null;
        };
    }

    private String getNextPlannedTransportFlightStatus(final TransportFlights.Flight transportFlight, final FlightMissions.Mission flight) {
        return switch (transportFlight.getStatus()) {
            case Scheduled -> CheckIn.name() + " at " + WebTime.hhmmOrNull(TransportFlightHelper.calcCheckinStartTime(flight));
            case CheckIn -> null; // todo ak1
            case WaitingForBoarding -> Boarding.name() + " when Captain clears";
            case Boarding -> WaitingForDeparture.name() + " till around ??:??"; // todo ak1
            case WaitingForDeparture -> Departure.name();
            case Departure -> Flying.name();
            case Flying -> Arrival.name();
            case Arrival -> WaitingForDeboarding.name() + " since Blocks On";
            case WaitingForDeboarding -> Deboarding.name() + " when Captain clears";
            case Deboarding -> Finished.name();
            default -> null;
        };
    }

    private String getTransportFlightPermittedActions(final TransportFlights.Flight transportFlight, final FlightMissions.Mission flight) {
        return switch (transportFlight.getStatus()) {
            case WaitingForBoarding -> flight.getStatus() == Preflight ? "start-boarding" : null;
            case WaitingForDeboarding -> "start-deboarding";
            default -> null;
        };
    }

    @PostMapping("/start-flight")
    public StatusDto startFlight(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();

            checkArgument(flight.isModePc(), "flight should be in the manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Dispatched, "flight status is not as expected");

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);
            final String permitted = getFlightMissionPermittedActions(flight, transportFlight, world); // todo ak1 permitted actions review
            if (!"start".equals(permitted)) {
                throw new IllegalStateException("start is not permitted");
            }

            // todo ak0 event-logging
            world.flightMissionControl().startOrCancel(flight);

            return getStatus(flightId);
        });
    }

    @PostMapping("/start-boarding")
    public StatusDto startBoarding(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkNotNull(transportFlight, "transport flight is required for start-boarding");

            checkArgument(flight.isModePc(), "flight should be in the manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForBoarding, "transport flight status is not as expected");

            final String permitted = getTransportFlightPermittedActions(transportFlight, flight); // todo ak1 permitted actions review
            if (!"start-boarding".equals(permitted)) {
                throw new IllegalStateException("start-boarding is not permitted");
            }

            // todo ak0 event-logging
            TransportFlightControl.instance(world).startBoarding(transportFlight);
            return getStatus(flightId);
        });
    }

    @PostMapping("/blocks-off")
    public StatusDto blocksOff(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == Preflight, "flight status is not as expected");

            final String permitted = getFlightMissionPermittedActions(flight, transportFlight, world); // todo ak1 permitted actions review
            if (!"blocks-off".equals(permitted)) {
                throw new IllegalStateException("blocks-off is not permitted");
            }

            // todo ak0 event-logging
            world.flightMissionControl().blocksOff(flight); // t/f update is inside

            return getStatus(flightId);
        });
    }

    @PostMapping("/takeoff")
    public StatusDto takeoff(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Departure, "flight status is not as expected");

            final String permitted = getFlightMissionPermittedActions(flight, transportFlight, world); // todo ak1 permitted actions review
            if (!"takeoff".equals(permitted)) {
                throw new IllegalStateException("takeoff is not permitted");
            }

            // todo ak0 event-logging
            world.flightMissionControl().takeoff(flight); // t/f update is inside

            return getStatus(flightId);
        });
    }

    @PostMapping("/landing")
    public StatusDto landing(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Flying, "flight status is not as expected");

            final String permitted = getFlightMissionPermittedActions(flight, transportFlight, world); // todo ak1 permitted actions review
            if (!"landing".equals(permitted)) {
                throw new IllegalStateException("landing is not permitted");
            }

            // todo ak0 event-logging
            final Airports.Airport landingAirport = world.airports().byId(flight.getDestinationAirportId()).orElseThrow();
            world.flightMissionControl().landing(flight, landingAirport); // t/f update is inside

            return getStatus(flightId);
        });
    }

    @PostMapping("/blocks-on")
    public StatusDto blocksOn(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Arrival, "flight status is not as expected");

            final String permitted = getFlightMissionPermittedActions(flight, transportFlight, world); // todo ak1 permitted actions review
            if (!"blocks-on".equals(permitted)) {
                throw new IllegalStateException("blocks-on is not permitted");
            }

            // todo ak0 event-logging
            world.flightMissionControl().blocksOn(flight); // t/f update is inside

            return getStatus(flightId);
        });
    }

    @PostMapping("/start-deboarding")
    public StatusDto startDeboarding(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkNotNull(transportFlight, "transport flight is required for start-deboarding");

            checkArgument(flight.isModePc(), "flight should be in the manual mode");
            checkArgument(flight.getStatus() == Postflight, "flight status is not as expected");
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForDeboarding, "transport flight status is not as expected");

            final String permitted = getTransportFlightPermittedActions(transportFlight, flight); // todo ak1 permitted actions review
            if (!"start-deboarding".equals(permitted)) {
                throw new IllegalStateException("start-deboarding is not permitted");
            }

            // todo ak0 event-logging
            TransportFlightControl.instance(world).startDeboarding(transportFlight);

            return getStatus(flightId);
        });
    }

    @PostMapping("/finish-flight")
    public StatusDto finish(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkArgument(flight.isModePc(), "flight should be in manual mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Postflight, "flight status is not as expected");

            final String permitted = getFlightMissionPermittedActions(flight, transportFlight, world); // todo ak1 permitted actions review
            if (!"finish".equals(permitted)) {
                throw new IllegalStateException("finish is not permitted");
            }

            // todo ak0 event-logging
            world.flightMissionControl().finish(flight);

            return getStatus(flightId);
        });
    }

    @GetMapping("/fix")
    public void fix() {
        worldBean.modifySync(world -> {
            final int flightId = 682;

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            transportFlight.setHeartbeatTime(world.getWorldTime());

            return null;
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
        private String nextPlannedStatus;
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
        private String nextPlannedStatus;
        private String permittedActions;
    }
}
