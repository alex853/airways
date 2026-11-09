package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.EventLog;
import net.simforge.airways2.world.datamodel.EventsToProcess;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * The removal of the outdated objects of the world, one processor for all of them (it replaced MiscCleanups and FlightCleanup, see docs/status-and-backlog.md, backlog 8).
 * <p>
 * It is a walk through the storages by ids with a budget, not a search. Every storage has a SWEEP (the cursor): the id of the last visited record and the time of the end of
 * the last pass. A pass is a full walk from id 1 to the last id of the storage. A run of the processor (every step of the world) takes the sweeps one after another and for every
 * sweep goes on from the cursor: it visits the records by ids (a deleted record is skipped), checks if the object is to be removed and removes it. A run of one sweep stops after
 * {@link #batch} live objects, or when the time budget of the run is over (the time is checked after every object, so a run is over the budget by one object at most),
 * the cursor is kept. When the end of the storage is reached the pass is over: it is logged (runs, milliseconds, visited, removed), the cursor goes back to zero and the next
 * pass starts an hour after the END of this one. After the start of the server the first pass of the first sweep is in 5 minutes and every next sweep starts 5 minutes after the previous one (5, 10, 15, 20 minutes), so they do not start together. The state is in memory only: after a restart
 * the pass starts again from the beginning, it is fine because the removal is idempotent.
 * <p>
 * The sweeps are: events of the log (EventLog), events to process (EventsToProcess), flight missions (with everything which hangs on a mission: transport flights, scheduled
 * flights, pilot contracts) and transport flights (the check of the orphaned ones, which have no mission). The sweeps are taken in turn, the first one is different every run,
 * so one big storage does not eat the budget of the others.
 * <p>
 * THE RULES (a sweep calls a check for one object, with the world time of the moment of the check):
 * - an event of the log is removed after 7 days, an event of a person (object1 is a Person) after 30 days: the history of the pilot (the travel of the pilot by airline, the meals)
 * is kept longer than the rest;
 * - an event to process is removed 7 days after its time if it is processed;
 * - a flight mission is removed (the age is the time after its planned departure): cancelled one - 15 minutes if it is scheduled for the quick removal (it is checked and taken
 * from the list on the way), or a day; finished - 10 days; in any of the active statuses (dispatched, preflight, departure, flying, arrival, postflight) - 36 hours;
 * - a transport flight is removed if its flight mission does not exist.
 * <p>
 * With the pass once an hour a rule of minutes (the quick removal of the cancelled flight) works with a delay of up to an hour and the time of the pass, it is accepted.
 */
public class Cleanup {
    private static final Logger log = LoggerFactory.getLogger(Cleanup.class);

    static final int DEFAULT_BATCH = 1000;
    static final long DEFAULT_BUDGET_NANOS = 5_000_000L;
    static final long PASS_INTERVAL_MILLIS = 3_600_000L;
    // after the start of the server the first sweep starts in 5 minutes and every next one 5 minutes after the previous one
    private static final long FIRST_START_DELAY_MILLIS = 5 * 60_000L;
    private static final long START_DELAY_STEP_MILLIS = 5 * 60_000L;

    // the removed objects of the current run, they are logged by lines of up to 10 ids: "t/f #10208, f/m #6432, s/f #77 removed"
    private static final int IDS_PER_LINE = 10;
    static final List<String> removed = new ArrayList<>();

    private static final int EVENT_LOG_DAYS = 7;
    private static final int EVENT_LOG_PERSON_DAYS = 30;
    private static final int EVENTS_TO_PROCESS_DAYS = 7;

    /**
     * The result of the check of one id.
     */
    enum Visit {
        /** there is no record with the id (deleted), it is not counted */
        ABSENT,
        /** a live object which stays */
        KEPT,
        /** a live object which has been removed */
        REMOVED
    }

    private final World world;
    private final LongSupplier clockMillis;
    private final long budgetNanos;
    private final int batch;
    private final List<Sweep> sweeps = new ArrayList<>();
    private int firstSweep;

    /**
     * The cleanup of the world with the default budget (5 ms of a run), the batch (1000 objects of a sweep in a run) and the start of the sweeps: the first one in 5 minutes after
     * the creation, every next one 5 minutes later.
     */
    public Cleanup(final World world) {
        this(world, System::currentTimeMillis, FIRST_START_DELAY_MILLIS, START_DELAY_STEP_MILLIS, DEFAULT_BUDGET_NANOS, DEFAULT_BATCH);
    }

    Cleanup(final World world, final LongSupplier clockMillis, final long firstStartDelayMillis, final long startDelayStepMillis, final long budgetNanos, final int batch) {
        this.world = world;
        this.clockMillis = clockMillis;
        this.budgetNanos = budgetNanos;
        this.batch = batch;

        final long createdAt = clockMillis.getAsLong();
        sweeps.add(new Sweep("event log", createdAt + firstStartDelayMillis, () -> world.eventLog().getLastRecordId(), this::visitEvent));
        sweeps.add(new Sweep("events to process", createdAt + firstStartDelayMillis + startDelayStepMillis, () -> world.eventsToProcess().getLastRecordId(), this::visitEventToProcess));
        sweeps.add(new Sweep("flight missions", createdAt + firstStartDelayMillis + 2 * startDelayStepMillis, () -> world.flightMissions().getLastRecordId(), this::visitFlightMission));
        sweeps.add(new Sweep("transport flights", createdAt + firstStartDelayMillis + 3 * startDelayStepMillis, () -> world.transportFlights().getLastRecordId(), this::visitTransportFlight));
    }

    /**
     * One run: every sweep which is due goes on from its cursor, for the time of the budget in all.
     */
    public void process() {
        final long deadline = System.nanoTime() + budgetNanos;
        try {
            for (int i = 0; i < sweeps.size(); i++) {
                // the first sweep of the turn always goes forward (by one object at least), the next ones only while the budget is not over
                if (i > 0 && System.nanoTime() >= deadline) {
                    break;
                }
                sweeps.get((firstSweep + i) % sweeps.size()).run(deadline);
            }
        } finally {
            firstSweep = (firstSweep + 1) % sweeps.size();
            logRemoved();
        }
    }

    private final class Sweep {
        private final String name;
        private final IntSupplier lastId;
        private final IntFunction<Visit> check;

        private long nextPassAtMillis;
        // the id of the last visited record, 0 is the start of a pass
        private int cursor;
        // the numbers of the current pass
        private int runs;
        private int visited;
        private int removedCount;
        private long spentNanos;

        private Sweep(final String name, final long firstPassAtMillis, final IntSupplier lastId, final IntFunction<Visit> check) {
            this.name = name;
            this.nextPassAtMillis = firstPassAtMillis;
            this.lastId = lastId;
            this.check = check;
        }

        private void run(final long deadlineNanos) {
            if (clockMillis.getAsLong() < nextPassAtMillis) {
                return;
            }

            final long startNanos = System.nanoTime();
            final int last = lastId.getAsInt();
            runs++;

            int live = 0;
            while (cursor < last) {
                cursor++;
                final Visit visit = visit(cursor);
                if (visit != Visit.ABSENT) {
                    live++;
                    visited++;
                    if (visit == Visit.REMOVED) {
                        removedCount++;
                    }
                }
                // at least one record is visited in a run, so the pass always goes forward
                if (live >= batch || System.nanoTime() >= deadlineNanos) {
                    break;
                }
            }
            spentNanos += System.nanoTime() - startNanos;

            if (cursor >= last) {
                finishPass();
            }
        }

        private Visit visit(final int id) {
            try {
                return check.apply(id);
            } catch (final RuntimeException e) {
                // one bad record must not stop the pass
                log.error("cleanup - {} - error during check of #{}", name, id, e);
                return Visit.KEPT;
            }
        }

        private void finishPass() {
            log.info("cleanup - {} - pass is over: {} runs, {} ms, {} visited, {} removed", name, runs, spentNanos / 1_000_000, visited, removedCount);
            cursor = 0;
            runs = 0;
            visited = 0;
            removedCount = 0;
            spentNanos = 0;
            nextPassAtMillis = clockMillis.getAsLong() + PASS_INTERVAL_MILLIS;
        }
    }

    // ------------------------------------------------------------------ the checks of the sweeps

    private Visit visitEvent(final int id) {
        final Optional<EventLog.Event> event = world.eventLog().byId(id);
        if (event.isEmpty()) {
            return Visit.ABSENT;
        }

        final int days = event.get().getObject1TypeRaw() == EventLog.ObjectType.Person.code() ? EVENT_LOG_PERSON_DAYS : EVENT_LOG_DAYS;
        if (event.get().getTime() > world.getWorldTime() - days * Time.ONE_DAY) {
            return Visit.KEPT;
        }

        world.eventLog().deleteById(id);
        return Visit.REMOVED;
    }

    private Visit visitEventToProcess(final int id) {
        final Optional<EventsToProcess.Event> event = world.eventsToProcess().byId(id);
        if (event.isEmpty()) {
            return Visit.ABSENT;
        }

        if (event.get().getStatus() != EventsToProcess.Status.Processed
                || event.get().getTime() > world.getWorldTime() - EVENTS_TO_PROCESS_DAYS * Time.ONE_DAY) {
            return Visit.KEPT;
        }

        world.eventsToProcess().deleteById(id);
        return Visit.REMOVED;
    }

    private Visit visitFlightMission(final int id) {
        final Optional<FlightMissions.Mission> found = world.flightMissions().byId(id);
        if (found.isEmpty()) {
            return Visit.ABSENT;
        }

        final FlightMissions.Mission mission = found.get();
        final int age = world.getWorldTime() - mission.getPlannedDepartureWorldTime();
        final boolean remove = switch (mission.getStatus()) {
            case Cancelled -> {
                final boolean quick = age >= 15 * Time.ONE_MINUTE && world.flightMissionControl().checkAndRemoveIfScheduledForQuickRemoval(mission);
                yield quick || age >= Time.ONE_DAY;
            }
            case Finished -> age >= 10 * Time.ONE_DAY;
            case Dispatched, Preflight, Departure, Flying, Arrival, Postflight -> age >= 36 * Time.ONE_HOUR;
            default -> false;
        };
        if (!remove) {
            return Visit.KEPT;
        }

        deleteFlightMission(mission);
        return Visit.REMOVED;
    }

    private Visit visitTransportFlight(final int id) {
        final Optional<TransportFlights.Flight> found = world.transportFlights().byId(id);
        if (found.isEmpty()) {
            return Visit.ABSENT;
        }

        final TransportFlights.Flight tf = found.get();
        if (world.flightMissions().byId(tf.getFlightMissionId()).isPresent()) {
            return Visit.KEPT;
        }

        log.warn("t/f #{} - f/m #{} is not found, FOUND AND REMOVED ORPHANED TRANSPORT FLIGHT", tf.getId(), tf.getFlightMissionId());
        deleteTransportFlight(tf);
        return Visit.REMOVED;
    }

    // ------------------------------------------------------------------ the deletion

    private void deleteFlightMission(final FlightMissions.Mission f) {
        deleteTransportFlights(f);
        world.pilotContracts().deleteByFlightMissionId(f.getId());
        world.flightMissions().deleteById(f.getId());
        removed.add("f/m #" + f.getId());
    }

    private void deleteTransportFlights(final FlightMissions.Mission f) {
        while (true) {
            final Optional<TransportFlights.Flight> tf = world.transportFlights().byFlightMissionId(f.getId());
            if (tf.isEmpty()) {
                break;
            }
            deleteTransportFlight(tf.get());
        }
    }

    private void deleteTransportFlight(final TransportFlights.Flight tf1) {
        world.scheduledFlights().byId(tf1.getScheduledFlightId())
                .ifPresent(sf -> {
                    world.scheduledFlights().deleteById(sf.getId());
                    removed.add("s/f #" + sf.getId());
                });

        final List<Journeys.Journey> journeys = world.journeys()
                .allByAnyTransportFlightId(tf1.getId())
                .toList();
        final boolean missionExists = world.flightMissions().byId(tf1.getFlightMissionId()).isPresent();
        for (final Journeys.Journey journey : journeys) {
            final Journeys.Status status = journey.getStatus();
            if (status == Journeys.Status.Orphaned
                    || status == Journeys.Status.Finished
                    || status == Journeys.Status.CouldNotFindTickets) {
                continue;
            }

            if (journey.getTransportFlight1Id() == tf1.getId()) {
                // the journey is reset while the transport flight and its mission still exist, they are required for that
                if (missionExists) {
                    log.warn("t/f #{} - j/y #{} in {} status is reset to looking for tickets", tf1.getId(), journey.getId(), status);
                    world.journeyControl().resetJourneyForcefully(journey);
                } else {
                    log.warn("t/f #{} - j/y #{} in {} status, mission not found, orphaned", tf1.getId(), journey.getId(), status);
                    world.journeyControl().orphaned(journey);
                }
            } else {
                log.warn("t/f #{} - j/y #{} in {} status, t/f2 reference is cleared", tf1.getId(), journey.getId(), status);
                journey.setTransportFlight2Id(0);
                journey.setPrice2(0);
            }
        }

        world.transportFlightExtras().deleteByTransportFlightId(tf1.getId());
        world.transportFlights().deleteById(tf1.getId());
        removed.add("t/f #" + tf1.getId());
    }

    static void logRemoved() {
        for (int i = 0; i < removed.size(); i += IDS_PER_LINE) {
            log.info("{} removed", String.join(", ", removed.subList(i, Math.min(i + IDS_PER_LINE, removed.size()))));
        }
        removed.clear();
    }
}
