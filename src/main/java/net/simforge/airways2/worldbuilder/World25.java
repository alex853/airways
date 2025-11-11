package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.DiskStorageStrategy;
import net.simforge.airways2.world.World;

import java.io.IOException;

public class World25 {
    public static final String name = "World25";
    public static final String ShadowJetIata = "SJ";
    public static final String ShadowJetIcao = "SJT";
    public static final String BusyBirdsIata = "BB";
    public static final String BusyBirdsIcao = "BBD";

    public static final DiskStorageStrategy diskStorageStrategy = new DiskStorageStrategy(World25.name);

    public static World load() throws IOException {
        return World.load(diskStorageStrategy);
    }
}
