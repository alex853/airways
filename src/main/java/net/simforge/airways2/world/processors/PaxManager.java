package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PaxManager {
    private static final Logger log = LoggerFactory.getLogger(PaxManager.class);

    private final World world;
    private final Map<Integer, Boarding> boardings = new TreeMap<>();
    private final Map<Integer, Deboarding> deboardings = new TreeMap<>();

    public PaxManager(final World world) {
        this.world = world;
    }

    private JourneyControl journeyControl() {
        return world.journeyControl();
    }

    public void startBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForBoarding
                || transportFlight.getStatus() == TransportFlights.Status.Boarding);

        log.info("t/f #{} - boarding - STARTING", transportFlight.getId());

        tick(transportFlight);
    }

    public void continueBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        tick(transportFlight);
    }

    private void tick(final TransportFlights.Flight transportFlight) {
        Boarding boarding = boardings.get(transportFlight.getId());
        if (boarding == null) {
            boarding = restoreBoardingState(transportFlight);
            boardings.put(transportFlight.getId(), boarding);
        } else {
            refreshBoardingState(boarding, transportFlight);
        }

        final Optional<Journeys.Journey> nextToBoard = world.journeys()
                .findFirst(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.WaitingForBoarding));
        if (nextToBoard.isEmpty()) {
            log.info("t/f #{} - boarding - no j/y to board, {}", transportFlight.getId(), boarding);
            return;
        }

        boarding.tickDelta(world.getWorldTime());

        if (boarding.getCounterValue() >= nextToBoard.get().getGroupSize()) {
            journeyControl().board(nextToBoard.get());
            boarding.updateStateWhenSomeoneBoarded(nextToBoard.get().getGroupSize());
        }

        int currentExpectedPax = boarding.getConfirmedOnBoard() + (int) boarding.getCounterValue();
        if (currentExpectedPax != transportFlight.getPaxOnBoard()) {
            transportFlight.setPaxOnBoard(currentExpectedPax);
        }

        log.info("t/f #{} - boarding - PAX {}, {}", transportFlight.getId(), transportFlight.getPaxOnBoard(), boarding);
    }

    public int getEstimatedBoardingFinishTime(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        Boarding boarding = boardings.get(transportFlight.getId());
        if (boarding == null) {
            boarding = restoreBoardingState(transportFlight);
            if (boarding.getRemainingToBoard() == 0) {
                return 0;
            }
            boardings.put(transportFlight.getId(), boarding);
        }
        return boarding.getEstimatedBoardingFinishTime();
    }

    public boolean wasBoardingFinishTimePassed(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        Boarding boarding = boardings.get(transportFlight.getId());
        if (boarding == null) {
            boarding = restoreBoardingState(transportFlight);
            if (boarding.getRemainingToBoard() == 0) {
                return true;
            }
            boardings.put(transportFlight.getId(), boarding);
        }
        return boarding.getEstimatedBoardingFinishTime() <= world.getWorldTime();
    }

    public void finishBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        log.info("t/f #{} - boarding - finish", transportFlight.getId());

        // The journey whose boarding has been started but not finished (its PAX are already counted in PAX on board by the
        // boarding counter) boards now, so PAX on board does not jump down. A jump up is better than a jump down.
        final Boarding boarding = boardings.get(transportFlight.getId());
        if (boarding != null && (int) boarding.getCounterValue() > 0) {
            world.journeys()
                    .findFirst(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.WaitingForBoarding))
                    .ifPresent(journey -> {
                        journeyControl().board(journey);
                        log.info("t/f #{} - boarding - j/y #{} was being boarded (counter {}), boarded at the finish", transportFlight.getId(), journey.getId(), boarding.getCounterValue());
                    });
        }

        final Collection<Journeys.Journey> failedToBoardJourneys = world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(),
                        Journeys.Status.WaitingForCheckIn,
                        Journeys.Status.WaitingForBoarding))
                .toList();
        log.info("t/f #{} - boarding - found {} failed to board journeys", transportFlight.getId(), failedToBoardJourneys.size());

        failedToBoardJourneys.forEach(j -> journeyControl().tooLateToBoard(j));

        // the boarding counter may hold PAX of the group which has not boarded yet, they are nobody's PAX
        transportFlight.setPaxOnBoard(countPaxOnBoard(transportFlight));

        boardings.remove(transportFlight.getId());
    }

    /**
     * The boarding of the cancelled flight is not finished by finishBoarding, its state must not stay in memory.
     */
    public void dropBoardingState(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        boardings.remove(transportFlight.getId());
    }

    /**
     * The deboarding is driven by the same fractional counter as the boarding, in the opposite direction and a bit faster: the counter
     * grows with the deboarding rate and the journeys leave the flight one after another (the lowest id first) as soon as the counter
     * covers the group size. PAX on board goes down smoothly, not by a whole group at a random moment.
     */
    public void startDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);

        deboardings.put(transportFlight.getId(), new Deboarding(world.getWorldTime()));

        log.info("t/f #{} - deboarding - STARTING", transportFlight.getId());
    }

    public void continueDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);

        // after a restart of the application the state is not in the memory: the journeys which are still waiting are the state
        final Deboarding deboarding = deboardings.computeIfAbsent(transportFlight.getId(), id -> new Deboarding(world.getWorldTime()));

        final List<Journeys.Journey> waiting = world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.WaitingForDeboarding))
                .toList();
        int remaining = waiting.stream().mapToInt(Journeys.Journey::getGroupSize).sum();

        deboarding.tickDelta(world.getWorldTime(), remaining);

        for (final Journeys.Journey journey : waiting) {
            if (deboarding.counterValue < journey.getGroupSize()) {
                break;
            }
            journeyControl().deboard(journey);
            deboarding.counterValue -= journey.getGroupSize();
            remaining -= journey.getGroupSize();
        }

        // the counter holds the PAX of the group which is leaving now, they are not on board anymore
        transportFlight.setPaxOnBoard(Math.max(0, remaining - (int) deboarding.counterValue));

        log.info("t/f #{} - deboarding - PAX {}, remaining {}, counter {}", transportFlight.getId(), transportFlight.getPaxOnBoard(), remaining, deboarding.counterValue);
    }

    /**
     * The flight is finished (all the PAX have left or the deboarding window is over). The journeys which are still waiting are woken
     * up to look at the finished flight and to deboard at once.
     */
    public void finishDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);

        deboardings.remove(transportFlight.getId());

        world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.WaitingForDeboarding))
                .forEach(journey -> journey.setHeartbeatTime(world.getWorldTime()));
    }

    /**
     * The deboarding of the cancelled flight is not finished by finishDeboarding, its state must not stay in memory.
     */
    public void dropDeboardingState(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        deboardings.remove(transportFlight.getId());
    }

    // PAX per time with a random factor, the same formula for the boarding and for the deboarding
    private static double counterDelta(final int timeElapsedSeconds, final int ratePaxPerMinute) {
        return timeElapsedSeconds / (Time.ONE_MINUTE / (double) ratePaxPerMinute) * (Tools.random(85, 125) / 100.0);
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

    private Boarding restoreBoardingState(final TransportFlights.Flight transportFlight) {
        int actualOnBoard = world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(
                        transportFlight.getId(),
                        Journeys.Status.OnBoard))
                .map(Journeys.Journey::getGroupSize)
                .reduce(0, Integer::sum);

        List<Journeys.Journey> remainedToBoardJourneys = world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(),
                        Journeys.Status.WaitingForBoarding,
                        Journeys.Status.WaitingForCheckIn))
                .toList();
        int remainingToBoard = remainedToBoardJourneys.stream()
                .map(Journeys.Journey::getGroupSize)
                .reduce(0, Integer::sum);
        int readyToBoard = remainedToBoardJourneys.stream()
                .filter(j -> j.getStatus() == Journeys.Status.WaitingForBoarding)
                .map(Journeys.Journey::getGroupSize)
                .reduce(0, Integer::sum);

        transportFlight.setPaxOnBoard(actualOnBoard);
        log.info("t/f #{} - boarding - restored, remained {}", transportFlight.getId(), remainedToBoardJourneys.stream().map(j -> "j/y #" + j.getId()).collect(Collectors.toSet()));

        return new Boarding(actualOnBoard, remainingToBoard, readyToBoard, world.getWorldTime());
    }

    private void refreshBoardingState(Boarding boarding, TransportFlights.Flight transportFlight) {
        int actualOnBoard = world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(
                        transportFlight.getId(),
                        Journeys.Status.OnBoard))
                .map(Journeys.Journey::getGroupSize)
                .reduce(0, Integer::sum);

        List<Journeys.Journey> remainedToBoardJourneys = world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(),
                        Journeys.Status.WaitingForBoarding,
                        Journeys.Status.WaitingForCheckIn))
                .toList();
        int remainingToBoard = remainedToBoardJourneys.stream()
                .map(Journeys.Journey::getGroupSize)
                .reduce(0, Integer::sum);

        boarding.confirmedOnBoard = actualOnBoard;
        boarding.remainingToBoard = remainingToBoard;
        boarding.readyToBoard = remainedToBoardJourneys.stream()
                .filter(j -> j.getStatus() == Journeys.Status.WaitingForBoarding)
                .map(Journeys.Journey::getGroupSize)
                .reduce(0, Integer::sum);
        boarding.recalculateEstimatedBoardingFinishTime(world.getWorldTime());

        log.info("t/f #{} - boarding - refreshed, remained {}", transportFlight.getId(), remainedToBoardJourneys.stream().map(j -> "j/y #" + j.getId()).collect(Collectors.toSet()));
    }

    public static class Deboarding {
        public static final int RATE_PAX_PER_MINUTE = 25;
        /**
         * A journey which waits for deboarding looks at its flight this often. The deboarding itself is done by the flight, the look is
         * a safety net: the flight can be lost or finished.
         */
        public static final int JOURNEY_WATCH_INTERVAL = 5 * Time.ONE_MINUTE;

        private int counterLastTime;
        private double counterValue;

        private Deboarding(final int worldTime) {
            this.counterLastTime = worldTime;
            this.counterValue = 0.0;
        }

        private void tickDelta(final int worldTime, final int remainingToDeboard) {
            counterValue += counterDelta(worldTime - counterLastTime, RATE_PAX_PER_MINUTE);
            counterLastTime = worldTime;

            if (counterValue > remainingToDeboard) {
                counterValue = remainingToDeboard;
            }
        }
    }

    private static class Boarding {
        private static final int boardingTimeReserve = 3 * Time.ONE_MINUTE;
        private static final int ratePaxPerMinute = 15;

        private int confirmedOnBoard;
        // all journeys which are not on board yet, including the ones which have not checked-in yet, it is used for the estimation
        private int remainingToBoard;
        // the journeys which have checked-in and are able to board now, the boarding counter can not go beyond them
        private int readyToBoard;
        private int estimatedBoardingFinishTime;

        private int counterLastTime;
        private double counterValue;

        public Boarding(final int actualOnBoard, final int remainingToBoard, final int readyToBoard, final int worldTime) {
            this.confirmedOnBoard = actualOnBoard;
            this.remainingToBoard = remainingToBoard;
            this.readyToBoard = readyToBoard;
            recalculateEstimatedBoardingFinishTime(worldTime);

            this.counterValue = 0.0;
            this.counterLastTime = worldTime;
        }

        public int getConfirmedOnBoard() {
            return confirmedOnBoard;
        }

        public int getRemainingToBoard() {
            return remainingToBoard;
        }

        public double getCounterValue() {
            return counterValue;
        }

        public void tickDelta(final int worldTime) {
            final int timeElapsed = worldTime - counterLastTime;
            final double deltaAvailable = counterDelta(timeElapsed, ratePaxPerMinute);

            counterLastTime = worldTime;

            counterValue += deltaAvailable;
            if (counterValue > readyToBoard) {
                counterValue = readyToBoard;
            }
        }

        public void updateStateWhenSomeoneBoarded(int groupSize) {
            counterValue -= groupSize;
            confirmedOnBoard += groupSize;
            remainingToBoard -= groupSize;
            readyToBoard -= groupSize;
        }

        public int getEstimatedBoardingFinishTime() {
            return estimatedBoardingFinishTime;
        }

        private void recalculateEstimatedBoardingFinishTime(int worldTime) {
            this.estimatedBoardingFinishTime = worldTime
                    + (int) Math.ceil(remainingToBoard / (double) ratePaxPerMinute * Time.ONE_MINUTE)
                    + boardingTimeReserve;
        }

        @Override
        public String toString() {
            return "Boarding{" +
                    "cnfd: " + confirmedOnBoard +
                    ", rem: " + remainingToBoard +
                    ", rdy: " + readyToBoard +
                    ", eFinT: " + TimeTools.hhmmOrNull(estimatedBoardingFinishTime) +
                    ", cnt: " + counterValue +
                    ", t: " + TimeTools.hhmmOrNull(counterLastTime) +
                    '}';
        }
    }
}
