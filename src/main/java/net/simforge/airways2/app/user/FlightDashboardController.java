package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.SimTrackerBean;
import net.simforge.airways2.app.dto.FlightUltraDto;
import net.simforge.airways2.app.vatsimtracker.VatsimTrackerBean;
import net.simforge.airways2.pilottracker.EfbTracker;
import net.simforge.airways2.pilottracker.SimTracker;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.world.processors.TransportFlightHelper;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static net.simforge.airways2.world.datamodel.FlightMissions.Status.*;
import static net.simforge.airways2.world.datamodel.FlightMissions.Status.Cancelled;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.*;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.Arrival;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.Departure;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.Finished;
import static net.simforge.airways2.world.datamodel.TransportFlights.Status.Flying;

@RestController
@RequestMapping("/flight-dashboard")
@CrossOrigin
public class FlightDashboardController {
    // todo ak2 migrate ids to sqids
    private static final Logger log = LoggerFactory.getLogger(FlightDashboardController.class);

    @Autowired
    private WorldRunnerBean worldBean;
    @Autowired
    private SimTrackerBean simTrackerBean;
    @Autowired
    private VatsimTrackerBean vatsimTrackerBean;

    @GetMapping("/my-flights")
    public MyFlightsResponse getMyFlights(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            List<FlightMissions.Mission> userFlights = world.flightMissions()
                    .allByUserId(userId)
                    .sorted(FlightMissions.sortByDepartureTimeFromPastToFuture)
                    .toList();

            return new MyFlightsResponse(userFlights.stream().map(f -> toStatusDto(world, f, isEfbFlight(userId, f.getId()))).toList());
        });
    }

    @GetMapping("/status")
    public StatusDto getStatus(@RequestAttribute("userId") int userId,
                               @RequestParam(name = "flightId") int flightId) {
        return worldBean.read(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            return toStatusDto(world, flight, isEfbFlight(userId, flightId));
        });
    }

    @GetMapping("/status2")
    public Status2Dto getStatus(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            SimTracker.UserStatus simStatus = simTrackerBean.getSimStatus(userId);

//            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
//            checkIfFlightRelatesToUser(flight, userId);

//            return toStatusDto(world, flight, isEfbFlight(userId, flightId));
            return new Status2Dto(
                    SimStatusDto.from(simStatus),
                    new VatsimStatusDto(),
                    null // todo ak0
                    );
        });
    }

    @AllArgsConstructor
    @Data
    private static class Status2Dto {
        private final SimStatusDto sim;
        private final VatsimStatusDto vatsim;
        private final FlightUltraDto flight;
    }

    @AllArgsConstructor
    @Data
    private static class SimStatusDto {
        private final String status; // none, up-to-date, outdated
        private final String locationStatus; // At airport, On ground out of airport, In flight
        private final String airportIcao;
        private final Boolean parkingBrake;
        private final Boolean engineRunning;
        private final String measuredGs;

        public static SimStatusDto from(SimTracker.UserStatus simStatus) {
            return new SimStatusDto(
                    simStatus.getStatus(),
                    simStatus.getLocationStatus(),
                    simStatus.getAirportIcao(),
                    simStatus.getParkingBrake(),
                    simStatus.getEngineRunning(),
                    null // todo ak0
            );
        }
    }

    @AllArgsConstructor
    @Data
    private static class VatsimStatusDto {
        private final String status = "unknown";
    }

    @PostMapping("/start-flight")
    public StatusDto startFlight(@RequestAttribute("userId") int userId,
                                 @RequestParam(name = "flightId") int flightId,
                                 @RequestParam(name = "efb", required = false, defaultValue = "false") boolean efb) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Dispatched, "flight status is not as expected");

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);
            final String permitted = getFlightMissionShownElements(flight, transportFlight, world);
            checkNotNull(permitted);
            if (!Arrays.asList(permitted.split(",")).contains("start")) {
                throw new IllegalStateException("start is not permitted");
            }

            log.info("f/m #{} - flight-dashboard - start-flight", flightId);
            world.flightMissionControl().startOrCancel(flight);

            if (efb && flight.getStatus() == Preflight) {
                EfbTracker.get().notifyMissionStarted(userId, flightId);
            }

            return toStatusDto(world, flight, isEfbFlight(userId, flightId));
        });
    }

    @PostMapping("/start-boarding")
    public StatusDto startBoarding(@RequestAttribute("userId") int userId,
                                   @RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkNotNull(transportFlight, "transport flight is required for start-boarding");

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForBoarding, "transport flight status is not as expected");

            final String permitted = getTransportFlightShownElements(transportFlight, flight);
            checkNotNull(permitted);
            if (!Arrays.asList(permitted.split(",")).contains("start-boarding")) {
                throw new IllegalStateException("start-boarding is not permitted");
            }

            log.info("f/m #{} - flight-dashboard - start-boarding", flightId);
            world.transportFlightControl().startBoarding(transportFlight);

            return toStatusDto(world, flight, isEfbFlight(userId, flightId));
        });
    }

    @PostMapping("/blocks-off")
    public StatusDto blocksOff(@RequestAttribute("userId") int userId,
                               @RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == Preflight, "flight status is not as expected");

            final String permitted = getFlightMissionShownElements(flight, transportFlight, world);
            checkNotNull(permitted);
            if (!Arrays.asList(permitted.split(",")).contains("blocks-off")) {
                throw new IllegalStateException("blocks-off is not permitted");
            }

            log.info("f/m #{} - flight-dashboard - blocks-off", flightId);
            world.flightMissionControl().blocksOff(flight); // t/f update is inside

            return toStatusDto(world, flight, isEfbFlight(userId, flightId));
        });
    }

    @PostMapping("/takeoff")
    public StatusDto takeoff(@RequestAttribute("userId") int userId,
                             @RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            boolean efbFlight = isEfbFlight(userId, flightId);
            if (efbFlight) {
                throw new IllegalStateException("takeoff is not permitted for EFB flight");
            }

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Departure, "flight status is not as expected");

            final String permitted = getFlightMissionShownElements(flight, transportFlight, world);
            checkNotNull(permitted);
            if (!Arrays.asList(permitted.split(",")).contains("takeoff")) {
                throw new IllegalStateException("takeoff is not permitted");
            }

            log.info("f/m #{} - flight-dashboard - takeoff", flightId);
            world.flightMissionControl().takeoff(flight); // t/f update is inside

            return toStatusDto(world, flight, false);
        });
    }

    @PostMapping("/landing")
    public StatusDto landing(@RequestAttribute("userId") int userId,
                             @RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            boolean efbFlight = isEfbFlight(userId, flightId);
            if (efbFlight) {
                throw new IllegalStateException("landing is not permitted for EFB flight");
            }

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Flying, "flight status is not as expected");

            final String permitted = getFlightMissionShownElements(flight, transportFlight, world);
            checkNotNull(permitted);
            if (!Arrays.asList(permitted.split(",")).contains("landing")) {
                throw new IllegalStateException("landing is not permitted");
            }

            log.info("f/m #{} - flight-dashboard - landing", flightId);
            final Airports.Airport landingAirport = world.airports().byId(flight.getDestinationAirportId()).orElseThrow();
            world.flightMissionControl().landing(flight, landingAirport); // t/f update is inside

            return toStatusDto(world, flight, false);
        });
    }

    @PostMapping("/blocks-on")
    public StatusDto blocksOn(@RequestAttribute("userId") int userId,
                              @RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Arrival, "flight status is not as expected");

            final String permitted = getFlightMissionShownElements(flight, transportFlight, world);
            checkNotNull(permitted);
            if (!Arrays.asList(permitted.split(",")).contains("blocks-on")) {
                throw new IllegalStateException("blocks-on is not permitted");
            }

            log.info("f/m #{} - flight-dashboard - blocks-on", flightId);
            world.flightMissionControl().blocksOn(flight); // t/f update is inside

            return toStatusDto(world, flight, isEfbFlight(userId, flightId));
        });
    }

    @PostMapping("/start-deboarding")
    public StatusDto startDeboarding(@RequestAttribute("userId") int userId,
                                     @RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkNotNull(transportFlight, "transport flight is required for start-deboarding");

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == Postflight, "flight status is not as expected");
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForDeboarding, "transport flight status is not as expected");

            final String permitted = getTransportFlightShownElements(transportFlight, flight);
            checkNotNull(permitted);
            if (!Arrays.asList(permitted.split(",")).contains("start-deboarding")) {
                throw new IllegalStateException("start-deboarding is not permitted");
            }

            log.info("f/m #{} - flight-dashboard - start-deboarding", flightId);
            world.transportFlightControl().startDeboarding(transportFlight);

            return toStatusDto(world, flight, isEfbFlight(userId, flightId));
        });
    }

    @PostMapping("/finish-flight")
    public StatusDto finish(@RequestAttribute("userId") int userId,
                            @RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Postflight, "flight status is not as expected");

            final String permitted = getFlightMissionShownElements(flight, transportFlight, world);
            checkNotNull(permitted);
            if (!Arrays.asList(permitted.split(",")).contains("finish")) {
                throw new IllegalStateException("finish is not permitted");
            }

            log.info("f/m #{} - flight-dashboard - finish", flightId);
            world.flightMissionControl().finish(flight);

            boolean efbFlight = isEfbFlight(userId, flightId);
            if (efbFlight) {
                EfbTracker.get().notifyMissionFinished(userId);
            }

            return toStatusDto(world, flight, efbFlight);
        });
    }

    @PostMapping("/efb/status")
    public EfbStatusDto doEfbStatusExchange(@RequestAttribute("userId") int userId,
                                            @RequestParam(name = "posrep", required = false) String posrep) {
        if (posrep != null) {
            EfbTracker.get().processPosrep(worldBean, userId, posrep);
        }

        return worldBean.read(world -> {
            Optional<EfbTracker.UserStatus> userStatus = EfbTracker.get().getUserStatus(userId);
            int flightMissionId = userStatus.map(EfbTracker.UserStatus::getFlightMissionId).orElse(0);

            Optional<FlightMissions.Mission> current;
            if (flightMissionId > 0) {
                current = world.flightMissions().byId(flightMissionId);
            } else {
                List<FlightMissions.Mission> userFlights = world.flightMissions()
                        .allByUserId(userId)
                        .sorted(FlightMissions.sortByDepartureTimeFromPastToFuture)
                        .toList();

                Optional<FlightMissions.Mission> firstActive = userFlights.stream()
                        .filter(f -> !(f.getStatus() == FlightMissions.Status.Finished || f.getStatus() == Cancelled))
                        .findFirst();

                current = firstActive.isPresent()
                        ? firstActive
                        : (userFlights.isEmpty() ? Optional.empty() : Optional.of(userFlights.get(userFlights.size() - 1)));
            }

            return new EfbStatusDto(
                    current.map(f -> toStatusDto(world, f, true)).orElse(null),
                    userStatus.map(EfbTrackingDebugDto::from).orElse(null));
        });
    }

    private boolean isEfbFlight(int userId, int flightMissionId) {
        Optional<EfbTracker.UserStatus> userStatus = EfbTracker.get().getUserStatus(userId);
        return userStatus.map(EfbTracker.UserStatus::getFlightMissionId).orElse(0) == flightMissionId;
    }

    private void checkIfFlightRelatesToUser(FlightMissions.Mission flight, int userId) {
        if (flight.getUserId() != userId) {
            throw new IllegalArgumentException("flight does not relate to the user");
        }
    }

    private StatusDto toStatusDto(World world, FlightMissions.Mission flight, boolean isEfbFlight) {
        checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(flight.getAircraftId()).orElseThrow();
        final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flight.getId()).orElse(null);

        final AircraftDto aircraftDto = new AircraftDto(
                aircraft.getId(),
                world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao(),
                aircraft.getRegNo(),
                aircraft.getLocationStatus().name(),
                aircraft.getOperationalStatus().name()
        );

        final FlightDto flightDto = new FlightDto(
                flight.getId(),
                flight.getStatus().name(),
                getNextPlannedFlightMissionStatus(flight),
                isEfbFlight
                        ? getFlightMissionShownElementsEfb(flight, transportFlight, world)
                        : getFlightMissionShownElements(flight, transportFlight, world),
                world.airports().getIcao(flight.getDepartureAirportId()).orElseThrow(),
                world.airports().getIcao(flight.getDestinationAirportId()).orElseThrow(),
                TimeTools.ymdOrNull(flight.getPlannedDepartureWorldTime()),
                TimeTools.hhmmOrNull(flight.getPlannedDepartureWorldTime()),
                TimeTools.hhmmOrNull(flight.getPlannedArrivalWorldTime())
        );

        final TransportFlightDto transportFlightDto = transportFlight != null ? new TransportFlightDto(
                transportFlight.getId(),
                transportFlight.getStatus().name(),
                getNextPlannedTransportFlightStatus(transportFlight, flight, world),
                getTransportFlightShownElements(transportFlight, flight),
                transportFlight.getTotalTickets().getTotal(),
                transportFlight.getSoldTickets(),
                transportFlight.getRemainedTickets().toString(),
                transportFlight.getPaxCheckedIn(),
                transportFlight.getPaxOnBoard()
        ) : null;

        return new StatusDto(aircraftDto, flightDto, transportFlightDto);
    }

    private String getNextPlannedFlightMissionStatus(final FlightMissions.Mission flight) {
        return switch (flight.getStatus()) {
            case Dispatched -> Preflight.name() + " at " + TimeTools.hhmmOrNull(FlightMissionHelper.calcPreflightStartTime(flight));
            case Preflight -> Departure.name() + " when Captain decides";
            case Departure -> Flying.name() + " when Captain decides";
            case Flying -> Arrival.name() + " not earlier than " + TimeTools.hhmmOrNull(FlightMissionHelper.calcEarliestAllowedLandingTime(flight));
            case Arrival -> Postflight.name() + " just after Blocks On";
            case Postflight -> Finished.name() + " after Deboarding";
            default -> null;
        };
    }

    private String getFlightMissionShownElements(final FlightMissions.Mission flight, final TransportFlights.Flight transportFlight, final World world) {
        return switch (flight.getStatus()) {
            case Dispatched -> (FlightMissionHelper.calcPreflightStartTime(flight) <= world.getWorldTime()) ? "start" : "start-early-with-caution";
            case Preflight -> (transportFlight == null || transportFlight.getStatus() == WaitingForDeparture) ? "blocks-off" : "blocks-off-disabled";
            case Departure -> "takeoff";
            case Flying -> (FlightMissionHelper.calcEarliestAllowedLandingTime(flight) <= world.getWorldTime()) ? "landing" : "landing-disabled";
            case Arrival -> "blocks-on";
            case Postflight -> (transportFlight == null || transportFlight.getStatus() == Finished) ? "finish" : "finish-disabled";
            default -> null;
        };
    }

    private String getFlightMissionShownElementsEfb(final FlightMissions.Mission flight, final TransportFlights.Flight transportFlight, final World world) {
        return switch (flight.getStatus()) {
            case Dispatched -> (FlightMissionHelper.calcPreflightStartTime(flight) <= world.getWorldTime()) ? "start" : "start-early-with-caution";
            case Preflight -> (transportFlight == null || transportFlight.getStatus() == WaitingForDeparture) ? "blocks-off" : "blocks-off-disabled";
            case Departure -> "takeoff-efb";
            case Flying -> "landing-efb";
            case Arrival -> "blocks-on";
            case Postflight -> (transportFlight == null || transportFlight.getStatus() == Finished) ? "finish" : "finish-disabled";
            default -> null;
        };
    }

    private String getNextPlannedTransportFlightStatus(final TransportFlights.Flight transportFlight, final FlightMissions.Mission flight, World world) {
        return switch (transportFlight.getStatus()) {
            case Scheduled -> CheckIn.name() + " at " + TimeTools.hhmmOrNull(TransportFlightHelper.calcCheckinStartTime(flight));
            case CheckIn -> WaitingForBoarding.name() + " since " + TimeTools.hhmmOrNull(TransportFlightHelper.calcCheckinEndTime(flight));
            case WaitingForBoarding -> Boarding.name() + " when Captain clears";
            case Boarding -> WaitingForDeparture.name() + " at ~" + TimeTools.hhmmOrNull(world.paxManager().getEstimatedBoardingFinishTime(transportFlight));
            case WaitingForDeparture -> Departure.name();
            case Departure -> Flying.name();
            case Flying -> Arrival.name();
            case Arrival -> WaitingForDeboarding.name() + " since Blocks On";
            case WaitingForDeboarding -> Deboarding.name() + " when Captain clears";
            case Deboarding -> Finished.name();
            default -> null;
        };
    }

    private String getTransportFlightShownElements(final TransportFlights.Flight transportFlight, final FlightMissions.Mission flight) {
        return switch (transportFlight.getStatus()) {
            case Scheduled -> "sold";
            case CheckIn -> "sold,check-in,start-boarding-disabled";
            case WaitingForBoarding -> "sold,check-in," + (flight.getStatus() == Preflight ? "start-boarding" : "start-boarding-disabled");
            case Boarding -> "sold,check-in,on-board";
            case WaitingForDeparture, Departure, Flying -> "on-board";
            case Arrival -> "on-board,start-deboarding-disabled";
            case WaitingForDeboarding -> "on-board,start-deboarding";
            case Deboarding -> "on-board";
            default -> null;
        };
    }

    @Data
    @AllArgsConstructor
    public static class MyFlightsResponse {
        private List<StatusDto> flights;
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
        private String shownElements;
        private String depIcao;
        private String destIcao;
        private String planDOF;
        private String planDepTime;
        private String planArrTime;
    }

    @Data
    @AllArgsConstructor
    public static class TransportFlightDto {
        private int id;
        private String status;
        private String nextPlannedStatus;
        private String shownElements;
        private int totalSeats;
        private int soldSeats;
        private String remainedTickets;
        private int checkedIn;
        private int onBoard;
    }

    @Data
    @AllArgsConstructor
    public static class EfbStatusDto {
        private StatusDto flight;
        private EfbTrackingDebugDto tracking;
    }

    @Data
    @AllArgsConstructor
    public static class EfbTrackingDebugDto {
        private String lastSeen;
        private String status;
        private String airportIcao;

        public static EfbTrackingDebugDto from(EfbTracker.UserStatus userStatus) {
            return new EfbTrackingDebugDto(
                    LocalDateTime.ofEpochSecond(userStatus.getLastSeen()/1000, 0, ZoneOffset.UTC)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    userStatus.getStatus(),
                    userStatus.getAirportIcao()
            );
        }
    }
}
