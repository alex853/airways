package net.simforge.airways2.world;

public interface WorldStorageStrategy {
    World create();
    World load();
    void save(World world);
}
