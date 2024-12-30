package net.simforge.airways2.world;

import java.nio.file.Path;

public class DiskStorageStrategy implements WorldStorageStrategy {
    private final String name;

    public DiskStorageStrategy(final String name) {
        this.name = name;
    }

    public Path getRootPath() {
        throw new UnsupportedOperationException("DiskStorageStrategy.getRootPath not implemented");
    }

    @Override
    public void load(final WorldIOOperation loadingOps) {
        throw new UnsupportedOperationException("DiskStorageStrategy.load not implemented");
    }

    // todo ak0 safe saving via save to tmp and then renaming
    @Override
    public void save(final WorldIOOperation savingOps) {
        throw new UnsupportedOperationException("DiskStorageStrategy.save not implemented");
    }
}
