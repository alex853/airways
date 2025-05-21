package net.simforge.airways2.app;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Misc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class WorldRunnerBean implements DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(WorldRunnerBean.class.getName());

    private static final int saveWorldPeriod = Time.ONE_HOUR;

    private volatile Status status = Status.Startup;
    private Thread thread;
    private World world;

    @PostConstruct
    public void init() {
        loadWorld();

        thread = new Thread(() -> {
            int lastSaved = (int) (System.currentTimeMillis() / 1000);
            status = Status.Running;

            while (status == Status.Running) {
                final int now = (int) (System.currentTimeMillis() / 1000);
                final boolean needToCatchTime;
                synchronized (world) {
                    needToCatchTime = world.process(now);
                }
                if (needToCatchTime) {
                    Thread.yield();
                } else {
                    Misc.sleepBM(100);
                }

                if (lastSaved + saveWorldPeriod < now) {
                    saveWorld();
                    lastSaved = now;
                }
            }
            logger.info("world cycle stopped, status is {}", status);

            if (status == Status.HaveToStopNow) {
                saveWorld();
                status = Status.Stopped;
            }
        });
        thread.setName("world-runner-bean-thread");
        thread.start();
    }

    @Override
    public void destroy() throws Exception {
        logger.info("world thread was told to stop");

        status = Status.HaveToStopNow;
        thread.join();

        logger.info("world thread stopped");
    }

    public World world() {
        return world;
    }

    private void loadWorld() {
        try {
            world = World25.load();
            logger.info("world loaded, world time {}", LocalDateTime.ofEpochSecond(world.getWorldTime(), 0, ZoneOffset.UTC));
        } catch (IOException e) {
            logger.error("unable to load the world", e);
            throw new RuntimeException("unable to load the world", e);
        }
    }

    private void saveWorld() {
        try {
            world.save();
            logger.info("world saved, world time {}", LocalDateTime.ofEpochSecond(world.getWorldTime(), 0, ZoneOffset.UTC));
        } catch (IOException e) {
            status = Status.TerminatedDueToError;
            logger.error("unable to save the world", e);
            throw new RuntimeException("unable to save the world", e);
        }
    }

    private enum Status {
        Startup,
        Running,
        HaveToStopNow,
        Stopped,
        TerminatedDueToError
    }
}
