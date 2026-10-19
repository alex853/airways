package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.SimTrackerBean;
import net.simforge.airways2.app.dto.FlightUltraDto;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.app.vatsimtracker.VatsimTrackerBean;
import net.simforge.airways2.pilottracker.SimTracker;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.world.processors.TransportFlightHelper;
import net.simforge.airways2.world.processors.FlightMissionControl;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@RestController
@RequestMapping("/flight-dashboard")
@CrossOrigin
public class FlightDashboardController {
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

            return new MyFlightsResponse(userFlights.stream().map(f -> toStatusDto(world, f)).toList());
        });
    }

    @GetMapping("/status")
    @Deprecated
    public StatusDto getStatus(@RequestAttribute("userId") int userId,
                               @RequestParam(name = "flightId") String flightId) {
        return worldBean.read(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(Id.decode(flightId)).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            return toStatusDto(world, flight);
        });
    }

    @GetMapping("/status2")
    public Status2Dto getStatus2(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            SimTracker.UserStatus simStatus = simTrackerBean.getSimStatus(userId);
            if (simStatus == null) {
                simStatus = SimTracker.UserStatus.none();
            }

            Integer flightMissionId = simStatus.getFlightMissionId() != null ? Id.decode(simStatus.getFlightMissionId()) : null;
            Optional<FlightMissions.Mission> flight = flightMissionId != null ? world.flightMissions().byId(flightMissionId) : Optional.empty();

            return new Status2Dto(
                    simStatus,
                    new VatsimStatusDto(), // todo ak2 vatsim tracking & dashboard integration rework
                    flight.map(fm -> FlightUltraDto.from(world, fm, true)).orElse(null),
                    buildUserDto(world, userId, simStatus));
        });
    }

    private static UserDto buildUserDto(final World world, final int userId, final SimTracker.UserStatus simStatus) {
        final Optional<User> userO = UserStorage.getInstance().findById(userId);
        if (userO.isEmpty()) {
            return null;
        }

        final User user = userO.get();
        final Optional<Persons.Person> person = user.getPersonId() > 0 ? world.persons().byId(user.getPersonId()) : Optional.empty();

        // The airport where the pilot is expected to be: the location of the person of the user, null if there is no person or
        // the person is not at an airport.
        final String expectedAirport = person
                .filter(p -> p.getLocationType() == Persons.LocationType.Airport)
                .flatMap(p -> world.airports().getIcao(p.getLocationId()))
                .orElse(null);

        final boolean flightInProgress = simStatus.getFlightMissionId() != null || userHasActiveFlight(world, userId);

        return new UserDto(
                Id.encode(user.getId()),
                expectedAirport,
                buildUserLocationDto(expectedAirport, person.map(Persons.Person::getState).orElse(null), person.isPresent(), flightInProgress, simStatus));
    }

    // The flight of the user which has been started and is not finished yet, the person is on duty then.
    private static boolean userHasActiveFlight(final World world, final int userId) {
        return world.flightMissions().allByUserId(userId)
                .anyMatch(fm -> switch (fm.getStatus()) {
                    case Preflight, Departure, Flying, Arrival, Postflight -> true;
                    default -> false;
                });
    }

    // The pilot is expected to be Idle at the airport of its person and the airport where the pilot has been loaded is the same.
    // A flight in progress is always valid (the person is OnDuty, the pilot may be nowhere). Expected and current airports are filled
    // whenever they are known, state is only the verdict.
    static UserLocationDto buildUserLocationDto(final String expectedAirport,
                                                final Persons.State personState,
                                                final boolean hasPerson,
                                                final boolean flightInProgress,
                                                final SimTracker.UserStatus simStatus) {
        final String currentAirport = "At airport".equals(simStatus.getLocationStatus()) ? simStatus.getAirportIcao() : null;

        final UserLocationState state;
        if (!hasPerson) {
            // no person yet - no restrictions
            state = UserLocationState.OK;
        } else if (flightInProgress) {
            // flight is in progress - the check is not applied
            state = UserLocationState.OK;
        } else if (personState != Persons.State.Idle) {
            // todo ak3 a person who is Resting or Travelling can be 'woken up' or 'brought back' by the user, but it costs something
            state = UserLocationState.PERSON_NOT_IDLE;
        } else if (expectedAirport == null) {
            // the person is not at an airport - no restrictions
            state = UserLocationState.OK;
        } else if (currentAirport == null) {
            // sim tracker is not connected, or pilot is in the air or on the ground out of any airport
            state = UserLocationState.UNKNOWN;
        } else if (expectedAirport.equalsIgnoreCase(currentAirport)) {
            state = UserLocationState.OK;
        } else {
            state = UserLocationState.WRONG_AIRPORT;
        }

        return new UserLocationDto(state, expectedAirport, currentAirport);
    }

    @PostMapping("/start-flight")
    public Status2Dto startFlight(@RequestAttribute("userId") int userId,
                                  @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            int flightId = Id.decode(flightIdStr);

            FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Dispatched, "flight status is not as expected");

            boolean simTrackerConnected = simTrackerBean.isUserConnected(userId);

            if (simTrackerConnected) {
                SimTracker.UserStatus simStatus = simTrackerBean.getSimStatus(userId);
                checkArgument(Objects.equals(flightIdStr, simStatus.getFlightMissionId()), "sim tracker status is invalid");
                checkActionAllowed(simStatus, "start-flight");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            log.info("f/m #{} - flight-dashboard - start-flight2", flightId);
            // todo ak2 a manual start should not cancel the flight silently: answer with an error which says what exactly was
            //   checked and what failed (startResult.reason() has it: the flight status, or the aircraft operational status,
            //   location status, location airport), the flight stays Dispatched and the pilot can fix the cause and try again.
            //   Now the old behaviour is kept: the flight is cancelled, the user sees only the new status of the flight.
            final FlightMissionControl.StartResult startResult = world.flightMissionControl().start(flight);
            if (!startResult.started()) {
                world.flightMissionControl().cancelDispatched(flight, startResult.reason());
            }

            if (simTrackerConnected) {
                world.flightMissionControl().switchToExternalCoordinatesMode(flight);
                simTrackerBean.refreshContext(userId);
            }

            return getStatus2(userId);
        });
    }

    @PostMapping("/reschedule")
    public Status2Dto reschedule(@RequestAttribute("userId") int userId,
                                 @RequestParam(name = "flightId") String flightIdStr,
                                 @RequestParam(name = "shiftMinutes") int shiftMinutes) {
        return worldBean.modifySync(world -> {
            int flightId = Id.decode(flightIdStr);

            FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");

            boolean simTrackerConnected = simTrackerBean.isUserConnected(userId);

            if (simTrackerConnected) {
                SimTracker.UserStatus simStatus = simTrackerBean.getSimStatus(userId);
                checkArgument(Objects.equals(flightIdStr, simStatus.getFlightMissionId()), "sim tracker status is invalid");
                checkActionAllowed(simStatus, "reschedule");
            }

            log.info("f/m #{} - flight-dashboard - reschedule by {} min", flightId, shiftMinutes);
            world.flightMissionControl().reschedule(flight, shiftMinutes);

            if (simTrackerConnected) {
                simTrackerBean.refreshContext(userId);
            }

            return getStatus2(userId);
        });
    }

    @PostMapping("/start-boarding")
    public Status2Dto startBoarding(@RequestAttribute("userId") int userId,
                                    @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkNotNull(transportFlight, "transport flight is required for start-boarding");

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForBoarding, "transport flight status is not as expected");

            boolean simTrackerConnected = simTrackerBean.isUserConnected(userId);

            if (simTrackerConnected) {
                SimTracker.UserStatus simStatus = simTrackerBean.getSimStatus(userId);
                checkArgument(Objects.equals(flightIdStr, simStatus.getFlightMissionId()), "sim tracker status is invalid");
                checkActionAllowed(simStatus, "start-boarding");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            log.info("f/m #{} - flight-dashboard - start-boarding", flightId);
            world.transportFlightControl().startBoarding(transportFlight);

            if (simTrackerConnected) {
                simTrackerBean.refreshContext(userId);
            }

            return getStatus2(userId);
        });
    }

    @PostMapping("/blocks-off")
    public Status2Dto blocksOff(@RequestAttribute("userId") int userId,
                                @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");

            if (simTrackerBean.isUserConnected(userId)) {
                throw new IllegalStateException("manual blocks-off is prohibited");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            log.info("f/m #{} - flight-dashboard - blocks-off", flightId);
            world.flightMissionControl().blocksOff(flight); // t/f update is inside

            return getStatus2(userId);
        });
    }

    @PostMapping("/takeoff")
    public Status2Dto takeoff(@RequestAttribute("userId") int userId,
                              @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Departure, "flight status is not as expected");

            if (simTrackerBean.isUserConnected(userId)) {
                throw new IllegalStateException("manual takeoff is prohibited");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            log.info("f/m #{} - flight-dashboard - takeoff", flightId);
            world.flightMissionControl().takeoff(flight); // t/f update is inside

            return getStatus2(userId);
        });
    }

    @PostMapping("/landing")
    public Status2Dto landing(@RequestAttribute("userId") int userId,
                              @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Flying, "flight status is not as expected");

            if (simTrackerBean.isUserConnected(userId)) {
                throw new IllegalStateException("manual landing is prohibited");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            log.info("f/m #{} - flight-dashboard - landing", flightId);
            final Airports.Airport landingAirport = world.airports().byId(flight.getDestinationAirportId()).orElseThrow();
            world.flightMissionControl().landing(flight, landingAirport); // t/f update is inside

            return getStatus2(userId);
        });
    }

    @PostMapping("/blocks-on")
    public Status2Dto blocksOn(@RequestAttribute("userId") int userId,
                               @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Arrival, "flight status is not as expected");

            if (simTrackerBean.isUserConnected(userId)) {
                throw new IllegalStateException("manual blocks-on is prohibited");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            log.info("f/m #{} - flight-dashboard - blocks-on", flightId);
            world.flightMissionControl().blocksOn(flight); // t/f update is inside

            return getStatus2(userId);
        });
    }

    @PostMapping("/start-deboarding")
    public Status2Dto startDeboarding(@RequestAttribute("userId") int userId,
                                      @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkNotNull(transportFlight, "transport flight is required for start-deboarding");

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Postflight, "flight status is not as expected");
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForDeboarding, "transport flight status is not as expected");

            boolean simTrackerConnected = simTrackerBean.isUserConnected(userId);

            if (simTrackerConnected) {
                SimTracker.UserStatus simStatus = simTrackerBean.getSimStatus(userId);
                checkArgument(Objects.equals(flightIdStr, simStatus.getFlightMissionId()), "sim tracker status is invalid");
                checkActionAllowed(simStatus, "start-deboarding");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            log.info("f/m #{} - flight-dashboard - start-deboarding", flightId);
            world.transportFlightControl().startDeboarding(transportFlight);

            if (simTrackerConnected) {
                simTrackerBean.refreshContext(userId);
            }

            return getStatus2(userId);
        });
    }

    @PostMapping("/finish-flight")
    public Status2Dto finish(@RequestAttribute("userId") int userId,
                             @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Postflight, "flight status is not as expected");

            boolean simTrackerConnected = simTrackerBean.isUserConnected(userId);

            // todo ak2 checks?
//            if (simTrackerBean.isUserConnected(userId)) {
//                throw new IllegalStateException("manual finish-flight is prohibited");
//            }
//
//            if (vatsimTrackerBean.isUserConnected(userId)) {
                 // todo ak2 vatsim support
//            }

            log.info("f/m #{} - flight-dashboard - finish", flightId);
            world.flightMissionControl().finish(flight);

            if (simTrackerConnected) {
                simTrackerBean.refreshContext(userId);
            }

            return getStatus2(userId);
        });
    }

    private static void checkIfFlightRelatesToUser(FlightMissions.Mission flight, int userId) {
        checkArgument(flight.getUserId() == userId, "flight does not relate to the user");
    }

    private static void checkActionAllowed(SimTracker.UserStatus simStatus, String actionName) {
        boolean actionAllowed = simStatus.getActions().stream().filter(a -> a.getName().equals(actionName)).findFirst().map(SimTracker.UserAction::isAllowed).orElse(false);
        checkArgument(actionAllowed, "'" + actionName + "' should be allowed");
    }

    @Deprecated
    private StatusDto toStatusDto(World world, FlightMissions.Mission flight) {
        checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(flight.getAircraftId()).orElseThrow();
        final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flight.getId()).orElse(null);

        final AircraftDto aircraftDto = new AircraftDto(
                Id.encode(aircraft.getId()),
                world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao(),
                aircraft.getRegNo(),
                aircraft.getLocationStatus().name(),
                aircraft.getOperationalStatus().name()
        );

        final FlightDto flightDto = new FlightDto(
                Id.encode(flight.getId()),
                flight.getStatus().name(),
                getNextPlannedFlightMissionStatus(flight),
                getFlightMissionShownElements(flight, transportFlight, world),
                world.airports().getIcao(flight.getDepartureAirportId()).orElseThrow(),
                world.airports().getIcao(flight.getDestinationAirportId()).orElseThrow(),
                TimeTools.ymdOrNull(flight.getPlannedDepartureWorldTime()),
                TimeTools.hhmmOrNull(flight.getPlannedDepartureWorldTime()),
                TimeTools.hhmmOrNull(flight.getPlannedArrivalWorldTime())
        );

        final TransportFlightDto transportFlightDto = transportFlight != null ? new TransportFlightDto(
                Id.encode(transportFlight.getId()),
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

    @Deprecated
    private String getNextPlannedFlightMissionStatus(final FlightMissions.Mission flight) {
        return switch (flight.getStatus()) {
            case Dispatched -> FlightMissions.Status.Preflight.name() + " at " + TimeTools.hhmmOrNull(FlightMissionHelper.calcPreflightStartTime(flight));
            case Preflight -> FlightMissions.Status.Departure.name() + " when Captain decides";
            case Departure -> FlightMissions.Status.Flying.name() + " when Captain decides";
            case Flying -> FlightMissions.Status.Arrival.name() + " not earlier than " + TimeTools.hhmmOrNull(FlightMissionHelper.calcEarliestAllowedLandingTime(flight));
            case Arrival -> FlightMissions.Status.Postflight.name() + " just after Blocks On";
            case Postflight -> FlightMissions.Status.Finished.name() + " after Deboarding";
            default -> null;
        };
    }

    @Deprecated
    private String getFlightMissionShownElements(final FlightMissions.Mission flight, final TransportFlights.Flight transportFlight, final World world) {
        return switch (flight.getStatus()) {
            case Dispatched -> (FlightMissionHelper.calcPreflightStartTime(flight) <= world.getWorldTime()) ? "start" : "start-early-with-caution";
            case Preflight -> (transportFlight == null || transportFlight.getStatus() == TransportFlights.Status.WaitingForDeparture) ? "blocks-off" : "blocks-off-disabled";
            case Departure -> "takeoff";
            case Flying -> (FlightMissionHelper.calcEarliestAllowedLandingTime(flight) <= world.getWorldTime()) ? "landing" : "landing-disabled";
            case Arrival -> "blocks-on";
            case Postflight -> (transportFlight == null || transportFlight.getStatus() == TransportFlights.Status.Finished) ? "finish" : "finish-disabled";
            default -> null;
        };
    }

    @Deprecated
    private String getNextPlannedTransportFlightStatus(final TransportFlights.Flight transportFlight, final FlightMissions.Mission flight, World world) {
        return switch (transportFlight.getStatus()) {
            case Scheduled -> TransportFlights.Status.CheckIn.name() + " at " + TimeTools.hhmmOrNull(TransportFlightHelper.calcCheckinStartTime(flight));
            case CheckIn -> TransportFlights.Status.WaitingForBoarding.name() + " since " + TimeTools.hhmmOrNull(TransportFlightHelper.calcCheckinEndTime(flight));
            case WaitingForBoarding -> TransportFlights.Status.Boarding.name() + " when Captain clears";
            case Boarding -> TransportFlights.Status.WaitingForDeparture.name() + " at ~" + TimeTools.hhmmOrNull(world.paxManager().boarding(transportFlight).getEstimatedFinishTime());
            case WaitingForDeparture -> TransportFlights.Status.Departure.name();
            case Departure -> TransportFlights.Status.Flying.name();
            case Flying -> TransportFlights.Status.Arrival.name();
            case Arrival -> TransportFlights.Status.WaitingForDeboarding.name() + " since Blocks On";
            case WaitingForDeboarding -> TransportFlights.Status.Deboarding.name() + " when Captain clears";
            case Deboarding -> TransportFlights.Status.Finished.name();
            default -> null;
        };
    }

    @Deprecated
    private String getTransportFlightShownElements(final TransportFlights.Flight transportFlight, final FlightMissions.Mission flight) {
        return switch (transportFlight.getStatus()) {
            case Scheduled -> "sold";
            case CheckIn -> "sold,check-in,start-boarding-disabled";
            case WaitingForBoarding -> "sold,check-in," + (flight.getStatus() == FlightMissions.Status.Preflight ? "start-boarding" : "start-boarding-disabled");
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
    @Deprecated
    public static class StatusDto {
        private AircraftDto aircraft;
        private FlightDto flight;
        private TransportFlightDto transportFlight;
    }

    @AllArgsConstructor
    @Data
    public static class Status2Dto {
        private final SimTracker.UserStatus sim;
        private final VatsimStatusDto vatsim;
        private final FlightUltraDto flight;
        private final UserDto user;
    }

    @AllArgsConstructor
    @Data
    public static class UserDto {
        private final String id;
        private final String lastLocationAirport;
        private final UserLocationDto location;
        // todo ak2 initials, avatar, money etc. are to be added here
    }

    @AllArgsConstructor
    @Data
    public static class UserLocationDto {
        private final UserLocationState state;
        private final String expectedAirport;
        private final String currentAirport;
    }

    public enum UserLocationState {
        OK,
        WRONG_AIRPORT,
        PERSON_NOT_IDLE,
        UNKNOWN
    }

    @AllArgsConstructor
    @Data
    private static class VatsimStatusDto {
        private final String status = "unknown";
    }

    @Data
    @AllArgsConstructor
    public static class AircraftDto {
        private String id;
        private String type;
        private String regNo;
        private String locationStatus;
        private String operationalStatus;
    }

    @Data
    @AllArgsConstructor
    public static class FlightDto {
        private String id;
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
        private String id;
        private String status;
        private String nextPlannedStatus;
        private String shownElements;
        private int totalSeats;
        private int soldSeats;
        private String remainedTickets;
        private int checkedIn;
        private int onBoard;
    }
}
