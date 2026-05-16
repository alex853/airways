package net.simforge.airways2.app;

import net.simforge.airways2.world.World;

public interface WorldAccess {
    boolean isReady();

    <T> T read(WorldRunnerBean.Action<T> action);

    <T> T modifySync(WorldRunnerBean.Action<T> action);

    interface Action<T> {
        T invoke(World world);
    }
}
