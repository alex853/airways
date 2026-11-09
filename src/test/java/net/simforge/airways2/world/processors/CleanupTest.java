package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.EventLog;
import net.simforge.airways2.world.datamodel.EventsToProcess;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cleanup of the world: the rules of the removal, the walk with a batch and a budget, the pass once an hour, the delay of the first pass.
 */
class CleanupTest {
    private static final long HUGE_BUDGET_NANOS = 10_000_000_000L;

    private World world;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Aircrafts.Aircraft aircraft;
    private int now;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        world.setWorldTime(world.getWorldTime() + 100 * Time.ONE_DAY);
        now = world.getWorldTime();

        airportA = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        airportB = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", airportA);
    }

    private Cleanup wholePassAtOnce() {
        return new Cleanup(world, System::currentTimeMillis, 0, 0, HUGE_BUDGET_NANOS, 1000);
    }

    private FlightMissions.Mission missionDepartedAgo(final int secondsAgo, final FlightMissions.Status status) {
        final int departure = now - secondsAgo;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, departure, departure + 3600);
        mission.setStatus(status);
        return mission;
    }

    private void logEvent(final int secondsAgo, final EventLog.EventLogId object1) {
        world.eventLog().log(now - secondsAgo, EventLog.EventType.PersonBecameIdle, object1, null, null, null);
    }

    @Test
    public void test__events_of_the_log_live_7_days_and_events_of_a_person_30_days() {
        final EventLog.EventLogId mission = new EventLog.EventLogId(EventLog.ObjectType.FlightMission, 1);
        final EventLog.EventLogId person = new EventLog.EventLogId(EventLog.ObjectType.Person, 1);
        logEvent(8 * Time.ONE_DAY, mission);   // 1: old, removed
        logEvent(6 * Time.ONE_DAY, mission);   // 2: young, stays
        logEvent(8 * Time.ONE_DAY, person);    // 3: a person, 8 days, stays
        logEvent(29 * Time.ONE_DAY, person);   // 4: a person, 29 days, stays
        logEvent(31 * Time.ONE_DAY, person);   // 5: a person, 31 days, removed

        wholePassAtOnce().process();

        assertTrue(world.eventLog().byId(1).isEmpty());
        assertTrue(world.eventLog().byId(2).isPresent());
        assertTrue(world.eventLog().byId(3).isPresent());
        assertTrue(world.eventLog().byId(4).isPresent());
        assertTrue(world.eventLog().byId(5).isEmpty());
    }

    @Test
    public void test__only_processed_events_to_process_are_removed_after_7_days() {
        world.eventsToProcess().sendEvent(EventsToProcess.Type.PilotOnDuty, 1, now - 8 * Time.ONE_DAY);      // 1: old, processed, removed
        world.eventsToProcess().sendEvent(EventsToProcess.Type.PilotOnDuty, 2, now - 8 * Time.ONE_DAY);      // 2: old, active, stays
        world.eventsToProcess().sendEvent(EventsToProcess.Type.PilotOnDuty, 3, now - 6 * Time.ONE_DAY);      // 3: young, processed, stays
        world.eventsToProcess().byId(1).orElseThrow().setProcessedStatus();
        world.eventsToProcess().byId(3).orElseThrow().setProcessedStatus();

        wholePassAtOnce().process();

        assertTrue(world.eventsToProcess().byId(1).isEmpty());
        assertTrue(world.eventsToProcess().byId(2).isPresent());
        assertTrue(world.eventsToProcess().byId(3).isPresent());
    }

    @Test
    public void test__flight_missions_are_removed_by_their_status_and_age() {
        final FlightMissions.Mission finishedOld = missionDepartedAgo(11 * Time.ONE_DAY, FlightMissions.Status.Finished);
        final FlightMissions.Mission finishedYoung = missionDepartedAgo(9 * Time.ONE_DAY, FlightMissions.Status.Finished);
        final FlightMissions.Mission cancelledOld = missionDepartedAgo(Time.ONE_DAY + Time.ONE_HOUR, FlightMissions.Status.Cancelled);
        final FlightMissions.Mission cancelledYoung = missionDepartedAgo(2 * Time.ONE_HOUR, FlightMissions.Status.Cancelled);
        final FlightMissions.Mission stuckOld = missionDepartedAgo(37 * Time.ONE_HOUR, FlightMissions.Status.Flying);
        final FlightMissions.Mission stuckYoung = missionDepartedAgo(35 * Time.ONE_HOUR, FlightMissions.Status.Flying);

        wholePassAtOnce().process();

        assertTrue(world.flightMissions().byId(finishedOld.getId()).isEmpty());
        assertTrue(world.flightMissions().byId(finishedYoung.getId()).isPresent());
        assertTrue(world.flightMissions().byId(cancelledOld.getId()).isEmpty());
        assertTrue(world.flightMissions().byId(cancelledYoung.getId()).isPresent());
        assertTrue(world.flightMissions().byId(stuckOld.getId()).isEmpty());
        assertTrue(world.flightMissions().byId(stuckYoung.getId()).isPresent());
    }

    @Test
    public void test__a_transport_flight_goes_with_its_mission_and_an_orphaned_one_is_removed() {
        final FlightMissions.Mission old = missionDepartedAgo(11 * Time.ONE_DAY, FlightMissions.Status.Finished);
        final FlightMissions.Mission young = missionDepartedAgo(Time.ONE_HOUR, FlightMissions.Status.Dispatched);
        final TransportFlights.Flight ofOld = world.transportFlights().create(old, null, CabinLayout.JY(8, 100));
        final TransportFlights.Flight ofYoung = world.transportFlights().create(young, null, CabinLayout.JY(8, 100));
        final FlightMissions.Mission gone = missionDepartedAgo(Time.ONE_HOUR, FlightMissions.Status.Dispatched);
        final TransportFlights.Flight orphaned = world.transportFlights().create(gone, null, CabinLayout.JY(8, 100));
        world.flightMissions().deleteById(gone.getId());

        wholePassAtOnce().process();

        assertTrue(world.transportFlights().byId(ofOld.getId()).isEmpty());
        assertTrue(world.transportFlights().byId(ofYoung.getId()).isPresent());
        assertTrue(world.transportFlights().byId(orphaned.getId()).isEmpty());
    }

    @Test
    public void test__the_walk_goes_on_from_the_cursor_by_batches_and_the_next_pass_starts_in_an_hour_after_the_end() {
        for (int i = 0; i < 5; i++) {
            logEvent(8 * Time.ONE_DAY, new EventLog.EventLogId(EventLog.ObjectType.FlightMission, 1));
        }
        final AtomicLong clock = new AtomicLong(1_000_000L);
        // two live objects of a sweep in a run
        final Cleanup cleanup = new Cleanup(world, clock::get, 0, 0, HUGE_BUDGET_NANOS, 2);

        cleanup.process();
        assertEquals(3, world.eventLog().all().count(), "a batch of 2 is removed in the first run");
        cleanup.process();
        assertEquals(1, world.eventLog().all().count());
        cleanup.process();
        assertEquals(0, world.eventLog().all().count());

        // the pass is over, a new old event waits for the next pass, which is not earlier than an hour from the end
        logEvent(8 * Time.ONE_DAY, new EventLog.EventLogId(EventLog.ObjectType.FlightMission, 1));
        clock.addAndGet(Cleanup.PASS_INTERVAL_MILLIS - 1);
        cleanup.process();
        assertEquals(1, world.eventLog().all().count(), "less than an hour from the end of the pass");

        clock.addAndGet(1);
        cleanup.process();
        assertEquals(0, world.eventLog().all().count(), "an hour has passed");
    }

    @Test
    public void test__the_sweeps_start_5_minutes_apart_after_the_start() {
        final AtomicLong clock = new AtomicLong(1_000_000L);
        final long minute = 60_000L;
        final Cleanup cleanup = new Cleanup(world, clock::get, 5 * minute, 5 * minute, HUGE_BUDGET_NANOS, 1000);
        logEvent(8 * Time.ONE_DAY, new EventLog.EventLogId(EventLog.ObjectType.FlightMission, 1));
        world.eventsToProcess().sendEvent(EventsToProcess.Type.PilotOnDuty, 1, now - 8 * Time.ONE_DAY);
        world.eventsToProcess().byId(1).orElseThrow().setProcessedStatus();
        final FlightMissions.Mission finished = missionDepartedAgo(11 * Time.ONE_DAY, FlightMissions.Status.Finished);

        cleanup.process();
        assertEquals(1, world.eventLog().all().count(), "nothing starts in the first 5 minutes");

        clock.addAndGet(5 * minute);
        cleanup.process();
        assertEquals(0, world.eventLog().all().count(), "the first sweep starts at 5 minutes");
        assertTrue(world.eventsToProcess().byId(1).isPresent(), "the second one is not yet");
        assertTrue(world.flightMissions().byId(finished.getId()).isPresent());

        clock.addAndGet(5 * minute);
        cleanup.process();
        assertTrue(world.eventsToProcess().byId(1).isEmpty(), "the second sweep starts at 10 minutes");
        assertTrue(world.flightMissions().byId(finished.getId()).isPresent(), "the third one is not yet");

        clock.addAndGet(5 * minute);
        cleanup.process();
        assertTrue(world.flightMissions().byId(finished.getId()).isEmpty(), "the third sweep starts at 15 minutes");
    }

    @Test
    public void test__a_run_with_no_budget_still_goes_forward_by_one_object() {
        for (int i = 0; i < 3; i++) {
            logEvent(8 * Time.ONE_DAY, new EventLog.EventLogId(EventLog.ObjectType.FlightMission, 1));
        }
        final Cleanup cleanup = new Cleanup(world, System::currentTimeMillis, 0, 0, 0, 1000);

        cleanup.process();

        // the budget is over at once, so the first sweep in the turn (the event log) visits one object only
        assertEquals(2, world.eventLog().all().count());
    }

    @Test
    public void test__the_stats_of_the_runs_are_logged_every_10_minutes() {
        final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(Cleanup.class);
        final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        final ch.qos.logback.classic.Level oldLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        try {
            final AtomicLong clock = new AtomicLong(1_000_000L);
            final long budgetNanos = 5_000_000L;
            final Cleanup cleanup = new Cleanup(world, clock::get, 1_000_000_000L, 0, budgetNanos, 1000);

            for (int i = 0; i < 3; i++) {
                clock.addAndGet(60_000L);
                cleanup.process();
            }
            assertTrue(statsLines(appender).isEmpty(), "10 minutes have not passed");

            clock.addAndGet(Cleanup.STATS_INTERVAL_MILLIS);
            cleanup.process();

            final java.util.List<String> lines = statsLines(appender);
            assertEquals(1, lines.size(), lines.toString());
            assertTrue(lines.get(0).startsWith("cleanup - stats for 13 min: 4 runs, budget 20 ms in all, spent "), lines.get(0));
            assertTrue(lines.get(0).endsWith(" runs over the budget"), lines.get(0));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(oldLevel);
        }
    }

    private static java.util.List<String> statsLines(final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender) {
        return appender.list.stream()
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith("cleanup - stats"))
                .toList();
    }
}
