package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.TimeTools;
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

// todo ak2 the same can be done for deboarding
// todo ak2 the same can be done for check-in
public class PaxManager {
    private static final Logger log = LoggerFactory.getLogger(PaxManager.class);

    private final World world;
    private final Map<Integer, Boarding> boardings = new TreeMap<>();

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
            log.info("t/f #{} - boarding - no journey to board, exiting, state {}", transportFlight.getId(), boarding);
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

        log.info("t/f #{} - boarding - PAX on board {}, state {}", transportFlight.getId(), transportFlight.getPaxOnBoard(), boarding);
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

        final Collection<Journeys.Journey> failedToBoardJourneys = world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(),
                        Journeys.Status.WaitingForCheckIn,
                        Journeys.Status.WaitingForBoarding))
                .toList();
        log.info("t/f #{} - boarding - found {} failed to board journeys", transportFlight.getId(), failedToBoardJourneys.size());

        failedToBoardJourneys.forEach(j -> journeyControl().tooLateToBoard(j));

        boardings.remove(transportFlight.getId());
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

        transportFlight.setPaxOnBoard(actualOnBoard);
        log.info("t/f #{} - boarding - no status found, creating new, remained to board j/y ids {}", transportFlight.getId(), remainedToBoardJourneys.stream().map(Journeys.Journey::getId).collect(Collectors.toSet()));

        return new Boarding(actualOnBoard, remainingToBoard, world.getWorldTime());
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
        boarding.recalculateEstimatedBoardingFinishTime(world.getWorldTime());

        log.info("t/f #{} - boarding - status refreshed, remained to board j/y ids {}", transportFlight.getId(), remainedToBoardJourneys.stream().map(Journeys.Journey::getId).collect(Collectors.toSet()));
    }

    private static class Boarding {
        private static final int boardingTimeReserve = 3 * Time.ONE_MINUTE;
        private static final int ratePaxPerMinute = 15;

        private int confirmedOnBoard;
        private int remainingToBoard;
        private int estimatedBoardingFinishTime;

        private int counterLastTime;
        private double counterValue;

        public Boarding(final int actualOnBoard, final int remainingToBoard, final int worldTime) {
            this.confirmedOnBoard = actualOnBoard;
            this.remainingToBoard = remainingToBoard;
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
            final double deltaAvailable = timeElapsed / (Time.ONE_MINUTE / (double) ratePaxPerMinute);

            counterLastTime = worldTime;

            counterValue += deltaAvailable;
            if (counterValue > remainingToBoard) {
                counterValue = remainingToBoard;
            }
        }

        public void updateStateWhenSomeoneBoarded(int groupSize) {
            counterValue -= groupSize;
            confirmedOnBoard += groupSize;
            remainingToBoard -= groupSize;
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
                    "confirmed: " + confirmedOnBoard +
                    ", remaining: " + remainingToBoard +
                    ", estFinishTime: " + TimeTools.hhmmOrNull(estimatedBoardingFinishTime) +
                    ", counter: " + counterValue +
                    ", time: " + TimeTools.hhmmOrNull(counterLastTime) +
                    '}';
        }
    }
}
