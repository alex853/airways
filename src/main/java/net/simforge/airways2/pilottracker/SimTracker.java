package net.simforge.airways2.pilottracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import net.simforge.airways2.app.WorldAccess;
import net.simforge.airways2.pilottracker.track.TrackPosition;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SimTracker {
    private static final Logger log = LoggerFactory.getLogger(SimTracker.class);

    private WorldAccess worldAccess;
    private List<AirportInfo> airportInfos;

    private Map<Integer, Context> userContexts = new HashMap<>();

    public synchronized void processPosrep(int userId, String posrep) {
        checkArgument(userId > 0);
        checkNotNull(posrep);

        int worldTime = worldAccess.getWorldTime();

        E0Posrep parsed = E0Posrep.parse(posrep);
        TrackPosition position = toTrackPosition(parsed);
        // todo ak1 save posrep

        Context newContext = userContexts.get(userId);
        if (!userContexts.containsKey(userId)) {
            newContext = Context.forUser(userId);
            // todo ak1 restore context from previous posreps if exist
        }

        newContext = validateOrFindCurrentFlightMission(newContext);

        boolean newTakeoff = newContext.position != null && !newContext.position.isOnGround() && position.isOnGround();
        boolean newLanding = newContext.position != null && newContext.position.isOnGround() && !position.isOnGround();

        newContext = newContext.toBuilder()
                .position(position)
                .parkingBrake(parsed.isParkingBrake())
                .numberOfEnginesRunning(parsed.getNumberOfEnginesRunning())
                .build();

        doChecks(newContext);

        // todo ak0 check events and apply them to the world

        userContexts.put(userId, newContext);
    }

    private void doChecks(Context context) {
        if (context.getFlightMissionId() == null) {
            return;
        }

        worldAccess.read(world -> {
            FlightMissions.Mission fm = world.flightMissions().byId(context.getFlightMissionId()).orElseThrow();

            switch (fm.getStatus()) {
                case Dispatched -> {
                    Check[] checks = {
                            departureLocationCheck(context, world, fm),
                            parkingBrakeSetCheck(context),
                            enginesShutdownCheck(context),
                            aircraftStationaryCheck(context) };

                    boolean canStartFlight = checkAll(checks);
                    log.warn("canStartFlight {}", canStartFlight);
                    for (Check check : checks) {
                        log.warn("check {}, result {}", check.name(), check.doCheck());
                    }
                }
                case Preflight -> {
                    // Before Boarding
                    // the correct location
                    // parking brake set
                    // engines shutdown
                    // aircraft stationary

                    // During Boarding
                    // the correct location
                    // parking brake set
                    // engines shutdown
                    // aircraft stationary

                    // Before Blocks Off
                    // the correct location
                    // parking brake set
                    // engines shutdown
                    // aircraft stationary
                    // boarding completed if t/f is present
                }
                case Departure -> {
                    // the correct location
                    // aircraft is on ground
                }
                default -> log.warn("DO NOT KNOW FLIGHT CHECKS FOR A FLIGHT IN " + fm.getStatus() + " STATUS");
            }

            return null;
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
        private final Integer flightMissionId;

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
        private final Integer flightMissionId = 0;
        private final String locationStatus;
        private final String airportIcao;
        private final Boolean parkingBrake;
        private final Boolean engineRunning;

        public static UserStatus none() {
            return new UserStatus(
                    "None",
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
                    locationStatus,
                    airportIcao,
                    context.position != null ? context.parkingBrake : null,
                    context.position != null ? context.numberOfEnginesRunning > 0 : null);
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
    }

    private static boolean checkAll(Check... checks) {
        for (Check check : checks) {
            if (!check.doCheck()) {
                return false;
            }
        }
        return true;
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
                return true; // todo ak1 measured-gs
            }
        };
    }
}
