package net.simforge.airways2.world.processors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Journeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hourly line of the log with the passengers and the journeys by the statuses.
 */
class JourneyStatsTest {
    private World world;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);

        logger = (Logger) LoggerFactory.getLogger(JourneyStats.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void afterEach() {
        logger.detachAppender(appender);
    }

    private void journey(final Journeys.Status status, final int groupSize) {
        world.journeys().create(status, 1, 2, groupSize, CabinLayout.Service.Y);
    }

    private List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void theLineHasThePassengersAndTheJourneysByEveryStatus() {
        journey(Journeys.Status.LookingForTickets, 4);
        journey(Journeys.Status.LookingForTickets, 3);
        journey(Journeys.Status.OnBoard, 2);

        JourneyStats.logStats(world);

        assertEquals(1, lines().size());
        final String line = lines().get(0);
        assertTrue(line.startsWith("j/y - stats by statuses: passengers total 9: "), line);
        assertTrue(line.contains("LookingForTickets=7"), line);
        assertTrue(line.contains("OnBoard=2"), line);
        // a status without journeys is in the line with zero
        assertTrue(line.contains("Finished=0"), line);
        assertTrue(line.contains("journeys total 3: "), line);
        assertTrue(line.contains("LookingForTickets=2"), line);
    }

    @Test
    void aWorldWithoutJourneysHasAllZeros() {
        JourneyStats.logStats(world);

        final String line = lines().get(0);
        assertTrue(line.contains("passengers total 0: "), line);
        assertTrue(line.contains("journeys total 0: "), line);
    }
}
