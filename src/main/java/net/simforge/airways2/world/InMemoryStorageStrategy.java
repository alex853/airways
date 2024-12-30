package net.simforge.airways2.world;

public class InMemoryStorageStrategy implements WorldStorageStrategy {
    @Override
    public World create() {
        throw new UnsupportedOperationException("InMemoryStorageStrategy.create not implemented");
    }

    @Override
    public World load() {
        throw new UnsupportedOperationException("InMemoryStorageStrategy.load not implemented");
    }

    @Override
    public void save(World world) {
        throw new UnsupportedOperationException("InMemoryStorageStrategy.save not implemented");
    }
}
