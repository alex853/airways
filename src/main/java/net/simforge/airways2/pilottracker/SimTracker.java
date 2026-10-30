package net.simforge.airways2.pilottracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.world.WorldAccess;
import net.simforge.airways2.pilottracker.track.TrackLeg;
import net.simforge.airways2.pilottracker.track.TrackPosition;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.world.processors.TransportFlightHelper;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SimTracker {
    private static final Logger log = LoggerFactory.getLogger(SimTracker.class);
    private static final DecimalFormat df1 = new DecimalFormat("0.0");

    // a position older than this means the track trail has a gap, the speed can't be measured over such a gap
    private static final long STALE_POSITION_MILLIS = 10 * 60 * 1000;
    // saved contexts with a position older than this are dropped on restore, the user is not on the line anymore
    private static final long MAX_RESTORED_CONTEXT_AGE_MILLIS = 30 * 60 * 1000;
    // The user is connected while the reports come (the client sends one every 10 seconds). Without a report the connection is lost after 3 minutes,
    // and after 10 minutes if the user has a flight which is started (Preflight or further): a pause of the sim or a lost network must not drop it.
    static final long CONNECTED_IDLE_MILLIS = 3 * 60 * 1000;
    static final long CONNECTED_ACTIVE_FLIGHT_MILLIS = 10 * 60 * 1000;

    private WorldAccess worldAccess;
    private List<AirportInfo> airportInfos;

    private final Map<Integer, Context> userContexts = new ConcurrentHashMap<>();

    private final SimTrackerContextStore contextStore;
    // the saved file must not be overwritten by an empty state before the contexts are restored
    private volatile boolean contextsRestored = false;

    public SimTracker() {
        this(Paths.get("./sim-tracker/contexts.json"));
    }

    public SimTracker(final Path contextsFile) {
        this.contextStore = new SimTrackerContextStore(contextsFile);
    }

    public void processPosrep(int userId, String posrep) {
        checkArgument(userId > 0);
        checkNotNull(posrep);

        E0Posrep parsed = E0Posrep.parse(posrep);
        TrackPosition position = toTrackPosition(parsed);

        Context oldContext = userContexts.get(userId);
        if (!userContexts.containsKey(userId)) {
            oldContext = Context.forUser(userId);
        }

        // todo ak2 rework all those staff into one single modifySync processing splitted somehow
        Context newContext = validateOrFindCurrentFlightMission(oldContext);

        // the old position stays for takeoff/landing detection, but the track trail starts again after a gap
        // (the application was restarted, the client was offline), otherwise the speed would be measured over the gap
        final boolean oldPositionIsStale = oldContext.position != null
                && position.getTime() - oldContext.position.getTime() > STALE_POSITION_MILLIS;
        if (oldPositionIsStale) {
            log.info("SIM TRACKER: user {} - old position is {} s old, track trail is started again",
                    userId, (position.getTime() - oldContext.position.getTime()) / 1000);
        }
        List<TrackLeg> newTrackTrail = oldPositionIsStale
                ? TrackLeg.buildNewTrackTrail(null, null, position)
                : TrackLeg.buildNewTrackTrail(oldContext.trackTrail, oldContext.position, position);

        newContext = newContext.toBuilder()
                .position(position)
                .parkingBrake(parsed.isParkingBrake())
                .numberOfEnginesRunning(parsed.getNumberOfEnginesRunning())
                .trackTrail(newTrackTrail)
                .measuredGs(TrackLeg.calculateGroundspeed(newTrackTrail))
                .build();

        updateAircraftLocation(newContext);

        newContext = processEvents(oldContext, newContext);

        newContext = doChecks(newContext);
        newContext = updateActiveFlight(newContext);

        userContexts.put(userId, newContext);
    }

    // A flight which the pilot started manually is not followed by the tracker: its steps are done with the buttons of the dashboard and the position of
    // the aircraft is flown by the world, the reports of the sim change nothing in it.
    private boolean isManualFlight(final Context context) {
        if (context.getFlightMissionId() == null) {
            return false;
        }
        return worldAccess.read(world -> world.flightMissions().byId(context.getFlightMissionId())
                .map(fm -> fm.getTrackingMode() == FlightMissions.TrackingMode.Manual)
                .orElse(false));
    }

    private void updateAircraftLocation(Context context) {
        if (context.getFlightMissionId() == null || isManualFlight(context)) {
            return;
        }

        worldAccess.modifySync(world -> {
            Optional<FlightMissions.Mission> mission = world.flightMissions().byId(context.getFlightMissionId());
            if (mission.isEmpty()) {
                log.warn("no mission found!!!");
                return null;
            }
            Optional<Aircrafts.Aircraft> aircraft = world.aircrafts().byId(mission.get().getAircraftId());
            if (aircraft.isEmpty()) {
                log.warn("no aircraft found!!!");
                return null;
            }
            // the position goes to the flight mission control: it moves the aircraft and counts the night time of the flight
            world.flightMissionControl().moveAircraft(mission.get(), context.position.getCoords().getLat(), context.position.getCoords().getLon(),
                    context.position.getHeading(), context.position.getAltitude());

            return null;
        });
    }

    private Context processEvents(Context oldContext, Context newContext) {
        if (newContext.getFlightMissionId() == null || isManualFlight(newContext)) {
            return newContext;
        }
        Context result = newContext;

        boolean takeoffEvent = oldContext.position != null && oldContext.position.isOnGround() && !newContext.position.isOnGround();
        boolean landingEvent = oldContext.position != null && !oldContext.position.isOnGround() && newContext.position.isOnGround();

        boolean newRunningAndMoving = newContext.measuredGs > 0 && !newContext.parkingBrake && newContext.numberOfEnginesRunning > 0;
        boolean newStoppedAndShutdown = newContext.measuredGs == 0 && newContext.parkingBrake && newContext.numberOfEnginesRunning == 0;

        log.warn("SIM TRACKER EVENTS: takeoffEvent={}, landingEvent={}, newRunningAndMoving={}, newStoppedAndShutdown={}",
                takeoffEvent, landingEvent, newRunningAndMoving, newStoppedAndShutdown);

        FlightMissions.Status fmStatus = worldAccess.read(world -> world.flightMissions().byId(newContext.getFlightMissionId()).orElseThrow().getStatus());

        if (fmStatus == FlightMissions.Status.Preflight) {
            if (newRunningAndMoving) {
                log.info("SIM TRACKER: BLOCKS OFF detected");
                FlightMissionActions.mission_blocksOff(worldAccess, "sim", newContext.getFlightMissionId());
            }
        } else if (fmStatus == FlightMissions.Status.Departure) {
            if (takeoffEvent) {
                log.info("SIM TRACKER: TAKEOFF detected");
                FlightMissionActions.mission_takeoff(worldAccess, "sim", newContext.getFlightMissionId());
            }
        } else if (fmStatus == FlightMissions.Status.Flying) {
            if (landingEvent) {
                log.info("SIM TRACKER: LANDING detected");
                FlightMissionActions.mission_landing(worldAccess, "sim", newContext.getFlightMissionId(), newContext.position.getAirportIcao());
            }
        } else if (fmStatus == FlightMissions.Status.Arrival) {
            if (newStoppedAndShutdown) {
                log.info("SIM TRACKER: BLOCKS ON detected");
                FlightMissionActions.mission_blocksOn(worldAccess, "sim", newContext.getFlightMissionId());
            }
        }

        return result;
    }

    public void refreshContext(int userId) {
        checkArgument(userId > 0);

        Context oldContext = userContexts.get(userId);
        if (!userContexts.containsKey(userId)) {
            return;
        }

        Context newContext = validateOrFindCurrentFlightMission(oldContext);
        newContext = doChecks(newContext);
        newContext = updateActiveFlight(newContext);

        userContexts.put(userId, newContext);
    }

    // Whether the user has a flight which is started (Preflight or further, a flight which is only Dispatched does not count): the connection of the
    // user lives longer then. It is kept in the context so the status of the user does not have to ask the world.
    private Context updateActiveFlight(final Context context) {
        final boolean activeFlight = worldAccess.read(world -> world.flightMissions().allByUserId(context.getUserId())
                .anyMatch(fm -> switch (fm.getStatus()) {
                    case Preflight, Departure, Flying, Arrival, Postflight -> true;
                    default -> false;
                }));
        return activeFlight == context.isActiveFlight() ? context : context.toBuilder().activeFlight(activeFlight).build();
    }

    private Context doChecks(Context context) {
        if (context.getFlightMissionId() == null) {
            return context;
        }
        if (isManualFlight(context)) {
            // the dashboard offers the manual actions for such a flight
            return context.toBuilder().actions(new ArrayList<>()).build();
        }

        return worldAccess.read(world -> {
            Optional<FlightMissions.Mission> fm = world.flightMissions().byId(context.getFlightMissionId());

            if (fm.isEmpty()) {
                log.warn("Flight mission {} not found!!!", context.getFlightMissionId());
                return context;
            }

            Optional<TransportFlights.Flight> tf = world.transportFlights().byFlightMissionId(fm.get().getId());

            List<UserAction> actions = new ArrayList<>();

            FlightMissions.Status fmStatus = fm.map(FlightMissions.Mission::getStatus).orElseThrow();
            switch (fmStatus) {
                case Dispatched -> {
                    actions.add(UserAction.build("start-flight", new Check[]{
                            departureLocationCheck(context, world, fm.get()),
                            parkingBrakeSetCheck(context),
                            enginesShutdownCheck(context),
                            aircraftStationaryCheck(context)}));
                    actions.add(rescheduleAction(world, fm.get()));
                }
                case Preflight -> {
                    if (tf.isPresent()) {
                        if (TransportFlightHelper.flightStatusAllowsPilotToStartBoarding(tf.get().getStatus())) {
                            actions.add(UserAction.build("start-boarding", new Check[]{
                                    departureLocationCheck(context, world, fm.get()),
                                    parkingBrakeSetCheck(context),
                                    enginesShutdownCheck(context),
                                    aircraftStationaryCheck(context)}));
                        } else if (tf.get().getStatus() == TransportFlights.Status.Boarding) {
                            // the pilot stops the boarding by hand, the sim has nothing to check here
                            actions.add(UserAction.build("finish-boarding", new Check[]{}));
                        } else if (tf.get().getStatus() == TransportFlights.Status.WaitingForDeparture) {
                            actions.add(UserAction.build("blocks-off", new Check[]{}));
                        }
                    } else {
                        actions.add(UserAction.build("blocks-off", new Check[]{}));
                    }
                    actions.add(rescheduleAction(world, fm.get()));
                }
                case Departure -> {
                    // the correct location
                    // aircraft is on ground
                    // takeoff
                }
                case Flying -> {
                    // manual landing
                }
                case Arrival -> {
                    // blocks on
                }
                case Postflight -> {
                    if (tf.isPresent()) {
                        if (tf.get().getStatus() == TransportFlights.Status.WaitingForDeboarding) {
                            actions.add(UserAction.build("start-deboarding", new Check[]{
                                    destinationLocationCheck(context, world, fm.get()),
                                    parkingBrakeSetCheck(context),
                                    enginesShutdownCheck(context),
                                    aircraftStationaryCheck(context)}));
                        } else if (tf.get().getStatus() == TransportFlights.Status.Finished) {
                            actions.add(UserAction.build("finish-flight", new Check[]{}));
                        }
                    } else {
                        actions.add(UserAction.build("finish-flight", new Check[]{}));
                    }
                }
                case Finished -> {
                    // noop
                }
                default -> log.warn("DO NOT KNOW FLIGHT CHECKS FOR A FLIGHT IN {} STATUS", fmStatus);
            }

            return context.toBuilder().actions(actions).build();
        });
    }

    private Context validateOrFindCurrentFlightMission(Context context) {
        return worldAccess.read(world -> {
            Context result = context;

            if (result.getFlightMissionId() != null) {
                FlightMissions.Mission fm = world.flightMissions().byId(result.getFlightMissionId()).orElseThrow();
                if (FlightMissionHelper.isFinishedOrCancelled(fm)) {
                    result = result.resetFlightMissionId();
                    log.info("flight mission reset due to inactive status");
                } else if (fm.getUserId() != result.getUserId()) {
                    result = result.resetFlightMissionId();
                    log.info("flight mission reset due to user mismatch");
                }
            }

            if (result.getFlightMissionId() == null) {
                Optional<FlightMissions.Mission> nextActiveFlight = world.flightMissions()
                        .allByUserId(result.getUserId())
                        .sorted(FlightMissions.sortByDepartureTimeFromPastToFuture)
                        .filter(mission -> !FlightMissionHelper.isFinishedOrCancelled(mission))
                        .findFirst();
                if (nextActiveFlight.isPresent()) {
                    result = result.switchToFlightMissionId(nextActiveFlight.get().getId());
                    log.info("new active flight mission found");
                }
            }

            return result;
        });
    }

    private TrackPosition toTrackPosition(E0Posrep posrep) {
        Geo.Coords coords = Geo.coords(posrep.getLatitude(), posrep.getLongitude());
        Optional<AirportInfo> airport = posrep.isOnGround()
                ? airportInfos.stream().filter(a -> Geo.distance(a.getCoords(), coords) < 3.0).findFirst()
                : Optional.empty();

        return new TrackPosition(
                System.currentTimeMillis(), // todo ak2 read it from posrep
                posrep.isOnGround(),
                coords,
                airport.map(AirportInfo::getIcao).orElse(null),
                posrep.getHeading(),
                posrep.getAltitude());
    }

    /**
     * The status of the user, null if the user is not connected: there were no reports or the last one is too old (see CONNECTED_IDLE_MILLIS).
     */
    public UserStatus getUserStatus(int userId) {
        Context context = userContexts.get(userId);
        if (isConnected(context, System.currentTimeMillis())) {
            return UserStatus.from(context);
        } else {
            return null;
        }
    }

    static boolean isConnected(final Context context, final long nowMillis) {
        return context != null
                && context.position != null
                && nowMillis - context.position.getTime() <= (context.activeFlight ? CONNECTED_ACTIVE_FLIGHT_MILLIS : CONNECTED_IDLE_MILLIS);
    }

    public void setWorldAccess(WorldAccess worldAccess) {
        this.worldAccess = worldAccess;
        this.airportInfos = worldAccess.read(world -> world.airports().all().map(SimTracker.AirportInfo::from).collect(Collectors.toList()));
    }

    public void processContexts() {
        userContexts.keySet().forEach(this::refreshContext);
    }

    public void saveContexts() {
        if (!contextsRestored) {
            log.warn("SIM TRACKER: contexts are not restored yet, saving is skipped");
            return;
        }

        try {
            final int saved = contextStore.save(userContexts.values(), System.currentTimeMillis());
            log.debug("SIM TRACKER: {} contexts saved to {}", saved, contextStore.getFile());
        } catch (final Exception e) {
            log.error("SIM TRACKER: unable to save contexts to {}", contextStore.getFile(), e);
        }
    }

    public void restoreContexts() {
        final SimTrackerContextStore.Result result = contextStore.load(System.currentTimeMillis(), MAX_RESTORED_CONTEXT_AGE_MILLIS);
        result.restored().forEach(c -> userContexts.put(c.getUserId(), c));
        contextsRestored = true;

        log.info("SIM TRACKER: {} contexts restored, {} dropped as stale, {} broken, user ids {}",
                result.restored().size(), result.stale(), result.broken(),
                result.restored().stream().map(Context::getUserId).sorted().toList());
    }

    @AllArgsConstructor
    @Data
    @Builder(toBuilder = true)
    public static class Context {
        private final int userId;
        private final TrackPosition position;
        private final boolean parkingBrake;
        private final int numberOfEnginesRunning;
        private final List<TrackLeg> trackTrail;
        private final float measuredGs;
        private final Integer flightMissionId;
        private final List<UserAction> actions;
        // the user has a started flight (Preflight or further), see CONNECTED_ACTIVE_FLIGHT_MILLIS
        private final boolean activeFlight;

        public static Context forUser(int userId) {
            return Context.builder()
                    .userId(userId)
                    .build();
        }

        public Context resetFlightMissionId() {
            return this.toBuilder()
                    .flightMissionId(null)
                    .build();
        }

        public Context switchToFlightMissionId(int flightMissionId) {
            checkArgument(flightMissionId > 0);
            return this.toBuilder()
                    .flightMissionId(flightMissionId)
                    .build();
        }
    }

    @AllArgsConstructor
    @Data
    public static class UserStatus {
        private final String status;
        private final String flightMissionId;
        private final String locationStatus;
        private final String airportIcao;
        private final Boolean parkingBrake;
        private final Boolean engineRunning;
        private final String measuredGs;
        private final List<UserAction> actions;

        public static UserStatus none() {
            return new UserStatus(
                    "None",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        public static UserStatus from(Context context) {
            checkNotNull(context);

            String locationStatus = null;
            String airportIcao = null;
            if (context.position != null) {
                airportIcao = context.position.getAirportIcao();
                if (context.position.isOnGround()) {
                    locationStatus = airportIcao != null ? "At airport" : "On ground out of any airport";
                } else {
                    locationStatus = "In flight";
                }
            }

            return new UserStatus(
                    "Connected",
                    context.flightMissionId != null ? Id.encode(context.flightMissionId) : null,
                    locationStatus,
                    airportIcao,
                    context.position != null ? context.parkingBrake : null,
                    context.position != null ? context.numberOfEnginesRunning > 0 : null,
                    context.measuredGs < 10 ? df1.format(context.measuredGs) : String.valueOf(Math.round(context.measuredGs)),
                    context.actions);
        }
    }

    @AllArgsConstructor
    @Data
    public static class UserAction {
        private final String name;
        private final boolean allowed;
        private List<CheckResult> checks;

        public static UserAction build(String name, Check[] checks) {
            List<CheckResult> results = Arrays.stream(checks).map(CheckResult::from).toList();
            boolean result = results.stream().allMatch(CheckResult::isResult);
            return new UserAction(name, result, results);
        }
    }

    @AllArgsConstructor
    @Data
    public static class AirportInfo {
        private final String icao;
        private final Geo.Coords coords;

        public static AirportInfo from(Airports.Airport airport) {
            return new AirportInfo(airport.getIcao(), airport.getCoords());
        }
    }

    @AllArgsConstructor
    @Data
    private static class E0Posrep {
        // ts,e0,gnd,lat,lng,hdg,tas,alt,p/b,eng
        private final String time;
        private final String format = "E0";
        private final boolean onGround;
        private final float latitude;
        private final float longitude;
        private final int heading;
        private final int tas;
        private final int altitude;
        private final boolean parkingBrake;
        private final int numberOfEnginesRunning;

        public static E0Posrep parse(String posrep) {
            checkNotNull(posrep);

            String[] strs = posrep.split(",");
            checkArgument(strs.length == 10);

            String timeStr = strs[0];
            String formatStr = strs[1];
            String onGroundStr = strs[2];
            String latitudeStr = strs[3];
            String longitureStr = strs[4];
            String headingStr = strs[5];
            String tasStr = strs[6];
            String altitudeStr = strs[7];
            String parkingBrakeStr = strs[8];
            String numberOfEnginesRunningStr = strs[9];

            checkArgument("E0".equals(formatStr));

            return new E0Posrep(
                    timeStr, // todo ak2 parse it
                    Integer.parseInt(onGroundStr) == 1,
                    Float.parseFloat(latitudeStr),
                    Float.parseFloat(longitureStr),
                    Integer.parseInt(headingStr),
                    Integer.parseInt(tasStr),
                    Integer.parseInt(altitudeStr),
                    Integer.parseInt(parkingBrakeStr) == 1,
                    Integer.parseInt(numberOfEnginesRunningStr));
        }
    }

    public interface Check {
        String name();
        boolean doCheck();
    }

    @AllArgsConstructor
    @Data
    public static class CheckResult {
        private final String name;
        private final boolean result;

        public static CheckResult from(Check check) {
            return new CheckResult(check.name(), check.doCheck());
        }
    }

    private static UserAction rescheduleAction(World world, FlightMissions.Mission fm) {
        Check[] checks = world.flightMissionControl().rescheduleChecks(fm).entrySet().stream()
                .map(e -> simpleCheck(e.getKey(), e.getValue()))
                .toArray(Check[]::new);
        return UserAction.build("reschedule", checks);
    }

    private static Check simpleCheck(String name, boolean result) {
        return new Check() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean doCheck() {
                return result;
            }
        };
    }

    private static Check departureLocationCheck(Context context, World world, FlightMissions.Mission fm) {
        return new Check() {
            @Override
            public String name() {
                return "departure-location-check";
            }

            @Override
            public boolean doCheck() {
                if (context.getPosition() == null
                        || !context.getPosition().isOnGround()
                        || context.getPosition().getAirportIcao() == null) {
                    return false;
                }

                String departureIcao = world.airports().byId(fm.getDepartureAirportId()).orElseThrow().getIcao();
                String actualIcao = context.getPosition().getAirportIcao();
                return departureIcao.equals(actualIcao);
            }
        };
    }

    private static Check destinationLocationCheck(Context context, World world, FlightMissions.Mission fm) {
        return new Check() {
            @Override
            public String name() {
                return "destination-location-check";
            }

            @Override
            public boolean doCheck() {
                if (context.getPosition() == null
                        || !context.getPosition().isOnGround()
                        || context.getPosition().getAirportIcao() == null) {
                    return false;
                }

                String destinationIcao = world.airports().byId(fm.getDestinationAirportId()).orElseThrow().getIcao();
                String actualIcao = context.getPosition().getAirportIcao();
                return destinationIcao.equals(actualIcao);
            }
        };
    }

    private static Check parkingBrakeSetCheck(Context context) {
        return new Check() {
            @Override
            public String name() {
                return "parking-brake-set-check";
            }

            @Override
            public boolean doCheck() {
                return context.isParkingBrake();
            }
        };
    }

    private static Check enginesShutdownCheck(Context context) {
        return new Check() {
            @Override
            public String name() {
                return "engines-shutdown-check";
            }

            @Override
            public boolean doCheck() {
                return context.getNumberOfEnginesRunning() == 0;
            }
        };
    }

    private static Check aircraftStationaryCheck(Context context) {
        return new Check() {
            @Override
            public String name() {
                return "aircraft-stationary-check";
            }

            @Override
            public boolean doCheck() {
                return context.getMeasuredGs() == 0;
            }
        };
    }
}
