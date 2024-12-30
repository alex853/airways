package net.simforge.airways2.world;

import java.io.IOException;
import java.nio.file.Path;

public interface WorldStorageStrategy {

    void load(WorldIOOperation loadingOps) throws IOException;

    void save(WorldIOOperation savingOps) throws IOException;

    interface WorldIOOperation {
        void perform(Path rootPath) throws IOException;
    }

}
