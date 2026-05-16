package net.simforge.airways2.pilottracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import net.simforge.airways2.app.WorldAccess;
import net.simforge.airways2.pilottracker.track.TrackPosition;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.commons.misc.Geo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SimTracker {

    private WorldAccess worldAccess;
    private List<AirportInfo> airportInfos;

    private Map<Integer, Context> userContexts = new HashMap<>();

    public synchronized void processPosrep(int userId, String posrep) {
        checkArgument(userId > 0);
        checkNotNull(posrep);

        E0Posrep parsed = E0Posrep.parse(posrep);
        TrackPosition position = toTrackPosition(parsed);
        // todo ak0 save posrep

        Context newContext = userContexts.get(userId);
        if (!userContexts.containsKey(userId)) {
            newContext = Context.forUser(userId);
            // todo ak0 restore context from previous posreps if exist
        }

        // todo ak0 check current flight and if it is not active - find current flight if exists

        newContext = newContext.processPosrep(parsed, position);
        userContexts.put(userId, newContext);
    }

    private TrackPosition toTrackPosition(E0Posrep posrep) {
        Geo.Coords coords = Geo.coords(posrep.getLatitude(), posrep.getLongitude());
        Optional<AirportInfo> airport = posrep.isOnGround()
                ? airportInfos.stream().filter(a -> Geo.distance(a.getCoords(), coords) < 3.0).findFirst()
                : Optional.empty();

        return new TrackPosition(
                System.currentTimeMillis(), // todo ak1 khm
                posrep.isOnGround(),
                coords,
                airport.map(AirportInfo::getIcao).orElse(null));
    }

    public synchronized UserStatus getUserStatus(int userId) {
        Context context = userContexts.get(userId);
        if (context == null) {
            return new UserStatus(
                    "None",
                    null,
                    null,
                    null,
                    null);
        }

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

        public static Context forUser(int userId) {
            return Context.builder()
                    .userId(userId)
                    .build();
        }

        public Context processPosrep(E0Posrep posrep, TrackPosition position) {
            // todo ak0 !!!!!!!!!!!!!!!

            return this.toBuilder()
                    .position(position)
                    .parkingBrake(posrep.isParkingBrake())
                    .numberOfEnginesRunning(posrep.getNumberOfEnginesRunning())
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
}
