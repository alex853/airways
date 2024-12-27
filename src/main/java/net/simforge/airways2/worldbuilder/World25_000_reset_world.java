package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class World25_000_reset_world {
    public static void main(String[] args) throws IOException {
        removeWorld();
        createWorld();
    }

    private static void createWorld() throws IOException {
        final World world = World.loadOrCreate(World25.name);
        world.setWorldTime(Time.now());
        world.save();
    }

    public static void removeWorld() throws IOException {
        final World world = World.loadOrCreate(World25.name);
        final Path rootPath = world.getRootPath();
        try (final Stream<Path> paths = Files.walk(rootPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
