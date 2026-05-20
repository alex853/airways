package net.simforge.airways2.pilottracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import net.simforge.airways2.app.WorldAccess;
import net.simforge.airways2.pilottracker.track.TrackLeg;
import net.simforge.airways2.pilottracker.track.TrackPosition;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SimTracker {
    private static final Logger log = LoggerFactory.getLogger(SimTracker.class);
    private static final DecimalFormat df1 = new DecimalFormat("#.#");

    private WorldAccess worldAccess;
    private List<AirportInfo> airportInfos;

    private Map<Integer, Context> userContexts = new HashMap<>();

    public synchronized void processPosrep(int userId, String posrep) {
        checkArgument(userId > 0);
        checkNotNull(posrep);

        E0Posrep parsed = E0Posrep.parse(posrep);
        TrackPosition position = toTrackPosition(parsed);
        // todo ak1 save posrep

        Context oldContext = userContexts.get(userId);
        if (!userContexts.containsKey(userId)) {
            oldContext = Context.forUser(userId);
            // todo ak1 restore context from previous posreps if exist
        }

        Context newContext = validateOrFindCurrentFlightMission(oldContext);

        List<TrackLeg> newTrackTrail = TrackLeg.buildNewTrackTrail(oldContext.trackTrail, oldContext.position, position);

        newContext = newContext.toBuilder()
                .position(position)
                .parkingBrake(parsed.isParkingBrake())
                .numberOfEnginesRunning(parsed.getNumberOfEnginesRunning())
                .trackTrail(newTrackTrail)
                .measuredGs(TrackLeg.calculateGroundspeed(newTrackTrail))
                .build();

        processEvents(oldContext, newContext);

        newContext = doChecks(newContext);

        userContexts.put(userId, newContext);
    }

    private void processEvents(Context oldContext, Context newContext) {
        if (newContext.getFlightMissionId() == null) {
            return;
        }

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
    }

    public synchronized void refreshContext(int userId) {
        checkArgument(userId > 0);

        Context oldContext = userContexts.get(userId);
        if (!userContexts.containsKey(userId)) {
            return;
        }

        Context newContext = validateOrFindCurrentFlightMission(oldContext);
        newContext = doChecks(newContext);

        userContexts.put(userId, newContext);
    }

    private Context doChecks(Context context) {
        if (context.getFlightMissionId() == null) {
            return context;
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
                }
                case Preflight -> {
                    if (tf.isPresent()) {
                        if (tf.get().getStatus() == TransportFlights.Status.WaitingForBoarding) {
                            actions.add(UserAction.build("start-boarding", new Check[]{
                                    departureLocationCheck(context, world, fm.get()),
                                    parkingBrakeSetCheck(context),
                                    enginesShutdownCheck(context),
                                    aircraftStationaryCheck(context)}));
                        } else if (tf.get().getStatus() == TransportFlights.Status.WaitingForDeparture) {
                            actions.add(UserAction.build("blocks-off", new Check[]{}));
                        }
                    } else {
                        actions.add(UserAction.build("blocks-off", new Check[]{}));
                    }
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
                    // start deboarding
                    // finish
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
                System.currentTimeMillis(), // todo ak1 read it from posrep
                posrep.isOnGround(),
                coords,
                airport.map(AirportInfo::getIcao).orElse(null));
    }

    public synchronized UserStatus getUserStatus(int userId) {
        Context context = userContexts.get(userId);
        if (context == null) {
            return UserStatus.none();
        } else {
            return UserStatus.from(context);
        }
    }

    public synchronized void setWorldAccess(WorldAccess worldAccess) {
        this.worldAccess = worldAccess;
        this.airportInfos = worldAccess.read(world -> world.airports().all().map(SimTracker.AirportInfo::from).collect(Collectors.toList()));
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
        private final Integer flightMissionId;
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

            long lastSeen = 0;
            String locationStatus = null;
            String airportIcao = null;
            if (context.position != null) {
                lastSeen = context.position.getTime();
                airportIcao = context.position.getAirportIcao();
                if (context.position.isOnGround()) {
                    locationStatus = airportIcao != null ? "At airport" : "On ground out of any airport";
                } else {
                    locationStatus = "In flight";
                }
            }

            return new UserStatus(
                    "Connected",
                    context.flightMissionId,
                    locationStatus,
                    airportIcao,
                    context.position != null ? context.parkingBrake : null,
                    context.position != null ? context.numberOfEnginesRunning > 0 : null,
                    df1.format(context.measuredGs),
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
                    timeStr, // todo ak1 parse it
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

    private interface Check {
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
