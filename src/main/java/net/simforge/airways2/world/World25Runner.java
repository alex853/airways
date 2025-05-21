package net.simforge.airways2.world;

import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Misc;

import java.io.IOException;

public class World25Runner {
    private static final int saveWorldPeriod = Time.ONE_MINUTE;

    public static void main(String[] args) throws IOException {
        final World world = World25.load();
        int lastSaved = world.getWorldTime();

        while (true) {
            final int now = (int) (System.currentTimeMillis() / 1000);
            final boolean needToCatchTime = world.process(now);
            if (needToCatchTime) {
                Thread.yield();
            } else {
                Misc.sleepBM(1000);
            }

            if (lastSaved + saveWorldPeriod < world.getWorldTime()) {
                world.save();
                lastSaved = world.getWorldTime();
            }
        }
    }
}
