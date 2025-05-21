package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.DiskStorageStrategy;
import net.simforge.airways2.world.World;

import java.io.IOException;

public class World25 {
    public static final String name = "World25";

    public static World load() throws IOException {
        return World.load(new DiskStorageStrategy(World25.name));
    }
}
