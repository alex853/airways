package net.simforge.airways2.world.processors;

import net.simforge.airways2.app.WebTime;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForBoarding);

        log.info("t/f #{} - boarding - start", transportFlight.getId());

        tick(transportFlight);
    }

    public void continueBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        log.info("t/f #{} - boarding - continue", transportFlight.getId());

        tick(transportFlight);
    }

    private void tick(final TransportFlights.Flight transportFlight) {
        Boarding boarding = boardings.get(transportFlight.getId());
        if (boarding == null) {
            boarding = restoreBoardingState(transportFlight);
            boardings.put(transportFlight.getId(), boarding);
        }

        log.info("t/f #{} - boarding - data before tick {}", transportFlight.getId(), boarding);

        if (boarding.hasCurrToBoard()) {
            if (boarding.tickCurrToBoard(world.getWorldTime())) {
                log.info("t/f #{} - boarding - curr journey to board completed", transportFlight.getId());
                journeyControl().board(world.journeys().byId(boarding.getCurrToBoardId()).orElseThrow());
                boarding.finishCurrToBoard();
            }

            log.info("t/f #{} - boarding - set pax on board {}", transportFlight.getId(), boarding.getOnBoardIncludingCurr());
            transportFlight.setPaxOnBoard(boarding.getOnBoardIncludingCurr());
        }

        if (!boarding.hasCurrToBoard()) {
            final Optional<Journeys.Journey> nextToBoard = world.journeys()
                    .findFirst(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.WaitingForBoarding));

            if (nextToBoard.isPresent()) {
                log.info("t/f #{} - boarding - next journey to board, g/s {}", transportFlight.getId(), nextToBoard.get().getGroupSize());
                boarding.startCurrToBoard(nextToBoard.get().getId(), nextToBoard.get().getGroupSize(), world.getWorldTime());
            } else {
                log.info("t/f #{} - boarding - no next journey to board found", transportFlight.getId());
            }
        }

        log.info("t/f #{} - boarding - data after tick {}", transportFlight.getId(), boarding);
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
        final int actualOnBoard = world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.OnBoard))
                .map(Journeys.Journey::getGroupSize)
                .reduce(0, Integer::sum);

        final int remainingToBoard = transportFlight.getPaxCheckedIn() - actualOnBoard;

        transportFlight.setPaxOnBoard(actualOnBoard);
        log.info("t/f #{} - boarding - no data found, creating new, actual on board {}, remaining to board {}", transportFlight.getId(), actualOnBoard, remainingToBoard);

        return new Boarding(actualOnBoard, remainingToBoard, world.getWorldTime());
    }

    private static class Boarding {
        private static final int boardingTimeReserve = 3 * Time.ONE_MINUTE;
        private static final int ratePaxPerMinute = 15;

        private int confirmedOnBoard;
        private int remainingToBoard;
        private int estimatedBoardingFinishTime;

        private int currToBoardLastTime;
        private int currToBoardId;
        private int currToBoardTotal;
        private double currToBoardBoarded;

        public Boarding(final int actualOnBoard, final int remainingToBoard, final int worldTime) {
            this.confirmedOnBoard = actualOnBoard;
            this.remainingToBoard = remainingToBoard;
            this.estimatedBoardingFinishTime = worldTime
                    + (int) Math.ceil(remainingToBoard / (double) ratePaxPerMinute * Time.ONE_MINUTE)
                    + boardingTimeReserve;
        }

        public int getRemainingToBoard() {
            return remainingToBoard;
        }
        
        public boolean hasCurrToBoard() {
            return currToBoardId != 0;
        }

        public int getCurrToBoardId() {
            return currToBoardId;
        }

        public void startCurrToBoard(final int nextToBoardId, final int nextToBoardPax, final int worldTime) {
            checkArgument(currToBoardId == 0);

            currToBoardLastTime = worldTime;
            currToBoardId = nextToBoardId;
            currToBoardTotal = nextToBoardPax;
            currToBoardBoarded = 0;

            final int currEstimatedBoardingFinishTime = worldTime
                    + (int) Math.ceil(nextToBoardPax / (double) ratePaxPerMinute * Time.ONE_MINUTE)
                    + boardingTimeReserve;

            if (currEstimatedBoardingFinishTime >= estimatedBoardingFinishTime) {
                estimatedBoardingFinishTime = currEstimatedBoardingFinishTime;
            }
        }

        public boolean tickCurrToBoard(final int worldTime) {
            final int timeElapsed = worldTime - currToBoardLastTime;
            final double deltaAvailable = timeElapsed / (Time.ONE_MINUTE / (double) ratePaxPerMinute);

            currToBoardBoarded = Math.min(currToBoardBoarded + deltaAvailable, currToBoardTotal);
            currToBoardLastTime = worldTime;

            return currToBoardBoarded >= currToBoardTotal;
        }

        public void finishCurrToBoard() {
            currToBoardId = 0;
            confirmedOnBoard += currToBoardTotal;
            remainingToBoard -= currToBoardTotal;
            currToBoardBoarded = 0;
            currToBoardTotal = 0;
        }

        public int getEstimatedBoardingFinishTime() {
            return estimatedBoardingFinishTime;
        }

        public int getOnBoardIncludingCurr() {
            return confirmedOnBoard + (hasCurrToBoard() ? (int) currToBoardBoarded : 0);
        }

        @Override
        public String toString() {
            return "Boarding{" +
                    "confirmedOnBoard: " + confirmedOnBoard +
                    ", remainingToBoard: " + remainingToBoard +
                    ", estimatedBoardingFinishTime: " + WebTime.hhmmOrNull(estimatedBoardingFinishTime) +
                    ", currToBoardId: " + currToBoardId +
                    ", currToBoardTotal: " + currToBoardTotal +
                    ", currToBoardBoarded: " + currToBoardBoarded +
                    ", currToBoardLastTime: " + WebTime.hhmmOrNull(currToBoardLastTime) +
                    '}';
        }
    }
}
