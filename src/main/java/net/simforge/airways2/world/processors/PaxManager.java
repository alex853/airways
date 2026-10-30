package net.simforge.airways2.world.processors;

import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Moves PAX of a transport flight in and out of the aircraft. The boarding and the deboarding are one mechanism, {@link Operation}:
 * a fractional counter grows with the rate of the operation, the journeys are processed one after another (the lowest id first) as soon
 * as the counter covers the group size, PAX on board changes smoothly together with the counter. The operations differ only in the
 * journeys they work with, the rate, the effect on a journey and the way PAX on board is calculated.
 * <p>
 * Usage: {@code paxManager.boarding(tf).start()}, {@code paxManager.deboarding(tf).proceed()}, etc. The state of an operation is kept in
 * memory and is restored lazily from the journeys after a restart of the application.
 */
public class PaxManager {
    private static final Logger log = LoggerFactory.getLogger(PaxManager.class);

    /**
     * The first and the business class passengers board and leave the aircraft not so fast as the economy ones: the rate of the operation is cut by this
     * factor while such a journey is the one which is processed. (Two times slower: the gentlemen are not in a hurry.)
     */
    public static final double PREMIUM_RATE_FACTOR = 0.5;

    /**
     * A journey of the first or the business class (the cabin service it is booked in).
     */
    static boolean isPremium(final Journeys.Journey journey) {
        final CabinLayout.Service service = journey.getBookedCabinService();
        return service == CabinLayout.Service.F || service == CabinLayout.Service.J;
    }

    private final World world;
    private final Map<Integer, Boarding> boardings = new TreeMap<>();
    private final Map<Integer, Deboarding> deboardings = new TreeMap<>();

    public PaxManager(final World world) {
        this.world = world;
    }

    public Boarding boarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        final Boarding boarding = boardings.computeIfAbsent(transportFlight.getId(), id -> new Boarding(world, transportFlight, boardings));
        boarding.transportFlight = transportFlight;
        return boarding;
    }

    public Deboarding deboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        final Deboarding deboarding = deboardings.computeIfAbsent(transportFlight.getId(), id -> new Deboarding(world, transportFlight, deboardings));
        deboarding.transportFlight = transportFlight;
        return deboarding;
    }

    /**
     * The operations of the cancelled flight are not finished, their state must not stay in memory.
     */
    public void dropState(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        boardings.remove(transportFlight.getId());
        deboardings.remove(transportFlight.getId());
    }

    // The statuses of the journeys which the boarding and the deboarding look at
    private static final List<Journeys.Status> PASS_STATUSES = List.of(
            Journeys.Status.WaitingForCheckIn, Journeys.Status.WaitingForBoarding, Journeys.Status.OnBoard, Journeys.Status.WaitingForDeboarding);

    // The journeys of the flights by transportFlight1Id, the lowest id first; null when no pass is going
    private Map<Integer, List<Journeys.Journey>> passSnapshot;

    /**
     * Starts a pass of the processing of the transport flights: the journeys in the statuses of {@link #PASS_STATUSES} are read from the storage once and
     * grouped by their first transport flight, so the look of every flight at its journeys is a lookup, not a scan of all the journeys of the world.
     * A journey in the snapshot is a view, its status and its flight are read at the moment of the look, so what the pass itself changes (a journey
     * boards, leaves, is too late) is seen at once. What the snapshot can not see is a journey which comes into these statuses or to a flight during the
     * pass; the processing of the transport flights does not do that (the check-in, the tickets and the arrival are done by other processors).
     * Outside of a pass (an action of the user) the looks go to the storage as before.
     */
    public void beginPass() {
        try (final Timing.Timer ignored = Timing.label("PaxManager - pass snapshot # journeys scan")) {
            final Map<Integer, List<Journeys.Journey>> snapshot = new HashMap<>();
            world.journeys().filter(world.journeys().byStatusIn(PASS_STATUSES)).forEach(journey -> {
                final int transportFlightId = journey.getTransportFlight1Id();
                if (transportFlightId > 0) {
                    snapshot.computeIfAbsent(transportFlightId, id -> new ArrayList<>()).add(journey);
                }
            });
            passSnapshot = snapshot;
        }
    }

    public void endPass() {
        passSnapshot = null;
    }

    /**
     * The journeys of the flight (as their first transport flight) which are in one of the statuses, the lowest id first.
     */
    public List<Journeys.Journey> journeysOf(final TransportFlights.Flight transportFlight, final Journeys.Status... statuses) {
        checkNotNull(transportFlight);
        final int transportFlightId = transportFlight.getId();
        final Set<Journeys.Status> wanted = EnumSet.copyOf(List.of(statuses));

        if (passSnapshot == null) {
            return world.journeys()
                    .filter(world.journeys().byTransportFlight1Id(transportFlightId))
                    .filter(journey -> wanted.contains(journey.getStatus()))
                    .toList();
        }
        return passSnapshot.getOrDefault(transportFlightId, List.of()).stream()
                .filter(journey -> journey.getTransportFlight1Id() == transportFlightId && wanted.contains(journey.getStatus()))
                .toList();
    }

    /**
     * True if there are no journeys which are going to board the flight: all of them are on board or are already too late.
     * The journeys which have not checked-in yet are going to board, so the boarding goes on for them.
     */
    public boolean areAllBoarded(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        try (final Timing.Timer ignored = Timing.label("PaxManager - areAllBoarded # journeys scan")) {
            return journeysOf(transportFlight, Journeys.Status.WaitingForCheckIn, Journeys.Status.WaitingForBoarding).isEmpty();
        }
    }

    /**
     * Sum of the group sizes of the journeys which are on board of the flight.
     */
    public int countPaxOnBoard(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        try (final Timing.Timer ignored = Timing.label("PaxManager - countPaxOnBoard # journeys scan")) {
            return journeysOf(transportFlight, Journeys.Status.OnBoard).stream()
                    .map(Journeys.Journey::getGroupSize)
                    .reduce(0, Integer::sum);
        }
    }

    /**
     * The state of one operation of one flight. The counter holds PAX of the group which is being processed right now.
     */
    // todo ak3 checkin on the same rails?
    public abstract static class Operation {
        protected final World world;
        protected TransportFlights.Flight transportFlight;
        private final Map<Integer, ? extends Operation> registry;

        private int counterLastTime;
        protected double counterValue;
        private boolean refreshed;
        /**
         * The journeys which can be processed now, the lowest id first. Filled by refreshState.
         */
        protected List<Journeys.Journey> ready = List.of();

        private Operation(final World world, final TransportFlights.Flight transportFlight, final Map<Integer, ? extends Operation> registry) {
            this.world = world;
            this.transportFlight = transportFlight;
            this.registry = registry;
            this.counterLastTime = world.getWorldTime();
            this.counterValue = 0.0;
        }

        protected abstract String name();

        // the short name and the state for the progress line of the log (it is written at every tick of every flight, so it is short and goes to the debug level): "board PAX 16 [c16,r115,...]"
        protected abstract String shortName();

        protected abstract String progress();

        protected abstract int ratePaxPerMinute();

        protected abstract Set<TransportFlights.Status> startStatuses();

        protected abstract Set<TransportFlights.Status> proceedStatuses();

        /**
         * Recalculates the state from the journeys, fills {@link #ready}.
         */
        protected abstract void refreshState();

        /**
         * The counter can not go beyond this value.
         */
        protected abstract int counterLimit();

        /**
         * The journey has been processed by the counter, the operation does its effect on the journey and updates its own state.
         */
        protected abstract void onJourneyCompleted(Journeys.Journey journey);

        protected abstract int expectedPaxOnBoard();

        public void start() {
            checkArgument(startStatuses().contains(transportFlight.getStatus()));
            log.info("t/f #{} - {} START", transportFlight.getId(), shortName());
        }

        public void proceed() {
            checkArgument(proceedStatuses().contains(transportFlight.getStatus()));
            tick();
        }

        /**
         * Forgets the state of the operation.
         */
        public void drop() {
            registry.remove(transportFlight.getId());
        }

        protected final void tick() {
            refresh();

            tickDelta();

            for (final Journeys.Journey journey : ready) {
                if (counterValue < journey.getGroupSize()) {
                    break;
                }
                onJourneyCompleted(journey);
                counterValue -= journey.getGroupSize();
            }

            final int expectedPax = expectedPaxOnBoard();
            if (expectedPax != transportFlight.getPaxOnBoard()) {
                transportFlight.setPaxOnBoard(expectedPax);
            }

            // debug: the counter changes at every tick of every flight, the journeys which have boarded or got off are logged by JourneyControl
            log.debug("t/f #{} - {} PAX {} {}", transportFlight.getId(), shortName(), transportFlight.getPaxOnBoard(), progress());
        }

        protected final void refresh() {
            refreshState();
            refreshed = true;
        }

        protected final void ensureRefreshed() {
            if (!refreshed) {
                refresh();
            }
        }

        protected final void resetCounter() {
            counterValue = 0.0;
            counterLastTime = world.getWorldTime();
        }

        private void tickDelta() {
            final int timeElapsed = world.getWorldTime() - counterLastTime;
            counterLastTime = world.getWorldTime();

            // PAX per time with a random factor, the same formula for all the operations; the premium journey which is next in the queue slows it down
            final double rate = ratePaxPerMinute() * (!ready.isEmpty() && isPremium(ready.get(0)) ? PREMIUM_RATE_FACTOR : 1.0);
            counterValue += timeElapsed / (Time.ONE_MINUTE / rate) * (Tools.random(85, 125) / 100.0);
            if (counterValue > counterLimit()) {
                counterValue = counterLimit();
            }
        }
    }

    public static final class Boarding extends Operation {
        private static final int boardingTimeReserve = 3 * Time.ONE_MINUTE;
        // everybody who had a ticket is on board: the boarding stays open this long more, a journey which knocks in this time checks in and boards, the time starts again after it
        private static final int boardingIdleReserve = 3 * Time.ONE_MINUTE;
        private static final int ratePaxPerMinute = 15;

        private int confirmedOnBoard;
        // all journeys which are not on board yet, including the ones which have not checked-in yet, it is used for the estimation
        private int remainingToBoard;
        // the journeys which have checked-in and are able to board now, the boarding counter can not go beyond them
        private int readyToBoard;
        private int estimatedBoardingFinishTime;
        // since when nobody waits for the boarding, 0 if somebody does
        private int idleSince;

        // the plan of the boarding of a flight by its cabin layout (not by the sold tickets: they grow up to the departure and the plan would float after them)
        private static final int PLANNED_TIME_RESERVE = 5 * Time.ONE_MINUTE;
        // the seats of the aircraft up to which the rate of the boarding is the usual one, and from which it is 1.5 times faster (two doors), between them it grows linearly
        private static final int PLANNED_RATE_SEATS_FROM = 200;
        private static final int PLANNED_RATE_SEATS_TO = 400;
        private static final double PLANNED_RATE_FACTOR_MAX = 1.5;

        /**
         * The planned duration of the boarding of a flight by its cabin layout, in seconds: the seats divided by the rate of the boarding (the premium seats count twice, as in
         * the real boarding) and the reserve of 5 minutes. The rate is 15 PAX a minute up to 200 seats and grows to 22.5 at 400 seats (docs/boarding-planning.md).
         */
        public static int estimatePlannedDuration(final CabinLayout layout) {
            final double seatsAtTheRateOfEconomy = layout.get(CabinLayout.Service.Y) + layout.get(CabinLayout.Service.W)
                    + (layout.get(CabinLayout.Service.J) + layout.get(CabinLayout.Service.F)) / PREMIUM_RATE_FACTOR;
            final int total = layout.getTotal();
            final double share = Math.max(0, Math.min(1, (total - PLANNED_RATE_SEATS_FROM) / (double) (PLANNED_RATE_SEATS_TO - PLANNED_RATE_SEATS_FROM)));
            final double rate = ratePaxPerMinute * (1 + (PLANNED_RATE_FACTOR_MAX - 1) * share);
            return (int) Math.ceil(seatsAtTheRateOfEconomy / rate) * Time.ONE_MINUTE + PLANNED_TIME_RESERVE;
        }

        /**
         * The planned end of the boarding: 10 minutes before the planned departure. It is only a plan, nothing ends the boarding by it (the pilot of a user starts the boarding
         * when he likes, see docs/status-and-backlog.md).
         */
        public static int plannedEndTime(final FlightMissions.Mission mission) {
            return mission.getPlannedDepartureWorldTime() - TransportFlightHelper.BOARDING_ENDS_BEFORE_DEPARTURE;
        }

        /**
         * The planned start of the boarding: the planned end minus the planned duration by the cabin layout.
         */
        public static int plannedStartTime(final FlightMissions.Mission mission, final CabinLayout layout) {
            return plannedEndTime(mission) - estimatePlannedDuration(layout);
        }

        private Boarding(final World world, final TransportFlights.Flight transportFlight, final Map<Integer, Boarding> registry) {
            super(world, transportFlight, registry);
        }

        @Override
        protected String name() {
            return "boarding";
        }

        @Override
        protected String shortName() {
            return "board";
        }

        // c - confirmed on board, r - remaining to board, y - ready to board, f - the estimated finish time, k - the counter
        @Override
        protected String progress() {
            return "[c" + confirmedOnBoard
                    + ",r" + remainingToBoard
                    + ",y" + readyToBoard
                    + ",f" + TimeTools.hhmmOrNull(estimatedBoardingFinishTime)
                    + ",k" + round2(counterValue) + "]";
        }

        @Override
        protected int ratePaxPerMinute() {
            return ratePaxPerMinute;
        }

        @Override
        protected Set<TransportFlights.Status> startStatuses() {
            return EnumSet.of(TransportFlights.Status.WaitingForBoarding, TransportFlights.Status.Boarding);
        }

        @Override
        protected Set<TransportFlights.Status> proceedStatuses() {
            return EnumSet.of(TransportFlights.Status.Boarding);
        }

        @Override
        public void start() {
            super.start();
            tick();
        }

        @Override
        protected void refreshState() {
            try (final Timing.Timer ignored = Timing.label("PaxManager - Boarding.refresh # scan 1 (on board)")) {
                confirmedOnBoard = world.paxManager().journeysOf(transportFlight, Journeys.Status.OnBoard).stream()
                        .map(Journeys.Journey::getGroupSize)
                        .reduce(0, Integer::sum);
            }

            final List<Journeys.Journey> remained;
            try (final Timing.Timer ignored = Timing.label("PaxManager - Boarding.refresh # scan 2 (remained)")) {
                remained = world.paxManager().journeysOf(transportFlight, Journeys.Status.WaitingForBoarding, Journeys.Status.WaitingForCheckIn);
            }
            remainingToBoard = remained.stream().mapToInt(Journeys.Journey::getGroupSize).sum();
            ready = remained.stream().filter(j -> j.getStatus() == Journeys.Status.WaitingForBoarding).toList();
            readyToBoard = ready.stream().mapToInt(Journeys.Journey::getGroupSize).sum();

            // the premium PAX board two times slower, so for the time they count twice
            final double remainingAtTheRateOfEconomy = remained.stream()
                    .mapToDouble(j -> j.getGroupSize() / (isPremium(j) ? PREMIUM_RATE_FACTOR : 1.0))
                    .sum();
            estimatedBoardingFinishTime = world.getWorldTime()
                    + (int) Math.ceil(remainingAtTheRateOfEconomy / ratePaxPerMinute * Time.ONE_MINUTE)
                    + boardingTimeReserve;
        }

        @Override
        protected int counterLimit() {
            return readyToBoard;
        }

        @Override
        protected void onJourneyCompleted(final Journeys.Journey journey) {
            world.journeyControl().board(journey);
            confirmedOnBoard += journey.getGroupSize();
            remainingToBoard -= journey.getGroupSize();
            readyToBoard -= journey.getGroupSize();
        }

        @Override
        protected int expectedPaxOnBoard() {
            return confirmedOnBoard + (int) counterValue;
        }

        public int getEstimatedFinishTime() {
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);
            ensureRefreshed();
            if (remainingToBoard == 0) {
                return 0;
            }
            return estimatedBoardingFinishTime;
        }

        public boolean wasFinishTimePassed() {
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);
            ensureRefreshed();
            if (remainingToBoard == 0) {
                // nobody waits for the boarding: a few minutes more for the journeys which knock late, a flight which is sold out and boarded is over at once
                if (transportFlight.getRemainedTickets().getTotal() == 0) {
                    return true;
                }
                if (idleSince == 0) {
                    idleSince = world.getWorldTime();
                }
                return world.getWorldTime() - idleSince >= boardingIdleReserve;
            }
            idleSince = 0;
            return estimatedBoardingFinishTime <= world.getWorldTime();
        }

        public void finish() {
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

            log.info("t/f #{} - board finish", transportFlight.getId());

            // The journey whose boarding has been started but not finished (its PAX are already counted in PAX on board by the
            // boarding counter) boards now, so PAX on board does not jump down. A jump up is better than a jump down.
            if ((int) counterValue > 0) {
                try (final Timing.Timer ignored = Timing.label("PaxManager - Boarding.finish # scan 1 (last group)")) {
                    world.paxManager().journeysOf(transportFlight, Journeys.Status.WaitingForBoarding).stream()
                            .findFirst()
                            .ifPresent(journey -> {
                                world.journeyControl().board(journey);
                                log.info("t/f #{} - board j/y #{} boarded at finish", transportFlight.getId(), journey.getId());
                            });
                }
            }

            final Collection<Journeys.Journey> failedToBoardJourneys;
            try (final Timing.Timer ignored = Timing.label("PaxManager - Boarding.finish # scan 2 (failed)")) {
                failedToBoardJourneys = world.paxManager().journeysOf(transportFlight, Journeys.Status.WaitingForCheckIn, Journeys.Status.WaitingForBoarding);
            }
            log.info("t/f #{} - board found {} failed to board journeys", transportFlight.getId(), failedToBoardJourneys.size());

            failedToBoardJourneys.forEach(j -> world.journeyControl().tooLateToBoard(j));

            // the boarding counter may hold PAX of the group which has not boarded yet, they are nobody's PAX
            transportFlight.setPaxOnBoard(world.paxManager().countPaxOnBoard(transportFlight));
            // the boarding is over: the income of the flight is what really boarded (the flights of the pilots only)
            world.transportFlightControl().recalculateIncome(transportFlight);

            drop();
        }

        @Override
        public String toString() {
            return "Boarding{" +
                    "cnfd: " + confirmedOnBoard +
                    ", rem: " + remainingToBoard +
                    ", rdy: " + readyToBoard +
                    ", eFinT: " + TimeTools.hhmmOrNull(estimatedBoardingFinishTime) +
                    ", cnt: " + counterValue +
                    '}';
        }
    }

    /**
     * The deboarding goes in the opposite direction and a bit faster than the boarding: the journeys leave the flight one after another
     * as soon as the counter covers the group size. PAX on board goes down smoothly, not by a whole group at a random moment.
     */
    public static final class Deboarding extends Operation {
        public static final int RATE_PAX_PER_MINUTE = 25;
        /**
         * A journey which waits for deboarding looks at its flight this often. The deboarding itself is done by the flight, the look is
         * a safety net: the flight can be lost or finished.
         */
        public static final int JOURNEY_WATCH_INTERVAL = 5 * Time.ONE_MINUTE;

        // PAX who are still in the aircraft, the journeys which are still waiting are the state
        private int remainingToDeboard;

        private Deboarding(final World world, final TransportFlights.Flight transportFlight, final Map<Integer, Deboarding> registry) {
            super(world, transportFlight, registry);
        }

        @Override
        protected String name() {
            return "deboarding";
        }

        @Override
        protected String shortName() {
            return "deboard";
        }

        // r - remaining to deboard, k - the counter
        @Override
        protected String progress() {
            return "[r" + remainingToDeboard + ",k" + round2(counterValue) + "]";
        }

        @Override
        protected int ratePaxPerMinute() {
            return RATE_PAX_PER_MINUTE;
        }

        @Override
        protected Set<TransportFlights.Status> startStatuses() {
            return EnumSet.of(TransportFlights.Status.Deboarding);
        }

        @Override
        protected Set<TransportFlights.Status> proceedStatuses() {
            return EnumSet.of(TransportFlights.Status.Deboarding);
        }

        @Override
        public void start() {
            super.start();
            resetCounter();
        }

        @Override
        protected void refreshState() {
            try (final Timing.Timer ignored = Timing.label("PaxManager - Deboarding.refresh # journeys scan")) {
                ready = world.paxManager().journeysOf(transportFlight, Journeys.Status.WaitingForDeboarding);
            }
            remainingToDeboard = ready.stream().mapToInt(Journeys.Journey::getGroupSize).sum();
        }

        @Override
        protected int counterLimit() {
            return remainingToDeboard;
        }

        @Override
        protected void onJourneyCompleted(final Journeys.Journey journey) {
            world.journeyControl().deboard(journey);
            remainingToDeboard -= journey.getGroupSize();
        }

        @Override
        protected int expectedPaxOnBoard() {
            // the counter holds the PAX of the group which is leaving now, they are not on board anymore
            return Math.max(0, remainingToDeboard - (int) counterValue);
        }

        /**
         * When the deboarding is going to end: the PAX who are still in the aircraft at the rate of the deboarding, 0 if nobody is left. It is an estimate, the real rate of the
         * counter is random around the nominal one.
         */
        public int getEstimatedFinishTime() {
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);
            ensureRefreshed();
            final double left = Math.max(0, remainingToDeboard - counterValue);
            if (left == 0) {
                return 0;
            }
            return world.getWorldTime() + (int) Math.ceil(left / RATE_PAX_PER_MINUTE * Time.ONE_MINUTE);
        }

        /**
         * The flight is finished (all the PAX have left or the deboarding window is over). The journeys which are still waiting are woken
         * up to look at the finished flight and to deboard at once.
         */
        public void finish() {
            try (final Timing.Timer ignored = Timing.label("PaxManager - Deboarding.finish # journeys scan")) {
                world.paxManager().journeysOf(transportFlight, Journeys.Status.WaitingForDeboarding)
                        .forEach(journey -> journey.setHeartbeatTime(world.getWorldTime()));
            }

            drop();
        }

        @Override
        public String toString() {
            return "Deboarding{rem: " + remainingToDeboard + ", cnt: " + counterValue + '}';
        }
    }

    // The counters are doubles with the noise of the arithmetic (2.674999999999999), the log has two decimals.
    static double round2(final double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
