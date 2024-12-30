package net.simforge.airways2.world;

import java.io.IOException;

public class InMemoryStorageStrategy implements WorldStorageStrategy {
    @Override
    public void load(WorldIOOperation loadingOps) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void save(WorldIOOperation savingOps) throws IOException {
        throw new UnsupportedOperationException();
    }
}
