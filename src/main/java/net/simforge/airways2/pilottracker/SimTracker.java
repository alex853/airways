package net.simforge.airways2.pilottracker;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.WorldAccess;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.commons.misc.Geo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SimTracker {

    private WorldAccess worldAccess;
    private List<AirportInfo> airportInfos;

    private Map<Integer, Context> userContexts = new HashMap<>();

    public synchronized void processPosrep(int userId, String posrep) {
        // todo ak0 parse posrep
        // todo ak0 save posrep
        // todo ak0 restore context from previous posreps if exist
        // todo ak0 find current flight if exists
        // todo ak0 apply posrep to context
        // todo ak0 save context to the map
    }

    public synchronized UserStatus getUserStatus(int userId) {
        Context context = userContexts.get(userId);
        if (context == null) {
            return new UserStatus("none");
        }
        return new UserStatus("exists");
    }

    public synchronized void setWorldAccess(WorldAccess worldAccess) {
        this.worldAccess = worldAccess;
        this.airportInfos = worldAccess.read(world -> world.airports().all().map(SimTracker.AirportInfo::from).collect(Collectors.toList()));
    }

    public static class Context {

    }

    @AllArgsConstructor
    @Data
    public static class UserStatus {
        private final String status;
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
}
