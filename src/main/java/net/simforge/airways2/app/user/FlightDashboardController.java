package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.app.beans.SimTrackerBean;
import net.simforge.airways2.app.dto.FlightUltraDto;
import net.simforge.airways2.app.dto.PlaceProblemDto;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.app.vatsimtracker.VatsimTrackerBean;
import net.simforge.airways2.pilottracker.SimTracker;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.PilotContracts;
import net.simforge.airways2.world.computations.PilotPay;
import net.simforge.airways2.world.processors.OperatorPay;
import net.simforge.airways2.world.processors.RosterCascade;
import net.simforge.airways2.world.processors.RosterForecast;
import net.simforge.airways2.world.processors.ScheduleWarnings;
import net.simforge.airways2.world.computations.PilotPayRates;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.world.processors.TransportFlightHelper;
import net.simforge.airways2.world.processors.FlightMissionControl;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@RestController
@RequestMapping("/flight-dashboard")
@CrossOrigin
public class FlightDashboardController {
    private static final Logger log = LoggerFactory.getLogger(FlightDashboardController.class);
    // the time when the start of a flight opens, in the text of the check (UTC, like all the times of the dashboard)
    private static final java.time.format.DateTimeFormatter START_WINDOW_FORMAT = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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

            // what the roster is going to be like: the expected times, the divert, the conflicts with the flights before (the dashboard tells the same, ScheduleWarnings)
            final List<RosterForecast.Entry> roster = RosterForecast.of(world, userId);
            final Map<Integer, RosterForecast.Entry> forecast = roster.stream().collect(Collectors.toMap(entry -> entry.mission().getId(), entry -> entry));
            // the choices of the move, for the flights which can be moved
            final Map<Integer, RosterForecast.Choices> choices = new java.util.HashMap<>();
            for (int i = 0; i < roster.size(); i++) {
                final int missionId = roster.get(i).mission().getId();
                RosterForecast.rescheduleChoices(world, roster, i).ifPresent(c -> choices.put(missionId, c));
            }
            return new MyFlightsResponse(userFlights.stream().map(f -> toMyFlightsDto(world, f, forecast.get(f.getId()), choices.get(f.getId()))).toList());
        });
    }

    @GetMapping("/status")
    public StatusDto getStatus(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            SimTracker.UserStatus simStatus = simTrackerBean.getSimStatus(userId);
            final boolean simTrackerConnected = simStatus != null;
            if (simStatus == null) {
                simStatus = SimTracker.UserStatus.none();
            }

            // The flight is the one the tracker follows, and the next active flight of the user when the tracker does not know it (it is not connected
            // or the flight is manual).
            Optional<FlightMissions.Mission> flight = Optional.empty();
            if (simTrackerConnected && simStatus.getFlightMissionId() != null) {
                flight = world.flightMissions().byId(Id.decode(simStatus.getFlightMissionId()));
            }
            if (flight.isEmpty()) {
                final Optional<FlightMissions.Mission> found = timed("findActiveFlight", () -> findActiveFlight(world, userId));
                flight = found;
            }

            final Optional<FlightMissions.Mission> activeFlight = flight;
            final SimTracker.UserStatus status = simStatus;
            final FlightMissions.Mission fm = flight.orElse(null);
            final TrackingMode mode = resolveMode(fm, simTrackerConnected);

            // A manual flight is not about the position of the sim, so the sim does not take part in the verdict about the pilot
            final SimTracker.UserStatus verdictSim = mode == TrackingMode.MANUAL ? SimTracker.UserStatus.none() : simStatus;

            return new StatusDto(
                    mode,
                    // the limits are checks of the start only, the scans of the logbook and of the missions are not done for the flights in progress
                    withPilotLimits(timed("buildActions", () -> buildActions(world, fm, status, simTrackerConnected, pilotAirportId(world, userId))),
                            fm != null && (fm.getStatus() == FlightMissions.Status.Dispatched || isWaitingForDecisionAfterWrongLanding(fm))
                                    ? timed("pilotLimitChecks", () -> pilotLimitChecks(world, userId)) : List.of()),
                    simStatus,
                    new VatsimStatusDto(), // todo ak2 vatsim tracking & dashboard integration rework
                    timed("FlightUltraDto", () -> activeFlight.map(f -> FlightUltraDto.from(world, f, true)).orElse(null)),
                    timed("buildPilotDto", () -> buildPilotDto(world, userId, verdictSim)),
                    timed("ScheduleWarnings", () -> ScheduleWarnings.of(world, fm).map(warning -> toScheduleWarningDto(world, warning)).orElse(null)));
        });
    }

    // the parts of the status are timed to see what the request spends its time on (/admin/timing)
    private static <T> T timed(final String part, final java.util.function.Supplier<T> supplier) {
        try (Timing.Timer ignored = Timing.label("FlightDashboard.status - " + part)) {
            return supplier.get();
        }
    }

    /**
     * The limits of the pilot which stop the start of a flight, the same numbers as /pilot shows: the awake time, the duty time in 24 hours, the flight
     * time in 30 and in 365 days. Each check is true while the pilot is within the limit (the orange zone before the limit does not stop anything).
     * A user without a pilot has no checks (the verdict about the pilot says that).
     */
    static List<SimTracker.CheckResult> pilotLimitChecks(final World world, final int userId) {
        final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
        final Persons.Person person = personId > 0 ? world.persons().byId(personId).orElse(null) : null;
        return person == null ? List.of() : pilotLimitChecks(world, person);
    }

    static List<SimTracker.CheckResult> pilotLimitChecks(final World world, final Persons.Person person) {
        final PilotController.AwakeDto awake = PilotController.buildAwake(world, person);
        final PilotController.DutyDto duty = PilotController.buildDuty(world, person);
        final PilotController.FlightTimeDto flightTime = PilotController.buildFlightTime(world, person);
        return List.of(
                new SimTracker.CheckResult("The awake time is within the limit (the pilot is not overtired)", awake.isSleeping() || awake.getCreditMinutes() >= 0),
                new SimTracker.CheckResult("The duty time in 24 hours is within the limit", duty.getLast24HoursMinutes() <= duty.getLimitMinutes()),
                new SimTracker.CheckResult("The flight time in 30 days is within the limit", flightTime.getLast30DaysMinutes() <= flightTime.getLimit30DaysMinutes()),
                new SimTracker.CheckResult("The flight time in 365 days is within the limit", flightTime.getLast365DaysMinutes() <= flightTime.getLimit365DaysMinutes()));
    }

    // The aircraft has landed at a wrong airport and the pilot has to decide what happens to the passengers (the flight can go on to the destination at once)
    private static boolean isWaitingForDecisionAfterWrongLanding(final FlightMissions.Mission flight) {
        return flight.getStatus() == FlightMissions.Status.Postflight && FlightMissionHelper.isLandedAtWrongAirport(flight);
    }

    // The limits are added to both starts of a flight (the tracked one and the manual one) and to the flight on to the destination after a landing at a wrong airport
    // (it starts a new mission at once): the start is not allowed over a limit
    static List<SimTracker.UserAction> withPilotLimits(final List<SimTracker.UserAction> actions, final List<SimTracker.CheckResult> limitChecks) {
        if (limitChecks.isEmpty()) {
            return actions;
        }
        final boolean withinLimits = limitChecks.stream().allMatch(SimTracker.CheckResult::isResult);
        return actions.stream().map(action -> {
            if (!"start-flight".equals(action.getName()) && !"start-flight-manual".equals(action.getName()) && !"continue-now".equals(action.getName())) {
                return action;
            }
            final List<SimTracker.CheckResult> checks = new ArrayList<>(action.getChecks());
            checks.addAll(limitChecks);
            return new SimTracker.UserAction(action.getName(), action.isAllowed() && withinLimits, checks);
        }).toList();
    }

    // The start of a flight is open from 3 hours before the planned departure (FlightMissionHelper.START_WINDOW_SECONDS): both starts of a flight which is not
    // started yet get this check, the endpoint of the start refuses the start before it too. The text says the time when the start opens.
    static SimTracker.CheckResult startWindowCheck(final World world, final FlightMissions.Mission flight) {
        final int earliest = FlightMissionHelper.calcEarliestAllowedStartTime(flight);
        final String opens = Time.toLdt(earliest).format(START_WINDOW_FORMAT);
        return new SimTracker.CheckResult("The flight can be started not earlier than 3 hours before the planned departure (from " + opens + " UTC)",
                world.getWorldTime() >= earliest);
    }

    // The starts of a flight which is not started yet are not allowed before the window of the start (startWindowCheck); the other actions are not touched
    static List<SimTracker.UserAction> withStartWindow(final World world, final FlightMissions.Mission flight, final List<SimTracker.UserAction> actions) {
        if (flight.getStatus() != FlightMissions.Status.Dispatched) {
            return actions;
        }
        final SimTracker.CheckResult check = startWindowCheck(world, flight);
        return actions.stream().map(action -> {
            if (!"start-flight".equals(action.getName()) && !"start-flight-manual".equals(action.getName())) {
                return action;
            }
            final List<SimTracker.CheckResult> checks = new ArrayList<>(action.getChecks());
            checks.add(check);
            return new SimTracker.UserAction(action.getName(), action.isAllowed() && check.isResult(), checks);
        }).toList();
    }

    // The next active flight of the user: the earliest one which is not finished or cancelled.
    private static Optional<FlightMissions.Mission> findActiveFlight(final World world, final int userId) {
        return world.flightMissions()
                .allByUserId(userId)
                .sorted(FlightMissions.sortByDepartureTimeFromPastToFuture)
                .filter(fm -> !FlightMissionHelper.isFinishedOrCancelled(fm))
                .findFirst();
    }

    /**
     * Who does the steps of the flight: null while it is not decided (the flight is not started yet, the pilot chooses with the button he presses),
     * after the start it is the mode which the flight was started in. A flight started before the mode was stored is tracked if the tracker is connected.
     */
    static TrackingMode resolveMode(final FlightMissions.Mission flight, final boolean simTrackerConnected) {
        if (flight == null) {
            return null;
        }
        return switch (flight.getTrackingMode()) {
            case Manual -> TrackingMode.MANUAL;
            case SimTracker -> TrackingMode.SIM_TRACKER;
            case Vatsim -> TrackingMode.VATSIM;
            case None -> flight.getStatus() == FlightMissions.Status.Dispatched
                    ? null
                    : (simTrackerConnected ? TrackingMode.SIM_TRACKER : TrackingMode.MANUAL);
        };
    }

    /**
     * What the dashboard may offer now. Before the start: the start of the tracker (it checks the position of the aircraft, only while the tracker is
     * connected) and the manual start, the pilot chooses. After the start: the actions of the tracker, or the manual ones, by the mode of the flight.
     */
    static List<SimTracker.UserAction> buildActions(final World world,
                                                    final FlightMissions.Mission flight,
                                                    final SimTracker.UserStatus simStatus,
                                                    final boolean simTrackerConnected,
                                                    final int pilotAirportId) {
        if (flight == null) {
            return List.of();
        }

        final List<SimTracker.UserAction> trackerActions = simTrackerConnected && simStatus.getActions() != null ? simStatus.getActions() : List.of();
        final TrackingMode mode = resolveMode(flight, simTrackerConnected);

        final List<SimTracker.UserAction> actions;
        if (mode == null) {
            actions = new ArrayList<>(trackerActions.stream().map(a -> withPilotAtDeparture(world, flight, pilotAirportId, a)).toList());
            actions.addAll(buildManualActions(world, flight, pilotAirportId));
        } else {
            actions = new ArrayList<>(mode == TrackingMode.MANUAL ? buildManualActions(world, flight, pilotAirportId) : trackerActions);
        }
        // the flight of a pilot with the auto finish is finished by itself (see FlightMissionProcessor), so the button is not offered
        if (flight.isAutoFinish()) {
            actions.removeIf(action -> "finish-flight".equals(action.getName()));
        }
        offerDecisionAfterWrongLanding(world, flight, actions);
        return withStartWindow(world, flight, actions);
    }

    // The aircraft has landed not at the destination and is at the gate: the passengers do not get off by the ordinary button, the pilot decides what happens to them.
    // The actions are the same whoever tracks the flight.
    private static void offerDecisionAfterWrongLanding(final World world, final FlightMissions.Mission flight, final List<SimTracker.UserAction> actions) {
        if (flight.getStatus() != FlightMissions.Status.Postflight || !FlightMissionHelper.isLandedAtWrongAirport(flight)) {
            return;
        }
        final TransportFlights.Flight tf = world.transportFlights().byFlightMissionId(flight.getId()).orElse(null);
        if (tf == null || tf.getStatus() != TransportFlights.Status.WaitingForDeboarding) {
            return;
        }
        actions.removeIf(action -> "start-deboarding".equals(action.getName()));
        actions.add(manualAction("continue-now"));
        actions.add(manualAction("continue-later"));
        actions.add(manualAction("cancel-transport"));
    }

    // The steps which the pilot does with buttons when nothing tracks the flight. There are no checks of the position of the aircraft here,
    // an action is listed (and allowed) when the flight and its transport flight are in the status where the controller accepts it.
    static List<SimTracker.UserAction> buildManualActions(final World world, final FlightMissions.Mission flight, final int pilotAirportId) {
        final TransportFlights.Flight tf = world.transportFlights().byFlightMissionId(flight.getId()).orElse(null);
        final List<SimTracker.UserAction> actions = new ArrayList<>();
        switch (flight.getStatus()) {
            case Dispatched -> actions.add(manualStartAction(world, flight, pilotAirportId));
            case Preflight -> {
                // the boarding can be started while the check-in is still going: the transport flight goes from CheckIn to Boarding directly (the check-in and the boarding
                // are different statuses and exclude each other, but the journeys check in during the boarding too)
                if (tf != null && TransportFlightHelper.flightStatusAllowsPilotToStartBoarding(tf.getStatus())) {
                    actions.add(manualAction("start-boarding"));
                } else if (tf != null && tf.getStatus() == TransportFlights.Status.Boarding) {
                    // the pilot stops the boarding by hand: not everybody may have boarded (finish-boarding)
                    actions.add(manualAction("finish-boarding"));
                } else if (tf == null || tf.getStatus() == TransportFlights.Status.WaitingForDeparture) {
                    actions.add(manualAction("blocks-off"));
                }
            }
            case Departure -> actions.add(manualAction("takeoff"));
            case Flying -> actions.add(manualLandingAction(world, flight));
            case Arrival -> actions.add(manualAction("blocks-on"));
            case Postflight -> {
                if (tf != null && tf.getStatus() == TransportFlights.Status.WaitingForDeboarding) {
                    actions.add(manualAction("start-deboarding"));
                } else if ((tf == null || tf.getStatus() == TransportFlights.Status.Finished) && !flight.isAutoFinish()) {
                    // with the auto finish the flight is finished by itself (see FlightMissionProcessor), no button
                    actions.add(manualAction("finish-flight"));
                }
            }
            default -> { }
        }
        return actions;
    }

    // The tracked start needs the pilot at the departure airport as the manual one does: a pilot who booked from a city has to travel to the airport first
    private static SimTracker.UserAction withPilotAtDeparture(final World world, final FlightMissions.Mission flight, final int pilotAirportId,
                                                              final SimTracker.UserAction action) {
        if (!"start-flight".equals(action.getName())) {
            return action;
        }
        final boolean pilotAtDeparture = pilotAirportId == flight.getDepartureAirportId();
        final String icao = world.airports().getIcao(flight.getDepartureAirportId()).orElse("?");
        final List<SimTracker.CheckResult> checks = new ArrayList<>(action.getChecks());
        checks.add(new SimTracker.CheckResult("The pilot is at the departure airport " + icao, pilotAtDeparture));
        return new SimTracker.UserAction(action.getName(), action.isAllowed() && pilotAtDeparture, checks);
    }

    // The pilot starts a flight from the airport where the pilot is: the position of the sim means nothing for a manual flight, the world is what counts
    private static SimTracker.UserAction manualStartAction(final World world, final FlightMissions.Mission flight, final int pilotAirportId) {
        final boolean pilotAtDeparture = pilotAirportId == flight.getDepartureAirportId();
        final String icao = world.airports().getIcao(flight.getDepartureAirportId()).orElse("?");
        // the same condition as the start of the flight itself has (FlightMissionControl.start)
        final boolean aircraftAtDeparture = world.aircrafts().byId(flight.getAircraftId())
                .map(a -> a.getOperationalStatus() == Aircrafts.OperationalStatus.Idle
                        && a.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport
                        && a.getLocationAirportId() == flight.getDepartureAirportId())
                .orElse(false);
        return new SimTracker.UserAction("start-flight-manual", pilotAtDeparture && aircraftAtDeparture, List.of(
                new SimTracker.CheckResult("The pilot is at the departure airport " + icao, pilotAtDeparture),
                new SimTracker.CheckResult("The aircraft is idle at the departure airport " + icao, aircraftAtDeparture)));
    }

    // The airport where the pilot of the user is in the world, 0 if there is no pilot or the pilot is not at an airport
    private static int pilotAirportId(final World world, final int userId) {
        final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
        if (personId <= 0) {
            return 0;
        }
        return world.persons().byId(personId)
                .filter(p -> p.getState() != Persons.State.Travelling && p.getLocationType() == Persons.LocationType.Airport)
                .map(Persons.Person::getLocationId)
                .orElse(0);
    }

    // A manual landing is allowed not earlier than a part of the planned duration after takeoff, the same rule as the one of the endpoint.
    private static SimTracker.UserAction manualLandingAction(final World world, final FlightMissions.Mission flight) {
        final int earliest = FlightMissionHelper.calcEarliestAllowedLandingTime(world, flight);
        final boolean allowed = earliest <= world.getWorldTime();
        return new SimTracker.UserAction("landing", allowed,
                List.of(new SimTracker.CheckResult("Landing is not allowed earlier than " + TimeTools.hhmmOrNull(earliest), allowed)));
    }

    private static SimTracker.UserAction manualAction(final String name) {
        return new SimTracker.UserAction(name, true, List.of());
    }

    // What the dashboards need to know about the pilot of the user: the verdict about where the pilot is and whether the dashboard may be used.
    // The profile of the pilot (initials, avatar etc.) is not here, it is in /pilot.
    private static UserLocationDto buildPilotDto(final World world, final int userId, final SimTracker.UserStatus simStatus) {
        final Optional<User> userO = UserStorage.getInstance().findById(userId);
        if (userO.isEmpty()) {
            return null;
        }

        return buildUserLocation(world, userO.get(), userId, simStatus);
    }

    // The person of the user decides where the pilot is expected to be. A user without a person is not allowed to use the dashboard at all.
    static UserLocationDto buildUserLocation(final World world, final User user, final int userId, final SimTracker.UserStatus simStatus) {
        final Optional<Persons.Person> person = user != null && user.getPersonId() > 0
                ? world.persons().byId(user.getPersonId())
                : Optional.empty();

        // The airport where the pilot is expected to be: the location of the person of the user, null if there is no person or
        // the person is not at an airport.
        final String expectedAirport = person
                .filter(p -> p.getState() != Persons.State.Travelling && p.getLocationType() == Persons.LocationType.Airport)
                .flatMap(p -> world.airports().getIcao(p.getLocationId()))
                .orElse(null);

        // The city where the pilot is: a pilot in a city may book an aircraft at the airports of the city and see the dashboard, but cannot start a flight
        final Optional<Cities.City> pilotCity = person
                .filter(p -> p.getState() != Persons.State.Travelling && p.getLocationType() == Persons.LocationType.City)
                .flatMap(p -> world.cities().byId(p.getLocationId()));

        // Only a started flight counts: the tracker follows the next flight of the roster too (Dispatched), that is not a flight in progress and the pilot has to be at the airport
        final boolean flightInProgress = userHasActiveFlight(world, userId);

        final UserLocationDto dto = buildUserLocationDto(expectedAirport, person.map(Persons.Person::getState).orElse(null), person.isPresent(), flightInProgress,
                simStatus, pilotCity.isPresent());
        dto.setExpectedCity(pilotCity.map(Cities.City::getName).orElse(null));
        dto.setBookingAirports(person.map(p -> world.personControl().bookingAirports(p).stream().map(Airports.Airport::getIcao).toList()).orElse(List.of()));
        return dto;
    }

    // The flight of the user which has been started and is not finished yet, the person is on duty then.
    private static boolean userHasActiveFlight(final World world, final int userId) {
        return world.flightMissions().allByUserId(userId)
                .anyMatch(fm -> switch (fm.getStatus()) {
                    case Preflight, Departure, Flying, Arrival, Postflight -> true;
                    default -> false;
                });
    }

    // The verdict about the pilot: the person is expected to be Idle at an airport and the pilot has to be loaded at the same airport. A flight in
    // progress is valid (the person is OnDuty, the pilot may be nowhere). Expected and current airports are filled whenever they are known, state is
    // only the verdict. The states which block the dashboard are listed in UserLocationState.
    static UserLocationDto buildUserLocationDto(final String expectedAirport,
                                                final Persons.State personState,
                                                final boolean hasPerson,
                                                final boolean flightInProgress,
                                                final SimTracker.UserStatus simStatus) {
        return buildUserLocationDto(expectedAirport, personState, hasPerson, flightInProgress, simStatus, false);
    }

    static UserLocationDto buildUserLocationDto(final String expectedAirport,
                                                final Persons.State personState,
                                                final boolean hasPerson,
                                                final boolean flightInProgress,
                                                final SimTracker.UserStatus simStatus,
                                                final boolean personInCity) {
        final String currentAirport = "At airport".equals(simStatus.getLocationStatus()) ? simStatus.getAirportIcao() : null;

        final UserLocationState state;
        if (!hasPerson) {
            // nobody flies without a pilot
            state = UserLocationState.NO_PERSON;
        } else if (flightInProgress) {
            // flight is in progress - the check is not applied
            state = UserLocationState.OK;
        } else if (personState != Persons.State.Idle) {
            // todo ak3 a person who is Resting or Travelling can be 'woken up' or 'brought back' by the user, but it costs something
            state = UserLocationState.PERSON_NOT_IDLE;
        } else if (expectedAirport == null && personInCity) {
            // the pilot is Idle in a city: the dashboard and the booking are available, a flight is started after the pilot has travelled to the airport
            state = UserLocationState.PERSON_IN_CITY;
        } else if (expectedAirport == null) {
            // the pilot is Idle but is not at an airport (in the air, in a hotel): something is wrong, nothing can be started from there
            state = UserLocationState.PERSON_NOT_AT_AIRPORT;
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

    /**
     * The dashboard is not available if the verdict about the pilot blocks it.
     */
    static void checkDashboardIsAllowed(final UserLocationDto location) {
        if (location.getState().blocksDashboard()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Flight dashboard is not available: " + location.getState());
        }
    }

    // True if the steps of the flight are done by the sim tracker: the flight was started in this mode, or it was started before the mode was stored
    // (or it is not started yet) and the tracker is connected.
    private boolean isTrackedBySimTracker(final FlightMissions.Mission flight, final int userId) {
        return switch (flight.getTrackingMode()) {
            case SimTracker -> true;
            case Manual, Vatsim -> false;
            case None -> simTrackerBean.isUserConnected(userId);
        };
    }

    // The status of the sim of the user for the checks of a flight which the sim tracker follows: without the reports there is nothing to check.
    private SimTracker.UserStatus requireConnectedSimStatus(final int userId) {
        final SimTracker.UserStatus simStatus = simTrackerBean.getSimStatus(userId);
        if (simStatus == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "the sim tracker is not connected: no reports from the sim for a while");
        }
        return simStatus;
    }

    private void checkUserMayUseDashboard(final World world, final int userId) {
        checkUserMayUseDashboard(world, userId, false);
    }

    // The sim is not asked where the pilot is when the flight is manual: the position of the sim means nothing for it.
    private void checkUserMayUseDashboard(final World world, final int userId, final boolean ignoreSim) {
        SimTracker.UserStatus simStatus = ignoreSim ? null : simTrackerBean.getSimStatus(userId);
        if (simStatus == null) {
            simStatus = SimTracker.UserStatus.none();
        }
        final User user = UserStorage.getInstance().findById(userId).orElse(null);
        checkDashboardIsAllowed(buildUserLocation(world, user, userId, simStatus));
    }

    /**
     * @param mode "manual" or "tracker": the pilot chooses how the flight goes. Without it the flight is tracked if the sim tracker is connected
     *             (the tab of G7 does not send it) and manual otherwise.
     */
    @PostMapping("/start-flight")
    public StatusDto startFlight(@RequestAttribute("userId") int userId,
                                  @RequestParam(name = "flightId") String flightIdStr,
                                  @RequestParam(name = "mode", required = false) String mode) {
        return worldBean.modifySync(world -> {
            final boolean simTrackerConnected = simTrackerBean.isUserConnected(userId);
            final FlightMissions.TrackingMode trackingMode;
            if (mode == null) {
                trackingMode = simTrackerConnected ? FlightMissions.TrackingMode.SimTracker : FlightMissions.TrackingMode.Manual;
            } else if (mode.equals("manual")) {
                trackingMode = FlightMissions.TrackingMode.Manual;
            } else if (mode.equals("tracker")) {
                trackingMode = FlightMissions.TrackingMode.SimTracker;
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown mode '" + mode + "', expected 'manual' or 'tracker'");
            }
            final boolean tracked = trackingMode == FlightMissions.TrackingMode.SimTracker;

            checkUserMayUseDashboard(world, userId, !tracked);
            int flightId = Id.decode(flightIdStr);

            FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Dispatched, "flight status is not as expected");

            // the pilot starts from the airport where the pilot is, in any mode
            final int pilotAirportId = pilotAirportId(world, userId);
            if (pilotAirportId != flight.getDepartureAirportId()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "the pilot is at " + world.airports().getIcao(pilotAirportId).orElse("no airport (travel to the airport first)")
                        + " but the flight departs from " + world.airports().getIcao(flight.getDepartureAirportId()).orElse("?"));
            }

            // the start is open from 3 hours before the planned departure, the same for both modes
            final SimTracker.CheckResult startWindow = startWindowCheck(world, flight);
            if (!startWindow.isResult()) {
                log.warn("f/m #{} - flight-dashboard - start-flight refused, too early: {}", flightId, startWindow.getName());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "the flight can not be started, the check failed: " + startWindow.getName());
            }

            // the limits of the pilot (awake, duty and flight time) are the same for both modes
            pilotLimitChecks(world, userId).stream().filter(check -> !check.isResult()).findFirst().ifPresent(check -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "the flight can not be started, the check failed: " + check.getName());
            });

            if (tracked) {
                if (!simTrackerConnected) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "the sim tracker is not connected, the flight can only be started manually");
                }
                SimTracker.UserStatus simStatus = requireConnectedSimStatus(userId);
                checkArgument(Objects.equals(flightIdStr, simStatus.getFlightMissionId()), "sim tracker status is invalid");
                checkActionAllowed(simStatus, "start-flight");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            log.info("f/m #{} - flight-dashboard - start-flight, mode {}", flightId, trackingMode);
            // a start which fails changes nothing: the flight stays Dispatched, the pilot sees the reason and can fix the cause and try again
            final FlightMissionControl.StartResult startResult = world.flightMissionControl().start(flight);
            if (!startResult.started()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "the flight cannot be started: " + startResult.reason());
            }

            world.flightMissionControl().setTrackingMode(flight, trackingMode);
            if (tracked) {
                world.flightMissionControl().switchToExternalCoordinatesMode(flight);
            }
            if (simTrackerConnected) {
                simTrackerBean.refreshContext(userId);
            }

            return getStatus(userId);
        });
    }

    /**
     * What the pilot gets for the flight by the contract and what the pilot would get if the flight is started in the manual mode: the dashboard shows it
     * before a manual start. A flight without a contract has nothing to show.
     */
    @GetMapping("/manual-pay")
    public ManualPayDto getManualPay(@RequestAttribute("userId") int userId,
                                     @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.read(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(Id.decode(flightIdStr)).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            return buildManualPay(world, flight);
        });
    }

    static ManualPayDto buildManualPay(final World world, final FlightMissions.Mission flight) {
        final Optional<PilotContracts.Contract> contractO = world.pilotContracts().byFlightMissionId(flight.getId());
        if (contractO.isEmpty()) {
            return new ManualPayDto(false, null, null);
        }
        final PilotContracts.Contract contract = contractO.get();

        final PilotPayParams manualParams = contract.getPayParams();
        manualParams.setManual(true);
        // the income share is by the income of the transport flight of the flight (before the end of the boarding it is the sum of the bookings)
        final int income = world.transportFlightControl().getIncomeOfMission(flight);
        // the rates are of the operator of the contract (BusyBirds or ShadowJet)
        final PilotPayRates rates = OperatorPay.ratesOf(contract.getOperatorId()).orElse(PilotPayRates.BUSY_BIRDS);
        final int flightPayByContract = PilotPay.flightPay(contract, rates);
        final int incomeShareByContract = PilotPay.incomeShare(income, contract, rates);
        final int manualFlightPay = PilotPay.flightPay(contract.getBaseFlightPay(), manualParams, rates);
        final int manualIncomeShare = PilotPay.incomeShare(income, manualParams, rates);

        return new ManualPayDto(true,
                new PayDto(flightPayByContract, incomeShareByContract, flightPayByContract + incomeShareByContract),
                new PayDto(manualFlightPay, manualIncomeShare, manualFlightPay + manualIncomeShare));
    }

    @PostMapping("/reschedule")
    public StatusDto reschedule(@RequestAttribute("userId") int userId,
                                 @RequestParam(name = "flightId") String flightIdStr,
                                 @RequestParam(name = "shiftMinutes") int shiftMinutes) {
        return worldBean.modifySync(world -> {
            // the move of a flight is an action of the roster, not of the dashboard: the state of the pilot does not matter for it, as for the booking
            int flightId = Id.decode(flightIdStr);

            FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");

            final boolean simTrackerConnected = simTrackerBean.isUserConnected(userId);

            if (isTrackedBySimTracker(flight, userId)) {
                SimTracker.UserStatus simStatus = requireConnectedSimStatus(userId);
                // the tracker checks the flight which it follows; the other flights of the roster are not its business
                if (Objects.equals(flightIdStr, simStatus.getFlightMissionId())) {
                    checkActionAllowed(simStatus, "reschedule");
                }
            }

            log.info("f/m #{} - flight-dashboard - reschedule by {} min", flightId, shiftMinutes);
            world.flightMissionControl().reschedule(flight, shiftMinutes);

            if (simTrackerConnected) {
                simTrackerBean.refreshContext(userId);
            }

            return getStatus(userId);
        });
    }

    /**
     * The airports which the pilot may choose as the alternate of the flight: the nearest ones to the destination with the distances, and the chosen one (null if none).
     */
    @GetMapping("/alternates")
    public AlternatesDto getAlternates(@RequestAttribute("userId") int userId,
                                       @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.read(world -> {
            // no check of the dashboard: the alternate is a part of the planning, like the booking it does not depend on the state or place of the pilot
            final FlightMissions.Mission flight = world.flightMissions().byId(Id.decode(flightIdStr)).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final List<AlternateDto> candidates = world.flightMissionControl().alternateCandidates(flight).stream()
                    .map(c -> new AlternateDto(c.airport().getIcao(), c.airport().getName(), Math.round(c.distanceNm())))
                    .toList();
            return new AlternatesDto(world.airports().getIcao(flight.getAlternateAirportId()).orElse(null), candidates);
        });
    }

    /**
     * Sets the alternate of the flight, an empty icao clears it. Before blocks off only, see FlightMissionControl.setAlternate.
     */
    @PostMapping("/alternate")
    public StatusDto setAlternate(@RequestAttribute("userId") int userId,
                                  @RequestParam(name = "flightId") String flightIdStr,
                                  @RequestParam(name = "icao", required = false) String icao) {
        return worldBean.modifySync(world -> {
            // no check of the dashboard, see getAlternates
            final int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");

            final Airports.Airport alternate = icao == null || icao.isBlank() ? null
                    : world.airports().byIcao(icao.trim().toUpperCase()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown airport '" + icao + "'"));

            log.info("f/m #{} - flight-dashboard - alternate {}", flightId, alternate != null ? alternate.getIcao() : "cleared");
            world.flightMissionControl().setAlternate(flight, alternate);

            return getStatus(userId);
        });
    }

    /**
     * The airports which the pilot may divert to from the position of the aircraft: the departure, the declared alternate and the nearest ones, the nearest first; and the airport
     * of the divert which is already declared (null if none).
     */
    @GetMapping("/divert-candidates")
    public DivertCandidatesDto getDivertCandidates(@RequestAttribute("userId") int userId,
                                                   @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.read(world -> {
            checkUserMayUseDashboard(world, userId);
            final FlightMissions.Mission flight = world.flightMissions().byId(Id.decode(flightIdStr)).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final List<DivertCandidateDto> candidates = world.flightMissionControl().divertCandidates(flight).stream()
                    .map(c -> new DivertCandidateDto(c.airport().getIcao(), c.airport().getName(), Math.round(c.distanceNm()), c.kind().name()))
                    .toList();
            return new DivertCandidatesDto(world.airports().getIcao(flight.getDivertAirportId()).orElse(null), candidates);
        });
    }

    /**
     * Declares the divert of the flight in flight, an empty icao takes it back. Only the airports of divert-candidates are accepted.
     */
    @PostMapping("/divert")
    public StatusDto divert(@RequestAttribute("userId") int userId,
                            @RequestParam(name = "flightId") String flightIdStr,
                            @RequestParam(name = "icao", required = false) String icao) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
            final int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");

            final Airports.Airport airport = icao == null || icao.isBlank() ? null
                    : world.airports().byIcao(icao.trim().toUpperCase()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown airport " + icao));

            log.info("f/m #{} - flight-dashboard - divert {}", flightId, airport != null ? airport.getIcao() : "cleared");
            world.flightMissionControl().divert(flight, airport);

            return getStatus(userId);
        });
    }

    @PostMapping("/start-boarding")
    public StatusDto startBoarding(@RequestAttribute("userId") int userId,
                                    @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkNotNull(transportFlight, "transport flight is required for start-boarding");

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");
            checkArgument(TransportFlightHelper.flightStatusAllowsPilotToStartBoarding(transportFlight.getStatus()), "transport flight status is not as expected");

            final boolean simTrackerConnected = simTrackerBean.isUserConnected(userId);

            if (isTrackedBySimTracker(flight, userId)) {
                SimTracker.UserStatus simStatus = requireConnectedSimStatus(userId);
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

            return getStatus(userId);
        });
    }

    /**
     * The pilot stops the boarding right now (decided 2026-10-30): the transport flight goes from Boarding to WaitingForDeparture, the journeys which have not boarded are
     * too late (they are not carried), the income of the flight is what has boarded. It is not undone, the boarding is not resumed. The step is done by the pilot in both
     * modes of the flight, the sim tracker does not track it.
     */
    @PostMapping("/finish-boarding")
    public StatusDto finishBoarding(@RequestAttribute("userId") int userId,
                                     @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkNotNull(transportFlight, "transport flight is required for finish-boarding");

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding, "transport flight status is not as expected");

            final boolean simTrackerConnected = simTrackerBean.isUserConnected(userId);

            if (isTrackedBySimTracker(flight, userId)) {
                SimTracker.UserStatus simStatus = requireConnectedSimStatus(userId);
                checkArgument(Objects.equals(flightIdStr, simStatus.getFlightMissionId()), "sim tracker status is invalid");
                checkActionAllowed(simStatus, "finish-boarding");
            }

            log.info("f/m #{} - flight-dashboard - finish-boarding", flightId);
            world.transportFlightControl().waitForDeparture(transportFlight);

            if (simTrackerConnected) {
                simTrackerBean.refreshContext(userId);
            }

            return getStatus(userId);
        });
    }

    @PostMapping("/blocks-off")
    public StatusDto blocksOff(@RequestAttribute("userId") int userId,
                                @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Preflight, "flight status is not as expected");

            if (isTrackedBySimTracker(flight, userId)) {
                throw new IllegalStateException("manual blocks-off is prohibited: the flight is tracked by the sim tracker");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            log.info("f/m #{} - flight-dashboard - blocks-off", flightId);
            world.flightMissionControl().blocksOff(flight); // t/f update is inside

            return getStatus(userId);
        });
    }

    @PostMapping("/takeoff")
    public StatusDto takeoff(@RequestAttribute("userId") int userId,
                              @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Departure, "flight status is not as expected");

            if (isTrackedBySimTracker(flight, userId)) {
                throw new IllegalStateException("manual takeoff is prohibited: the flight is tracked by the sim tracker");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            log.info("f/m #{} - flight-dashboard - takeoff", flightId);
            world.flightMissionControl().takeoff(flight); // t/f update is inside

            return getStatus(userId);
        });
    }

    @PostMapping("/landing")
    public StatusDto landing(@RequestAttribute("userId") int userId,
                              @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Flying, "flight status is not as expected");

            if (isTrackedBySimTracker(flight, userId)) {
                throw new IllegalStateException("manual landing is prohibited: the flight is tracked by the sim tracker");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            final int earliestLanding = FlightMissionHelper.calcEarliestAllowedLandingTime(world, flight);
            if (world.getWorldTime() < earliestLanding) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Manual landing is not allowed earlier than " + TimeTools.hhmmOrNull(earliestLanding));
            }
            log.info("f/m #{} - flight-dashboard - landing", flightId);
            // the divert, if the pilot has declared one, otherwise the destination
            final Airports.Airport landingAirport = world.airports().byId(flight.getTargetAirportId()).orElseThrow();
            world.flightMissionControl().landing(flight, landingAirport); // t/f update is inside

            return getStatus(userId);
        });
    }

    @PostMapping("/blocks-on")
    public StatusDto blocksOn(@RequestAttribute("userId") int userId,
                               @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Arrival, "flight status is not as expected");

            if (isTrackedBySimTracker(flight, userId)) {
                throw new IllegalStateException("manual blocks-on is prohibited: the flight is tracked by the sim tracker");
            }

            //noinspection StatementWithEmptyBody
            if (vatsimTrackerBean.isUserConnected(userId)) {
                // todo ak2 vatsim support
            }

            log.info("f/m #{} - flight-dashboard - blocks-on", flightId);
            world.flightMissionControl().blocksOn(flight); // t/f update is inside

            return getStatus(userId);
        });
    }

    /**
     * After a landing at a wrong airport: the passengers stay on board and the flight goes on to the destination in a new mission (FlightMissionControl.continueNow).
     */
    @PostMapping("/continue-now")
    public StatusDto continueNow(@RequestAttribute("userId") int userId,
                                           @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
            final int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            // the new mission starts at once, so the limits of the pilot are the same as at the start of a flight
            pilotLimitChecks(world, userId).stream().filter(check -> !check.isResult()).findFirst().ifPresent(check -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "the flight can not go on, the check failed: " + check.getName());
            });

            log.info("f/m #{} - flight-dashboard - continue-now", flightId);
            world.flightMissionControl().continueNow(flight);

            if (simTrackerBean.isUserConnected(userId)) {
                simTrackerBean.refreshContext(userId);
            }

            return getStatus(userId);
        });
    }

    /**
     * "Resolve conflicts" of the roster: what the moves would be (FlightMissionControl.resolveConflicts), nothing is changed.
     */
    @GetMapping("/resolve-conflicts/preview")
    public ResolveConflictsDto resolveConflictsPreview(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> toResolveConflictsDto(world, world.flightMissionControl().resolveConflictsCascade(userId)));
    }

    /**
     * "Resolve conflicts" of the roster: the flights in conflict and the ones after them are moved later, the least that the gaps need, all or none.
     */
    @PostMapping("/resolve-conflicts")
    public ResolveConflictsDto resolveConflicts(@RequestAttribute("userId") int userId) {
        return worldBean.modifySync(world -> {
            final RosterCascade.Plan plan = world.flightMissionControl().resolveConflictsCascade(userId);
            if (!plan.isPossible()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "the flights cannot be moved: "
                        + plan.problems().stream().map(p -> FlightMissionHelper.formatRoute(world, p.mission().getId()) + " " + p.reason()).toList());
            }
            log.info("flight-dashboard - resolve conflicts of user {}, {} flights are moved", userId, plan.moves().size());
            final ResolveConflictsDto dto = toResolveConflictsDto(world, plan);
            world.flightMissionControl().resolveConflicts(userId);
            if (simTrackerBean.isUserConnected(userId)) {
                simTrackerBean.refreshContext(userId);
            }
            return dto;
        });
    }

    /**
     * "Reschedule together with the next flights": what the moves would be when the flight goes to this departure (UTC milliseconds), nothing is changed.
     */
    @GetMapping("/reschedule-with-next/preview")
    public ResolveConflictsDto rescheduleWithNextPreview(@RequestAttribute("userId") int userId,
                                                         @RequestParam(name = "flightId") String flightIdStr,
                                                         @RequestParam(name = "departureTime") long departureTimeMillis) {
        return worldBean.read(world -> {
            final FlightMissions.Mission flight = world.flightMissions().byId(Id.decode(flightIdStr)).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);
            return toResolveConflictsDto(world, rescheduleWithNextPlan(world, flight, departureTimeMillis));
        });
    }

    /**
     * "Reschedule together with the next flights": the flight goes to the departure and the flights after it are moved later when they need it, all or none.
     */
    @PostMapping("/reschedule-with-next")
    public ResolveConflictsDto rescheduleWithNext(@RequestAttribute("userId") int userId,
                                                  @RequestParam(name = "flightId") String flightIdStr,
                                                  @RequestParam(name = "departureTime") long departureTimeMillis) {
        return worldBean.modifySync(world -> {
            final int flightId = Id.decode(flightIdStr);
            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);
            final RosterCascade.Plan plan = rescheduleWithNextPlan(world, flight, departureTimeMillis);
            if (!plan.isPossible()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "the flights cannot be moved: "
                        + plan.problems().stream().map(p -> FlightMissionHelper.formatRoute(world, p.mission().getId()) + " " + p.reason()).toList());
            }
            log.info("f/m #{} - flight-dashboard - reschedule with the next flights to {}, {} flights are moved", flightId, Time.toLdt((int) (departureTimeMillis / 1000)), plan.moves().size());
            final ResolveConflictsDto dto = toResolveConflictsDto(world, plan);
            world.flightMissionControl().rescheduleWithNext(flight, (int) (departureTimeMillis / 1000));
            if (simTrackerBean.isUserConnected(userId)) {
                simTrackerBean.refreshContext(userId);
            }
            return dto;
        });
    }

    private RosterCascade.Plan rescheduleWithNextPlan(final World world, final FlightMissions.Mission flight, final long departureTimeMillis) {
        checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
        try {
            return world.flightMissionControl().rescheduleWithNextCascade(flight, (int) (departureTimeMillis / 1000));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static ResolveConflictsDto toResolveConflictsDto(final World world, final RosterCascade.Plan plan) {
        return new ResolveConflictsDto(plan.isPossible(),
                plan.moves().stream().map(m -> new CascadeMoveDto(FlightMissionHelper.formatRoute(world, m.mission().getId()), m.oldDeparture() * 1000L, m.newDeparture() * 1000L,
                        m.connectionsLost(), m.connectionPaxLost())).toList(),
                plan.problems().stream().map(p -> new CascadeProblemDto(FlightMissionHelper.formatRoute(world, p.mission().getId()), p.reason())).toList());
    }

    /**
     * After a landing at a wrong airport: what the flight on later at this departure does (FlightMissionControl.continueLater). departureTime is UTC milliseconds on the step of
     * 15 minutes, without it the earliest one is taken. The answer has the earliest and the latest departure and what the cascade does to the flights of the pilot.
     */
    @GetMapping("/continue-later/preview")
    public ContinueLaterPreviewDto continueLaterPreview(@RequestAttribute("userId") int userId,
                                                        @RequestParam(name = "flightId") String flightIdStr,
                                                        @RequestParam(name = "departureTime", required = false) Long departureTimeMillis) {
        return worldBean.read(world -> {
            checkUserMayUseDashboard(world, userId);
            final FlightMissions.Mission flight = world.flightMissions().byId(Id.decode(flightIdStr)).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);
            checkWaitingForDecision(world, flight);

            final int step = RosterForecast.RESCHEDULE_OPTION_STEP;
            final int earliest = ceil(world.getWorldTime() + FlightMissionControl.CONTINUE_LATER_MIN_DELAY, step);
            final int latest = Math.floorDiv(world.getWorldTime() + FlightMissionControl.RESCHEDULE_MAX_SHIFT_MINUTES * Time.ONE_MINUTE, step) * step;
            final int departure = departureTimeMillis == null ? earliest : checkDeparture(departureTimeMillis, earliest, latest, step);

            final FlightMissionControl control = world.flightMissionControl();
            final RosterCascade.Plan plan = control.continueLaterCascade(flight, departure);
            return new ContinueLaterPreviewDto(
                    world.airports().getIcao(flight.getActualLandingAirportId()).orElse("?"),
                    world.airports().getIcao(flight.getDestinationAirportId()).orElse("?"),
                    earliest * 1000L, latest * 1000L, step / Time.ONE_MINUTE,
                    departure * 1000L, control.continueLaterArrival(flight, departure) * 1000L,
                    plan.isPossible(),
                    plan.moves().stream().map(m -> new CascadeMoveDto(FlightMissionHelper.formatRoute(world, m.mission().getId()), m.oldDeparture() * 1000L, m.newDeparture() * 1000L,
                            m.connectionsLost(), m.connectionPaxLost())).toList(),
                    plan.problems().stream().map(p -> new CascadeProblemDto(FlightMissionHelper.formatRoute(world, p.mission().getId()), p.reason())).toList());
        });
    }

    /**
     * After a landing at a wrong airport: the pilot flies on later (FlightMissionControl.continueLater). The limits of the pilot do not stop it, it is for the pilot who cannot fly now.
     */
    @PostMapping("/continue-later")
    public StatusDto continueLater(@RequestAttribute("userId") int userId,
                                   @RequestParam(name = "flightId") String flightIdStr,
                                   @RequestParam(name = "departureTime") long departureTimeMillis) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
            final int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);
            checkWaitingForDecision(world, flight);

            final int step = RosterForecast.RESCHEDULE_OPTION_STEP;
            final int earliest = ceil(world.getWorldTime() + FlightMissionControl.CONTINUE_LATER_MIN_DELAY, step);
            final int latest = Math.floorDiv(world.getWorldTime() + FlightMissionControl.RESCHEDULE_MAX_SHIFT_MINUTES * Time.ONE_MINUTE, step) * step;
            final int departure = checkDeparture(departureTimeMillis, earliest, latest, step);

            final RosterCascade.Plan plan = world.flightMissionControl().continueLaterCascade(flight, departure);
            if (!plan.isPossible()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "the flights after it can not be moved: "
                        + plan.problems().stream().map(p -> FlightMissionHelper.formatRoute(world, p.mission().getId()) + " " + p.reason()).toList());
            }

            log.info("f/m #{} - flight-dashboard - continue-later at {}", flightId, Time.toLdt(departure));
            world.flightMissionControl().continueLater(flight, departure);

            if (simTrackerBean.isUserConnected(userId)) {
                simTrackerBean.refreshContext(userId);
            }

            return getStatus(userId);
        });
    }

    private static int ceil(final int time, final int step) {
        return (time + step - 1) / step * step;
    }

    private static int checkDeparture(final long departureTimeMillis, final int earliest, final int latest, final int step) {
        final int departure = (int) (departureTimeMillis / 1000);
        if (departureTimeMillis % 1000 != 0 || departure % step != 0 || departure < earliest || departure > latest) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the departure should be on the step of " + step / Time.ONE_MINUTE + " minutes, from the earliest to the latest time");
        }
        return departure;
    }

    // the flight has landed at a wrong airport and waits for the decision of the pilot (the same condition as the actions of the decision)
    private static void checkWaitingForDecision(final World world, final FlightMissions.Mission flight) {
        final TransportFlights.Flight tf = world.transportFlights().byFlightMissionId(flight.getId()).orElse(null);
        if (!isWaitingForDecisionAfterWrongLanding(flight) || tf == null || tf.getStatus() != TransportFlights.Status.WaitingForDeboarding) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "the flight does not wait for the decision after a landing at a wrong airport");
        }
    }

    /**
     * The flight on later (UTC milliseconds): the earliest and the latest departure (on the step of stepMinutes), the departure which the moves are told for, and the arrival of the
     * new flight. possible: the flights after it can be moved (problems say why not). moves: the flights of the pilot which are moved after the new one, connectionsLost: the
     * journeys with two flights (and the passengers in them) which lose the second flight by the move.
     */
    @AllArgsConstructor
    @Data
    public static class ContinueLaterPreviewDto {
        private final String from;
        private final String to;
        private final long earliest;
        private final long latest;
        private final int stepMinutes;
        private final long departure;
        private final long arrival;
        private final boolean possible;
        private final List<CascadeMoveDto> moves;
        private final List<CascadeProblemDto> problems;
    }

    @AllArgsConstructor
    @Data
    public static class ResolveConflictsDto {
        private final boolean possible;
        private final List<CascadeMoveDto> moves;
        private final List<CascadeProblemDto> problems;
    }

    @AllArgsConstructor
    @Data
    public static class CascadeMoveDto {
        private final String route;
        private final long oldDep;
        private final long newDep;
        private final int connectionsLost;
        private final int connectionPaxLost;
    }

    @AllArgsConstructor
    @Data
    public static class CascadeProblemDto {
        private final String route;
        private final String reason;
    }

    /**
     * After a landing at a wrong airport: the pilot cancels the transportation, the passengers get off there (TransportFlightControl.cancelTransportation).
     */
    @PostMapping("/cancel-transport")
    public StatusDto cancelTransport(@RequestAttribute("userId") int userId,
                                     @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
            final int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);
            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Postflight, "flight status is not as expected");

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);
            checkNotNull(transportFlight, "transport flight is required for cancel-transport");

            log.info("f/m #{} - flight-dashboard - cancel-transport", flightId);
            world.transportFlightControl().cancelTransportation(transportFlight);

            if (simTrackerBean.isUserConnected(userId)) {
                simTrackerBean.refreshContext(userId);
            }

            return getStatus(userId);
        });
    }

    @PostMapping("/start-deboarding")
    public StatusDto startDeboarding(@RequestAttribute("userId") int userId,
                                      @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
            int flightId = Id.decode(flightIdStr);

            final FlightMissions.Mission flight = world.flightMissions().byId(flightId).orElseThrow();
            checkIfFlightRelatesToUser(flight, userId);

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            checkNotNull(transportFlight, "transport flight is required for start-deboarding");

            checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");
            checkArgument(flight.getStatus() == FlightMissions.Status.Postflight, "flight status is not as expected");
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForDeboarding, "transport flight status is not as expected");
            checkArgument(!FlightMissionHelper.isLandedAtWrongAirport(flight), "the flight has landed not at the destination: continue to the destination or cancel the transportation");

            final boolean simTrackerConnected = simTrackerBean.isUserConnected(userId);

            if (isTrackedBySimTracker(flight, userId)) {
                SimTracker.UserStatus simStatus = requireConnectedSimStatus(userId);
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

            return getStatus(userId);
        });
    }

    @PostMapping("/finish-flight")
    public StatusDto finish(@RequestAttribute("userId") int userId,
                             @RequestParam(name = "flightId") String flightIdStr) {
        return worldBean.modifySync(world -> {
            checkUserMayUseDashboard(world, userId);
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

            return getStatus(userId);
        });
    }

    private static void checkIfFlightRelatesToUser(FlightMissions.Mission flight, int userId) {
        checkArgument(flight.getUserId() == userId, "flight does not relate to the user");
    }

    private static void checkActionAllowed(SimTracker.UserStatus simStatus, String actionName) {
        boolean actionAllowed = simStatus.getActions().stream().filter(a -> a.getName().equals(actionName)).findFirst().map(SimTracker.UserAction::isAllowed).orElse(false);
        checkArgument(actionAllowed, "'" + actionName + "' should be allowed");
    }

    private MyFlightsDto toMyFlightsDto(World world, FlightMissions.Mission flight, RosterForecast.Entry forecast, RosterForecast.Choices rescheduleChoices) {
        checkArgument(flight.getCharacterMode() == FlightMissions.CharacterMode.PC, "flight should be in PC mode");

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(flight.getAircraftId()).orElseThrow();
        final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flight.getId()).orElse(null);

        final AircraftDto aircraftDto = new AircraftDto(
                Id.encode(aircraft.getId()),
                world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao(),
                aircraft.getRegNo(),
                aircraft.getLocationStatus().name(),
                aircraft.getOperationalStatus().name(),
                world.aircraftOperators().byId(aircraft.getAircraftOperatorId()).map(AircraftOperators.AircraftOperator::getName).orElse(null)
        );

        final FlightDto flightDto = new FlightDto(
                Id.encode(flight.getId()),
                flight.getStatus().name(),
                getNextPlannedFlightMissionStatus(flight, world),
                getFlightMissionShownElements(flight, transportFlight, world),
                world.airports().getIcao(flight.getDepartureAirportId()).orElseThrow(),
                world.airports().getIcao(flight.getDestinationAirportId()).orElseThrow(),
                TimeTools.ymdOrNull(flight.getPlannedDepartureWorldTime()),
                TimeTools.hhmmOrNull(flight.getPlannedDepartureWorldTime()),
                TimeTools.hhmmOrNull(flight.getPlannedArrivalWorldTime()),
                FlightMissionHelper.isLandedAtWrongAirport(flight),
                flight.getActualDepartureWorldTime() != 0 ? flight.getActualDepartureWorldTime() * 1000L : null,
                flight.getActualArrivalWorldTime() != 0 ? flight.getActualArrivalWorldTime() * 1000L : null,
                FlightMissionHelper.isLandedAtWrongAirport(flight) ? world.airports().getIcao(flight.getActualLandingAirportId()).orElse(null) : null,
                FlightMissionHelper.isLandedAtUnplannedAirport(flight)
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
                transportFlight.getPaxOnBoard(),
                // the carried passengers (the ones on board at the takeoff) are told after the landing, in the postflight and for a finished flight; before it the roster shows the
                // passengers on board (onBoard), which go down in the deboarding
                flight.getStatus() == FlightMissions.Status.Postflight || flight.getStatus() == FlightMissions.Status.Finished ? transportFlight.getPaxCarried() : null,
                // zero for a flight which landed at a wrong airport: nothing is paid for it, the passengers' price goes to the continuation flight
                world.transportFlightControl().getIncomeOfMission(flight)
        ) : null;

        // a flight which has not started yet and does not depart where the pilot is expected: the roster tells it above the flight (nothing is forbidden)
        final boolean notStarted = flight.getStatus() == FlightMissions.Status.Dispatched || flight.getStatus() == FlightMissions.Status.PlannedManually
                || flight.getStatus() == FlightMissions.Status.PlannedViaSchedule;
        final PlaceProblemDto placeProblem = notStarted
                ? PlaceProblemDto.check(world, flight.getUserId(), flight.getPlannedDepartureWorldTime(), flight.getId(), flight.getDepartureAirportId())
                : null;

        return new MyFlightsDto(aircraftDto, flightDto, transportFlightDto, forecast != null ? toForecastDto(world, forecast) : null,
                rescheduleChoices != null ? toRescheduleDto(world, rescheduleChoices) : null, placeProblem);
    }

    private String getNextPlannedFlightMissionStatus(final FlightMissions.Mission flight, final World world) {
        return switch (flight.getStatus()) {
            case Dispatched -> FlightMissions.Status.Preflight.name() + " at " + TimeTools.hhmmOrNull(FlightMissionHelper.calcPreflightStartTime(world, flight));
            case Preflight -> FlightMissions.Status.Departure.name() + " when Captain decides";
            case Departure -> FlightMissions.Status.Flying.name() + " when Captain decides";
            case Flying -> FlightMissions.Status.Arrival.name() + " not earlier than " + TimeTools.hhmmOrNull(FlightMissionHelper.calcEarliestAllowedLandingTime(world, flight));
            case Arrival -> FlightMissions.Status.Postflight.name() + " just after Blocks On";
            case Postflight -> FlightMissions.Status.Finished.name() + " after Deboarding";
            default -> null;
        };
    }

    private String getFlightMissionShownElements(final FlightMissions.Mission flight, final TransportFlights.Flight transportFlight, final World world) {
        return switch (flight.getStatus()) {
            case Dispatched -> (FlightMissionHelper.calcPreflightStartTime(world, flight) <= world.getWorldTime()) ? "start" : "start-early-with-caution";
            case Preflight -> (transportFlight == null || transportFlight.getStatus() == TransportFlights.Status.WaitingForDeparture) ? "blocks-off" : "blocks-off-disabled";
            case Departure -> "takeoff";
            case Flying -> (FlightMissionHelper.calcEarliestAllowedLandingTime(world, flight) <= world.getWorldTime()) ? "landing" : "landing-disabled";
            case Arrival -> "blocks-on";
            case Postflight -> (transportFlight == null || transportFlight.getStatus() == TransportFlights.Status.Finished) ? "finish" : "finish-disabled";
            default -> null;
        };
    }

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

    private String getTransportFlightShownElements(final TransportFlights.Flight transportFlight, final FlightMissions.Mission flight) {
        return switch (transportFlight.getStatus()) {
            case Scheduled -> "sold";
            case CheckIn, WaitingForBoarding -> "sold,check-in," + (flight.getStatus() == FlightMissions.Status.Preflight ? "start-boarding" : "start-boarding-disabled");
            case Boarding -> "sold,check-in,on-board";
            case WaitingForDeparture, Departure, Flying -> "on-board";
            case Arrival -> "on-board,start-deboarding-disabled";
            case WaitingForDeboarding -> "on-board,start-deboarding";
            case Deboarding -> "on-board";
            default -> null;
        };
    }

    // An airport the pilot may choose as the alternate: its code, name and the distance to the destination of the flight in nautical miles
    @Data
    @AllArgsConstructor
    public static class AlternateDto {
        private final String icao;
        private final String name;
        private final long distanceNm;
    }

    // An airport the pilot may divert to: its code, name, the distance from the aircraft in nautical miles and the kind (Departure, DeclaredAlternate or CloseOption)
    @Data
    @AllArgsConstructor
    public static class DivertCandidateDto {
        private final String icao;
        private final String name;
        private final long distanceNm;
        private final String kind;
    }

    // The candidates for the divert of a flight and the declared one (null if none)
    @Data
    @AllArgsConstructor
    public static class DivertCandidatesDto {
        private final String chosen;
        private final List<DivertCandidateDto> candidates;
    }

    // The candidates for the alternate of a flight and the chosen one (null if none)
    @Data
    @AllArgsConstructor
    public static class AlternatesDto {
        private final String chosen;
        private final List<AlternateDto> candidates;
    }

    // The pay of a flight for the pilot and its parts
    @Data
    @AllArgsConstructor
    public static class PayDto {
        private final int flightPay;
        private final int incomeShare;
        private final int total;
    }

    // What the contract promises (byContract) and what is left if the flight is started manually (ifManual); both are null if the flight has no contract
    @Data
    @AllArgsConstructor
    public static class ManualPayDto {
        private final boolean hasContract;
        private final PayDto byContract;
        private final PayDto ifManual;
    }

    @Data
    @AllArgsConstructor
    public static class MyFlightsResponse {
        private List<MyFlightsDto> flights;
    }

    @Data
    @AllArgsConstructor
    public static class MyFlightsDto {
        private AircraftDto aircraft;
        private FlightDto flight;
        private TransportFlightDto transportFlight;
        // the expected times, the divert and the conflict of the flight; null for a flight which has none of them (and for a flight which is over)
        private ForecastDto forecast;
        // the times to which the flight can be moved, null for a flight which cannot be moved
        private RescheduleDto reschedule;
        // the pilot is not expected at the airport where the flight departs (the flight before lands elsewhere), null when it is fine, not known or the flight has started
        private PlaceProblemDto placeProblem;
    }

    /**
     * The choices of the move of a flight in the roster (UTC milliseconds). from, to: the departures without a conflict with the neighbours, every stepMinutes minutes (from is later
     * than to when there are none). options: the times to choose from with what each of them does to the flight after (the flight after leaves at nextDep and is shortfallMinutes
     * short of the gap, 0 when there is no conflict; nextDep is null when there is no flight after); the current departure is not among them. before, after: the neighbours
     * (the arrival of the flight before, the planned departure of the flight after); null when there is none. reason: "earliest" when the flight is already at the earliest time.
     */
    @AllArgsConstructor
    @Data
    public static class RescheduleDto {
        private final long from;
        private final long to;
        private final int stepMinutes;
        private final List<RescheduleOptionDto> options;
        private final NeighbourDto before;
        private final NeighbourDto after;
        private final String reason;
    }

    @AllArgsConstructor
    @Data
    public static class RescheduleOptionDto {
        private final long dep;
        private final Long nextDep;
        private final int shortfallMinutes;
    }

    @AllArgsConstructor
    @Data
    public static class NeighbourDto {
        private final String route;
        private final long time;
    }

    private static RescheduleDto toRescheduleDto(final World world, final RosterForecast.Choices choices) {
        final RosterForecast.Entry before = choices.before();
        final RosterForecast.Entry after = choices.after();
        return new RescheduleDto(
                choices.window().earliestDeparture() * 1000L,
                choices.window().latestDeparture() * 1000L,
                RosterForecast.RESCHEDULE_OPTION_STEP / Time.ONE_MINUTE,
                choices.options().stream()
                        .map(o -> new RescheduleOptionDto(o.departure() * 1000L, o.nextDeparture() != 0 ? o.nextDeparture() * 1000L : null, o.shortfallMinutes()))
                        .toList(),
                before == null ? null : new NeighbourDto(FlightMissionHelper.formatRoute(world, before.mission().getId()),
                        (before.deliveryNotBefore() != 0 ? before.deliveryNotBefore() : before.expectedArrival()) * 1000L),
                after == null ? null : new NeighbourDto(FlightMissionHelper.formatRoute(world, after.mission().getId()), after.mission().getPlannedDepartureWorldTime() * 1000L),
                choices.options().isEmpty() && choices.atEarliest() ? "earliest" : null);
    }

    // the expected time is told when it is 5 minutes or more from the planned one: less than that is not a delay, the estimates move by themselves
    private static final int FORECAST_SHOWN_FROM_SECONDS = 5 * Time.ONE_MINUTE;

    /**
     * What the roster is going to be like for the flight. The times are UTC milliseconds, null when the expected time is the planned one. conflict: see RosterForecast.
     */
    @AllArgsConstructor
    @Data
    public static class ForecastDto {
        private final Long expDep;
        // the arrival at the airport the flight goes to now: the airport of the divert for a diverted flight
        private final Long expArr;
        // the earliest time a diverted flight with passengers on board can be at the destination of the plan (it can be later), null without it
        private final Long destNotBefore;
        // the airport of the divert (ICAO) of a flight which is diverted with passengers on board
        private final String divertTo;
        private final ConflictDto conflict;
    }

    /**
     * level: "red" (the flight before it is as it is: late, diverted) or "yellow" (it appears only after the flights before it are moved); shortfallMinutes: how many minutes
     * short of the gap between the flights; causeRoute: the flight before it; estimate: the time of its arrival is an estimate (a divert); suggestedDep: the earliest departure
     * without the conflict.
     */
    @AllArgsConstructor
    @Data
    public static class ConflictDto {
        private final String level;
        private final int shortfallMinutes;
        private final String causeRoute;
        private final boolean estimate;
        private final long suggestedDep;
    }

    private static ForecastDto toForecastDto(final World world, final RosterForecast.Entry entry) {
        final FlightMissions.Mission mission = entry.mission();
        final boolean departureMoved = Math.abs(entry.expectedDeparture() - mission.getPlannedDepartureWorldTime()) >= FORECAST_SHOWN_FROM_SECONDS;
        final boolean arrivalMoved = Math.abs(entry.expectedArrival() - mission.getPlannedArrivalWorldTime()) >= FORECAST_SHOWN_FROM_SECONDS;
        final RosterForecast.Conflict conflict = entry.conflict();
        final String divertTo = entry.divertReminder() && entry.divertAirport() != null ? entry.divertAirport().getIcao() : null;
        if (!departureMoved && !arrivalMoved && divertTo == null && conflict == null) {
            return null;
        }
        return new ForecastDto(
                departureMoved ? entry.expectedDeparture() * 1000L : null,
                arrivalMoved || divertTo != null ? entry.expectedArrival() * 1000L : null,
                entry.deliveryNotBefore() != 0 ? entry.deliveryNotBefore() * 1000L : null,
                divertTo,
                conflict == null ? null : new ConflictDto(
                        conflict.level().name().toLowerCase(),
                        conflict.shortfallMinutes(),
                        FlightMissionHelper.formatRoute(world, conflict.cause().getId()),
                        conflict.estimate(),
                        conflict.suggestedDeparture() * 1000L));
    }

    @AllArgsConstructor
    @Data
    public static class StatusDto {
        // Who does the steps of the flight (Blocks Off, Takeoff, Landing, Blocks On): the sim tracker or the pilot with buttons (manual). The mode is
        // chosen when the flight is started and does not change after that; null until then (and when there is no flight).
        private final TrackingMode mode;
        // What the dashboard may offer now, the same list for both modes
        private final List<SimTracker.UserAction> actions;
        private final SimTracker.UserStatus sim;
        private final VatsimStatusDto vatsim;
        private final FlightUltraDto flight;
        private final UserLocationDto pilot;
        // What the flight does to the next flights of the pilot (late, or diverted with passengers); null when there is nothing to tell
        private final ScheduleWarningDto scheduleWarning;
    }

    /**
     * kind: "late" (the flight is late so that the next flight of the pilot cannot start by its plan, conflict is always true) or "divert" (the passengers are expected at the
     * destination all the same; conflict tells whether the next flight is in conflict for sure). The times are HH:MM, with the days from today when it is not today.
     */
    @AllArgsConstructor
    @Data
    public static class ScheduleWarningDto {
        private final String kind;
        private final boolean conflict;
        private final String destinationIcao;
        // the estimated blocks on (late), the earliest blocks on at the destination (divert)
        private final String blocksOn;
        private final String nextFlightRoute;
        private final String nextFlightDeparture;
        private final int shortfallMinutes;
    }

    private static ScheduleWarningDto toScheduleWarningDto(final World world, final ScheduleWarnings.Warning warning) {
        final FlightMissions.Mission next = warning.nextFlight();
        return new ScheduleWarningDto(
                warning.kind().name().toLowerCase(),
                warning.conflict(),
                warning.destination() != null ? warning.destination().getIcao() : null,
                TimeTools.hhmmPlusDaysOrNull(warning.earliestBlocksOn()),
                next != null ? FlightMissionHelper.formatRoute(world, next.getId()) : null,
                next != null ? TimeTools.hhmmPlusDaysOrNull(next.getPlannedDepartureWorldTime()) : null,
                warning.shortfallMinutes());
    }

    @AllArgsConstructor
    @Data
    public static class UserLocationDto {
        private final UserLocationState state;
        private final String expectedAirport;
        private final String currentAirport;
        // the city where the pilot is, null when the pilot is not in a city
        private String expectedCity;
        // the airports where the pilot may book an aircraft: its airport, or the airports of its city
        private List<String> bookingAirports = List.of();

        public UserLocationDto(final UserLocationState state, final String expectedAirport, final String currentAirport) {
            this.state = state;
            this.expectedAirport = expectedAirport;
            this.currentAirport = currentAirport;
        }
    }

    public enum TrackingMode {
        SIM_TRACKER,
        MANUAL,
        VATSIM
    }

    public enum UserLocationState {
        OK,
        // the user has no person, nobody flies without a pilot
        NO_PERSON,
        // the person is not Idle (Resting, Travelling, OnDuty without a flight)
        PERSON_NOT_IDLE,
        // the person is Idle but is not at an airport and not in a city
        PERSON_NOT_AT_AIRPORT,
        // the person is Idle in a city: the dashboard works, a flight can be booked and is started after the pilot has travelled to the airport
        PERSON_IN_CITY,
        // the pilot is loaded at an airport which is not the airport of the person
        WRONG_AIRPORT,
        // the sim tracker does not say where the pilot is: it is not connected, the pilot is in the air or on the ground out of any airport
        UNKNOWN;

        /**
         * The actions of the dashboard are refused in these states. UNKNOWN does not block: the dashboard is used without the sim tracker too.
         */
        public boolean blocksDashboard() {
            return this == NO_PERSON || this == PERSON_NOT_IDLE || this == PERSON_NOT_AT_AIRPORT || this == WRONG_AIRPORT;
        }
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
        // the name of the operator of the aircraft (BusyBirds, ShadowJet), null if the aircraft has no operator
        private String operatorName;
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
        // the flight has landed not at its destination (a divert or an unplanned landing)
        private boolean diverted;
        // the actual blocks off and blocks on (UTC milliseconds), null while they have not happened
        private Long actualDeparture;
        private Long actualArrival;
        // the airport (ICAO) where the flight has landed when it is not its destination, null otherwise
        private String landedIcao;
        // the landing at a wrong airport was not declared as the divert: an unplanned landing (the roster says "unexpectedly")
        private boolean unplanned;
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
        // the passengers carried: the ones on board at the takeoff, told in the postflight and for a finished flight, otherwise null
        private Integer carriedPax;
        // the income of the flight: the sum of the price of its journeys (TransportFlightExtras), while the boarding goes it is what the bookings have added, after the
        // boarding it is what boarded; 0 for a flight which has none (and for the flights which were before this field, they are 0 and it is fine)
        private int income;
    }
}
