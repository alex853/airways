package net.simforge.airways2.world;

import java.nio.file.Path;

public class DiskStorageStrategy implements WorldStorageStrategy {
    @Override
    public World create() {
        throw new UnsupportedOperationException("DiskStorageStrategy.create not implemented");
    }

    @Override
    public World load() {
        throw new UnsupportedOperationException("DiskStorageStrategy.load not implemented");
    }

    @Override
    public void save(World world) {
        throw new UnsupportedOperationException("DiskStorageStrategy.save not implemented");
    }

    public Path getRootPath() {
        throw new UnsupportedOperationException("DiskStorageStrategy.getRootPath not implemented");
    }
}
