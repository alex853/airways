package net.simforge.airways2.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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

        // save to `tmp` folder
        savingOps.perform(tmpPath);

        // move existing `world` folder to `backup` folder
        Files.createDirectories(backupPath.getParent());
        if (Files.exists(worldPath)) {
            Files.move(worldPath, backupPath);
        }

        // move freshly saved `tmp` folder to `world` folder
        Files.move(tmpPath, worldPath);
    }

    public void reduceWorldBackupCounts() throws IOException {
        final long now = System.currentTimeMillis();
        final Path backupPath = Paths.get(dataRoot, name + ".backup");
        final List<String> allBackups = new ArrayList<>();
        Files.list(backupPath).forEach(path -> allBackups.add(path.getFileName().toString()));
        allBackups.sort(String::compareTo);
        final Set<Long> processesDays = new TreeSet<>();
        allBackups.forEach(backup -> {
            final long ts = Long.parseLong(backup);
            final long daysSinceNow = (ts - now) / (24 * 60 * 60 * 1000);
            if (daysSinceNow == 0) {
                return; // do not reduce backups in last 24 hours
            }
            if (!processesDays.contains(daysSinceNow) && daysSinceNow < 10) {
                processesDays.add(daysSinceNow); // this day still has not been encountered, this backup still needs to be kept
            } else {
                // we found 2 or more backups for one of processed days, lets remove it
                final Path backupForRemoval = backupPath.resolve(backup);
                try {
                    Files.walk(backupForRemoval)
                            .sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
