package net.simforge.airways2.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DiskStorageStrategy implements WorldStorageStrategy {
    private static final String dataRoot = "./world-data/";

    private final String name;

    public DiskStorageStrategy(final String name) {
        this.name = name;
    }

    public Path getWorldPath() {
        return Paths.get(dataRoot, name);
    }

    @Override
    public void load(final WorldIOOperation loadingOps) throws IOException {
        loadingOps.perform(getWorldPath());
    }

    @Override
    public void save(final WorldIOOperation savingOps) throws IOException {
        final Path worldPath = getWorldPath();
        final Path tmpPath = Paths.get(dataRoot, name + ".tmp", String.valueOf(System.currentTimeMillis()));
        final Path backupPath = Paths.get(dataRoot, name + ".backup", String.valueOf(System.currentTimeMillis()));

        savingOps.perform(tmpPath);

        Files.createDirectories(backupPath.getParent());
        if (Files.exists(worldPath)) {
            Files.move(worldPath, backupPath);
        }
        Files.move(tmpPath, worldPath);
    }
}
