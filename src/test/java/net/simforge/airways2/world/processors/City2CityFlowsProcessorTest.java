package net.simforge.airways2.world.processors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.simforge.airways2.tools.Formatting;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.Journeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flow of cities is served by the heartbeat: the earliest due flow first, and a flow which cannot be served does not block the others.
 */
class City2CityFlowsProcessorTest {
    private World world;
    private Cities.City london;
    private Cities.City berlin;
    private Cities.City paris;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final int countryId = world.countries().create("GB", "United Kingdom").getId();
        london = world.cities().create(countryId, "London", 51.5, -0.1, 9_000_000);
        berlin = world.cities().create(countryId, "Berlin", 52.5, 13.4, 4_000_000);
        paris = world.cities().create(countryId, "Paris", 48.9, 2.3, 2_000_000);
        world.cityFlows().createMissingCityFlows();
    }

    private City2CityFlows.Flow dueFlow(final Cities.City from, final Cities.City to, final int heartbeatTime) {
        final City2CityFlows.Flow flow = world.city2cityFlows().createInactive(from.getId(), to.getId());
        flow.setActive(true);
        flow.setFlowFraction(0.5f);
        flow.setNextGroupSize(2);
        flow.setAccumulatedFlowTime(heartbeatTime - Time.ONE_DAY);
        flow.setHeartbeatTime(heartbeatTime);
        return flow;
    }

    @Test
    public void test__ten_flows_are_served_in_a_step_the_earliest_heartbeats_first() {
        final int now = world.getWorldTime();
        final int countryId = london.getCountryId();
        final List<Cities.City> all = new ArrayList<>(List.of(london, berlin, paris));
        for (int i = 0; i < 3; i++) {
            all.add(world.cities().create(countryId, "City" + i, 40 + i, 10 + i, 1_000_000));
        }
        world.cityFlows().createMissingCityFlows();

        // 15 due flows; the bigger the id, the older the heartbeat
        final List<City2CityFlows.Flow> flows = new ArrayList<>();
        final List<Integer> heartbeats = new ArrayList<>();
        outer:
        for (final Cities.City from : all) {
            for (final Cities.City to : all) {
                if (from == to) {
                    continue;
                }
                final int heartbeat = now - (flows.size() + 1) * Time.ONE_HOUR;
                flows.add(dueFlow(from, to, heartbeat));
                heartbeats.add(heartbeat);
                if (flows.size() == 15) {
                    break outer;
                }
            }
        }

        City2CityFlowsProcessor.process(world);

        // the ten oldest flows were served once each and wait 9 to 11 minutes now (a served flow is not taken again in the step, even if its clock is behind);
        // the 5 newest were not reached
        for (int i = 5; i < 15; i++) {
            final int heartbeat = flows.get(i).getHeartbeatTime();
            assertTrue(heartbeat >= now + 9 * Time.ONE_MINUTE && heartbeat <= now + 11 * Time.ONE_MINUTE, "flow #" + i + " " + (heartbeat - now));
        }
        for (int i = 0; i < 5; i++) {
            assertEquals(heartbeats.get(i), flows.get(i).getHeartbeatTime(), "flow #" + i);
        }
    }

    @Test
    public void test__the_heartbeat_after_a_pass_is_never_in_the_past() {
        final int now = world.getWorldTime();

        // the next group is in the future: that moment
        assertEquals(now + 3 * Time.ONE_HOUR, City2CityFlowsProcessor.nextHeartbeat(now, now + 3 * Time.ONE_HOUR));
        // the next group is in the past (the clock is behind the world): 9 to 11 minutes from now, whatever the delay is
        for (int i = 0; i < 100; i++) {
            final int heartbeat = City2CityFlowsProcessor.nextHeartbeat(now, now - 90 * Time.ONE_DAY);
            assertTrue(heartbeat >= now + 9 * Time.ONE_MINUTE && heartbeat <= now + 11 * Time.ONE_MINUTE, String.valueOf(heartbeat - now));
        }
        assertTrue(City2CityFlowsProcessor.nextHeartbeat(now, now) >= now + 9 * Time.ONE_MINUTE);
    }

    @Test
    public void test__a_flow_of_a_city_without_its_own_flow_does_not_block_the_others() {
        final int now = world.getWorldTime();
        // a city which was added after the flows of the cities were created has no flow of its own
        final Cities.City rome = world.cities().create(london.getCountryId(), "Rome", 41.9, 12.5, 3_000_000);
        final City2CityFlows.Flow stuck = dueFlow(rome, berlin, now - 3 * Time.ONE_DAY);
        final City2CityFlows.Flow other = dueFlow(paris, berlin, now - Time.ONE_DAY);

        City2CityFlowsProcessor.process(world); // the stuck flow is the earliest, it cannot be served
        City2CityFlowsProcessor.process(world); // so the next one is served

        assertTrue(stuck.getHeartbeatTime() > now);
        assertTrue(other.getHeartbeatTime() != now - Time.ONE_DAY);
    }

    // the lines of the log of the class while the code runs
    private static List<String> logOf(final Class<?> clazz, final Runnable code) {
        final Logger logger = (Logger) LoggerFactory.getLogger(clazz);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        final Level oldLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        try {
            code.run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(oldLevel);
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    public void test__the_city_names_of_the_log_are_cut_to_5_characters() {
        assertEquals("San D", Formatting.shortCity("San Diego"));
        assertEquals("Los A", Formatting.shortCity("Los Angeles"));
        assertEquals("Paris", Formatting.shortCity("Paris"));
        assertEquals("Rome", Formatting.shortCity("Rome"));
    }

    @Test
    public void test__the_new_journey_line_is_short_and_has_the_journey_id() {
        final City2CityFlows.Flow flow = dueFlow(london, berlin, world.getWorldTime() - Time.ONE_HOUR);
        flow.setAccumulatedFlowTime(world.getWorldTime() - 2 * Time.ONE_DAY);

        final List<String> lines = logOf(City2CityFlowsProcessor.class, () -> City2CityFlowsProcessor.process(world));

        final String prefix = "#" + london.getId() + "/" + berlin.getId() + " Londo-Berli: NEW j/y #";
        final String line = lines.stream().filter(l -> l.startsWith(prefix)).findFirst().orElseThrow();
        // the journey id, the service and the group size, next group size, the times without seconds
        assertTrue(line.matches(java.util.regex.Pattern.quote(prefix) + "\\d+ [A-Z]+\\d+, nxt \\d+, a/t \\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d, h/b \\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d"), line);
    }

    @Test
    public void test__the_success_rate_of_both_directions_is_one_line() {
        final City2CityFlows.Flow forward = dueFlow(london, berlin, world.getWorldTime());
        final City2CityFlows.Flow backward = dueFlow(berlin, london, world.getWorldTime());
        forward.setSuccessRate(0.5f);
        backward.setSuccessRate(0.5f);

        final List<String> lines = logOf(City2CityFlowControl.class, () -> world.c2cFlowControl().updateSuccessRate(
                world.journeys().create(Journeys.Status.LookingForTickets, london.getId(), berlin.getId(), 2, net.simforge.airways2.tools.CabinLayout.Service.Y), 0.025f));

        assertEquals(1, lines.size(), lines.toString());
        assertEquals("s/r d0.025%: #" + london.getId() + "/" + berlin.getId() + " Londo-Berli n " + Formatting.df7.format(forward.getSuccessRate()) + ", #" + berlin.getId() + "/" + london.getId() + " Berli-Londo n " + Formatting.df7.format(backward.getSuccessRate()), lines.get(0));
    }
}
