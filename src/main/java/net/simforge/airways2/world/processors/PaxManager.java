package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.EnumSet;
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

    /**
     * True if there are no journeys which are going to board the flight: all of them are on board or are already too late.
     * The journeys which have not checked-in yet are going to board, so the boarding goes on for them.
     */
    public boolean areAllBoarded(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        return world.journeys()
                .findFirst(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(),
                        Journeys.Status.WaitingForCheckIn,
                        Journeys.Status.WaitingForBoarding))
                .isEmpty();
    }

    /**
     * Sum of the group sizes of the journeys which are on board of the flight.
     */
    public int countPaxOnBoard(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        return world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.OnBoard))
                .map(Journeys.Journey::getGroupSize)
                .reduce(0, Integer::sum);
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
            log.info("t/f #{} - {} - STARTING", transportFlight.getId(), name());
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

            log.info("t/f #{} - {} - PAX {}, {}", transportFlight.getId(), name(), transportFlight.getPaxOnBoard(), this);
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

            // PAX per time with a random factor, the same formula for all the operations
            counterValue += timeElapsed / (Time.ONE_MINUTE / (double) ratePaxPerMinute()) * (Tools.random(85, 125) / 100.0);
            if (counterValue > counterLimit()) {
                counterValue = counterLimit();
            }
        }
    }

    public static final class Boarding extends Operation {
        private static final int boardingTimeReserve = 3 * Time.ONE_MINUTE;
        private static final int ratePaxPerMinute = 15;

        private int confirmedOnBoard;
        // all journeys which are not on board yet, including the ones which have not checked-in yet, it is used for the estimation
        private int remainingToBoard;
        // the journeys which have checked-in and are able to board now, the boarding counter can not go beyond them
        private int readyToBoard;
        private int estimatedBoardingFinishTime;

        private Boarding(final World world, final TransportFlights.Flight transportFlight, final Map<Integer, Boarding> registry) {
            super(world, transportFlight, registry);
        }

        @Override
        protected String name() {
            return "boarding";
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
            confirmedOnBoard = world.journeys()
                    .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.OnBoard))
                    .map(Journeys.Journey::getGroupSize)
                    .reduce(0, Integer::sum);

            final List<Journeys.Journey> remained = world.journeys()
                    .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(),
                            Journeys.Status.WaitingForBoarding,
                            Journeys.Status.WaitingForCheckIn))
                    .toList();
            remainingToBoard = remained.stream().mapToInt(Journeys.Journey::getGroupSize).sum();
            ready = remained.stream().filter(j -> j.getStatus() == Journeys.Status.WaitingForBoarding).toList();
            readyToBoard = ready.stream().mapToInt(Journeys.Journey::getGroupSize).sum();

            estimatedBoardingFinishTime = world.getWorldTime()
                    + (int) Math.ceil(remainingToBoard / (double) ratePaxPerMinute * Time.ONE_MINUTE)
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
                return true;
            }
            return estimatedBoardingFinishTime <= world.getWorldTime();
        }

        public void finish() {
            checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

            log.info("t/f #{} - boarding - finish", transportFlight.getId());

            // The journey whose boarding has been started but not finished (its PAX are already counted in PAX on board by the
            // boarding counter) boards now, so PAX on board does not jump down. A jump up is better than a jump down.
            if ((int) counterValue > 0) {
                world.journeys()
                        .findFirst(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.WaitingForBoarding))
                        .ifPresent(journey -> {
                            world.journeyControl().board(journey);
                            log.info("t/f #{} - boarding - j/y #{} was being boarded (counter {}), boarded at the finish", transportFlight.getId(), journey.getId(), counterValue);
                        });
            }

            final Collection<Journeys.Journey> failedToBoardJourneys = world.journeys()
                    .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(),
                            Journeys.Status.WaitingForCheckIn,
                            Journeys.Status.WaitingForBoarding))
                    .toList();
            log.info("t/f #{} - boarding - found {} failed to board journeys", transportFlight.getId(), failedToBoardJourneys.size());

            failedToBoardJourneys.forEach(j -> world.journeyControl().tooLateToBoard(j));

            // the boarding counter may hold PAX of the group which has not boarded yet, they are nobody's PAX
            transportFlight.setPaxOnBoard(world.paxManager().countPaxOnBoard(transportFlight));

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
            ready = world.journeys()
                    .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.WaitingForDeboarding))
                    .toList();
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
         * The flight is finished (all the PAX have left or the deboarding window is over). The journeys which are still waiting are woken
         * up to look at the finished flight and to deboard at once.
         */
        public void finish() {
            world.journeys()
                    .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.WaitingForDeboarding))
                    .forEach(journey -> journey.setHeartbeatTime(world.getWorldTime()));

            drop();
        }

        @Override
        public String toString() {
            return "Deboarding{rem: " + remainingToDeboard + ", cnt: " + counterValue + '}';
        }
    }
}
