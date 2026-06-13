package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class City2CityFlowHistogramAnalyser {
    private static final Logger log = LoggerFactory.getLogger(City2CityFlowHistogramAnalyser.class);

    private static long lastProcessTime = 0;

    public static void process(final World world) {
        if (lastProcessTime + 3600000 > System.currentTimeMillis()) {
            return;
        }

        lastProcessTime = System.currentTimeMillis();
        int worldTime = world.getWorldTime();

        int[] counts = new int[100];

        world.city2cityFlows().all()
                .filter(City2CityFlows.Flow::isActive)
                .forEach(c2c -> {
                    int heartbeatTime = c2c.getHeartbeatTime();
                    if (heartbeatTime == 0) {
                        return;
                    }

                    if (heartbeatTime < worldTime) {
                        return;
                    }

                    int day = (heartbeatTime - worldTime) / Time.ONE_DAY;
                    if (day >= counts.length) {
                        day = counts.length - 1;
                    }

                    counts[day]++;
                });

        for (int row = 0; row < counts.length / 10; row++) {
            log.warn("c2c heartbeat histogram - row {} - data: {}, {}, {}, {}, {}, {}, {}, {}, {}, {}", row, counts[row * 10], counts[row * 10 + 1], counts[row * 10 + 2], counts[row * 10 + 3], counts[row * 10 + 4], counts[row * 10 + 5], counts[row * 10 + 6], counts[row * 10 + 7], counts[row * 10 + 8], counts[row * 10 + 9]);
        }
    }
}
