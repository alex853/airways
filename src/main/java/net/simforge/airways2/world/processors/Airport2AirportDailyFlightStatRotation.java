package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.commons.misc.JavaTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class Airport2AirportDailyFlightStatRotation {
    private static final Logger log = LoggerFactory.getLogger(Airport2AirportDailyFlightStatRotation.class);

    private static LocalDate lastDate = JavaTime.todayUtc();

    public static void process(final World world) {
        final LocalDate now = JavaTime.todayUtc();
        if (now.equals(lastDate) || now.isBefore(lastDate)) {
            return;
        }

        lastDate = now;
        try {
            world.airport2airportDailyFlightStats().rotateCountsAtMidnight();
        } catch (final Exception e) {
            log.error("unable to rotate counts", e);
        }
    }
}
