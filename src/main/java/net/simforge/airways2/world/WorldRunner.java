package net.simforge.airways2.world;

import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Misc;

import java.io.IOException;

public class WorldRunner {
    public static void main(String[] args) throws IOException {
        final World world = World.loadOrCreate(World25.name);

        while (true) {
            final boolean needToCatchTime = world.process();
            if (needToCatchTime) {
                Thread.yield();
            } else {
                Misc.sleepBM(1000);
            }
        }
    }
}
