package net.simforge.airways2.world.processors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The removed objects are logged by lines of up to 10 ids.
 */
class FlightCleanupLogTest {
    private List<String> logRemoved() {
        final Logger logger = (Logger) LoggerFactory.getLogger(FlightCleanup.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        final Level oldLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        try {
            FlightCleanup.logRemoved();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(oldLevel);
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    public void test__removed_ids_are_logged_by_ten_in_a_line() {
        FlightCleanup.removed.clear();
        for (int i = 0; i < 11; i++) {
            FlightCleanup.removed.add("t/f #" + (100 + i));
            FlightCleanup.removed.add("f/m #" + (200 + i));
        }
        FlightCleanup.removed.add("s/f #300");

        final List<String> lines = logRemoved();

        assertEquals(3, lines.size(), lines.toString());
        assertEquals("t/f #100, f/m #200, t/f #101, f/m #201, t/f #102, f/m #202, t/f #103, f/m #203, t/f #104, f/m #204 removed", lines.get(0));
        assertEquals("t/f #105, f/m #205, t/f #106, f/m #206, t/f #107, f/m #207, t/f #108, f/m #208, t/f #109, f/m #209 removed", lines.get(1));
        assertEquals("t/f #110, f/m #210, s/f #300 removed", lines.get(2));
        assertTrue(FlightCleanup.removed.isEmpty());
    }

    @Test
    public void test__nothing_removed_nothing_logged() {
        FlightCleanup.removed.clear();
        assertTrue(logRemoved().isEmpty());
    }
}
