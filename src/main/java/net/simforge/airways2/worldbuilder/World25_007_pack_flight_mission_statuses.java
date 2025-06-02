package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.FlightMissions;

import java.io.IOException;

public class World25_007_pack_flight_mission_statuses {
    public static void main(String[] args) throws IOException {
        final World world = World25.load();

        world.flightMissions().all().forEach(f -> {
            final int oldCode = f.getStatusCode();
            final int newCode = switch (oldCode) {
                case 1 -> 1;
                case 2 -> 2;
                case 20 -> 4;
                case 30 -> 5;
                case 40 -> 6;
                case 50 -> 7;
                case 60 -> 8;
                case 70 -> 9;
                case 100 -> 10;
                case 99 -> 13;
                default -> throw new IllegalStateException(String.valueOf(oldCode));
            };
            f.setStatus(FlightMissions.Status.byCode(newCode));
        });

        world.save();
    }
}
