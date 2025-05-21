package net.simforge.airways2.world;

import java.io.IOException;

public class InMemoryStorageStrategy implements WorldStorageStrategy {
    @Override
    public void load(final WorldIOOperation loadingOps) throws IOException {
        loadingOps.perform(null);
    }

    @Override
    public void save(final WorldIOOperation savingOps) {
        // noop
    }
}
