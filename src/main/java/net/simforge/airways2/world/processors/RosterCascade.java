package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The cascade: a flight is put first into the roster of a pilot (it goes after a flight which the pilot has made already, so it is not in the plan any more), and every flight
 * of the pilot which has not left yet is moved after it, one after another, with the least gap between the flights (ShadowJetBooking.MIN_GAP_SECONDS, the departures are
 * rounded up to 5 minutes). A flight which is later than that anyway stays where it is, and so do all the flights after it which are fine with it. Nothing is moved by
 * {@link #plan}, it only says what would be; {@link #apply} does the moves (FlightMissionControl.reschedule) when there is nothing that stops them.
 */
public final class RosterCascade {
    // the departures of the moved flights are rounded up to this step
    public static final int STEP = 5 * Time.ONE_MINUTE;

    /**
     * @param connectionsLost the journeys with two flights which lose the second flight by this move (JourneyControl.dropSecondFlight), and the passengers in them
     */
    public record Move(FlightMissions.Mission mission, int oldDeparture, int newDeparture, int connectionsLost, int connectionPaxLost) {
        public int shiftMinutes() {
            return (newDeparture - oldDeparture) / Time.ONE_MINUTE;
        }
    }

    /**
     * @param reason what stops the move of the flight: its state or the size of the shift
     */
    public record Problem(FlightMissions.Mission mission, String reason) {
    }

    /**
     * @param moves    the flights which are moved, from the earliest
     * @param problems the flights which cannot be moved as the cascade needs: the plan cannot be done when there are some
     */
    public record Plan(List<Move> moves, List<Problem> problems) {
        public boolean isPossible() {
            return problems.isEmpty();
        }
    }

    private RosterCascade() {
    }

    /**
     * What the cascade does when a flight which lands at {@code insertedArrival} is put first.
     *
     * @param excludedMissionIds the missions which are not moved: the flight which is put (when it exists) and the flight which the pilot has made
     */
    public static Plan plan(final World world, final int userId, final int insertedArrival, final Set<Integer> excludedMissionIds) {
        final List<FlightMissions.Mission> pending = world.flightMissions().allByUserId(userId)
                .filter(m -> !FlightMissionHelper.isFinishedOrCancelled(m))
                .filter(m -> m.getActualDepartureWorldTime() == 0)
                .filter(m -> !excludedMissionIds.contains(m.getId()))
                .sorted(FlightMissions.sortByDepartureTimeFromPastToFuture)
                .toList();

        final int maxShift = FlightMissionControl.RESCHEDULE_MAX_SHIFT_MINUTES * Time.ONE_MINUTE;
        final List<Move> moves = new ArrayList<>();
        final List<Problem> problems = new ArrayList<>();
        // the times of the moved flights, for the connections of the journeys which are on two moved flights
        final Map<Integer, int[]> newTimes = new HashMap<>();

        int previousArrival = insertedArrival;
        for (final FlightMissions.Mission mission : pending) {
            final int planned = mission.getPlannedDepartureWorldTime();
            final int duration = mission.getPlannedArrivalWorldTime() - planned;
            final int needed = previousArrival + ShadowJetBooking.MIN_GAP_SECONDS;
            if (planned >= needed) {
                previousArrival = mission.getPlannedArrivalWorldTime();
                continue;
            }

            final int newDeparture = (needed + STEP - 1) / STEP * STEP;
            final String reason = whyCannotBeMoved(world, mission, newDeparture - planned, maxShift);
            if (reason != null) {
                problems.add(new Problem(mission, reason));
            }
            newTimes.put(mission.getId(), new int[]{newDeparture, newDeparture + duration});
            moves.add(new Move(mission, planned, newDeparture, 0, 0));
            previousArrival = newDeparture + duration;
        }

        // the connections which the moves cost, now when all the new times are known
        final List<Move> withConnections = new ArrayList<>();
        for (final Move move : moves) {
            final int[] lost = lostConnections(world, move.mission(), newTimes);
            withConnections.add(new Move(move.mission(), move.oldDeparture(), move.newDeparture(), lost[0], lost[1]));
        }
        return new Plan(withConnections, problems);
    }

    private static String whyCannotBeMoved(final World world, final FlightMissions.Mission mission, final int shiftSeconds, final int maxShiftSeconds) {
        if (shiftSeconds > maxShiftSeconds) {
            return "it would be moved by more than " + FlightMissionControl.RESCHEDULE_MAX_SHIFT_MINUTES / 60 / 24 + " days";
        }
        final Optional<String> failed = world.flightMissionControl().rescheduleChecks(mission).entrySet().stream()
                .filter(check -> !check.getValue())
                .map(Map.Entry::getKey)
                .findFirst();
        return failed.map(check -> "it cannot be moved (" + check + ")").orElse(null);
    }

    // [journeys, passengers] of the journeys with two flights which lose the connection by the move: the same rule as FlightMissionControl.releaseLostConnections, with the
    // times of the plan for the flights which are moved too
    private static int[] lostConnections(final World world, final FlightMissions.Mission moved, final Map<Integer, int[]> newTimes) {
        final Optional<TransportFlights.Flight> flight = world.transportFlights().byFlightMissionId(moved.getId());
        if (flight.isEmpty()) {
            return new int[]{0, 0};
        }
        int journeys = 0;
        int passengers = 0;
        final List<Journeys.Journey> twoFlightJourneys = world.journeys()
                .allByAnyTransportFlightId(flight.get().getId())
                .filter(j -> j.getTransportFlight2Id() != 0)
                .toList();
        for (final Journeys.Journey journey : twoFlightJourneys) {
            final Optional<FlightMissions.Mission> first = missionOfFlight(world, journey.getTransportFlight1Id());
            final Optional<FlightMissions.Mission> second = missionOfFlight(world, journey.getTransportFlight2Id());
            final boolean kept = first.isPresent() && second.isPresent()
                    && departureOf(second.get(), newTimes) > arrivalOf(first.get(), newTimes) + JourneyProcessor.STOPOVER_CONNECTION_RESERVE;
            if (!kept) {
                journeys++;
                passengers += journey.getGroupSize();
            }
        }
        return new int[]{journeys, passengers};
    }

    private static Optional<FlightMissions.Mission> missionOfFlight(final World world, final int transportFlightId) {
        return world.transportFlights().byId(transportFlightId).flatMap(tf -> world.flightMissions().byId(tf.getFlightMissionId()));
    }

    private static int departureOf(final FlightMissions.Mission mission, final Map<Integer, int[]> newTimes) {
        final int[] times = newTimes.get(mission.getId());
        return times != null ? times[0] : mission.getPlannedDepartureWorldTime();
    }

    private static int arrivalOf(final FlightMissions.Mission mission, final Map<Integer, int[]> newTimes) {
        final int[] times = newTimes.get(mission.getId());
        return times != null ? times[1] : mission.getPlannedArrivalWorldTime();
    }

    /**
     * Does the moves of the plan, the latest flight first (a journey with two flights keeps the second one when the second flight has been moved before the first one is checked).
     */
    public static void apply(final World world, final Plan plan) {
        if (!plan.isPossible()) {
            throw new IllegalArgumentException("cascade - the plan cannot be done: " + plan.problems().stream().map(p -> "f/m #" + p.mission().getId() + " " + p.reason()).toList());
        }
        for (int i = plan.moves().size() - 1; i >= 0; i--) {
            final Move move = plan.moves().get(i);
            world.flightMissionControl().reschedule(move.mission(), move.shiftMinutes());
        }
    }
}
