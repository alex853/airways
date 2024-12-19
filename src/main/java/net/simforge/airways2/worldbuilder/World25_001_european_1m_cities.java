package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.World;
import net.simforge.airways2.worldbuilder.tools.ImportCities;
import net.simforge.airways2.worldbuilder.tools.ImportMajorAirportsInVicinityOfCities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class World25_001_european_1m_cities {
    public static void main(String[] args) throws IOException {
        // drop world
        dropWorld();

        ImportCities.main(new String[]{"country-code:GB", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:FR", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:DE", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:CZ", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:AT", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:IT", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:ES", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:PT", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:IE", "min-population:1000000"});

        ImportMajorAirportsInVicinityOfCities.main(new String[0]);
    }

    private static void dropWorld() throws IOException {
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
