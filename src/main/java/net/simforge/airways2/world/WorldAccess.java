package net.simforge.airways2.world;

import net.simforge.airways2.app.beans.WorldRunnerBean;

public interface WorldAccess {
    boolean isReady();

    int getWorldTime();

    <T> T read(Action<T> action);

    <T> T modifySync(Action<T> action);

    interface Action<T> {
        T invoke(World world);
    }
}
