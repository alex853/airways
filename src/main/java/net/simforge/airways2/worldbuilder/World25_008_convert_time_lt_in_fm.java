package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.FlightMissions;

import java.io.IOException;

public class World25_008_convert_time_lt_in_fm {
    public static void main(String[] args) throws IOException {
        final World world = World25.load();

        world.flightMissions().all().forEach(FlightMissions.Mission::convertTimeToLT);

        world.save();
    }
}
