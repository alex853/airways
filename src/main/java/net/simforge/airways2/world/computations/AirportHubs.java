package net.simforge.airways2.world.computations;

import java.util.Set;

/**
 * The hubs of the world. todo ak0 hubs are hardcoded, later they should be calculated from the flight stats OR be an additional flag of the Airport entity.
 */
public final class AirportHubs {
    private AirportHubs() {
    }

    private static final Set<String> HUB_ICAOS = Set.of("EGLL", "EGKK", "EDDF", "EDDM", "EHAM", "KJFK", "KEWR", "KLGA", "KMIA", "KATL", "KLAX", "KSFO", "LFPG", "LSZH", "LOWW", "LGAV", "OMDB", "OMAA", "RJAA", "WSSS", "YSSY", "BIKF");

    public static boolean isHub(final String icao) {
        return HUB_ICAOS.contains(icao);
    }
}
