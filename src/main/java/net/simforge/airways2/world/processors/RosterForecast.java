package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the roster of a pilot is going to be like from the current situation: the flights which are not over, in the order of the planned departure, each with the expected
 * departure and arrival and the conflict with the flight before it. The flight dashboard and the roster show the same numbers.
 * <ul>
 * <li>The expected arrival of a flight which has left is the estimate of its timeline (from the position of the aircraft, FlightMissionToTimeline), of a flight which has not
 * left it is the departure plus the planned time of the flight; the departure of a flight which has not left is not earlier than now. A diverted flight goes to the airport of the
 * divert now, so its expected arrival is the arrival there.</li>
 * <li>A flight which is diverted with passengers on board is expected to go on to the destination of the plan after the divert: the earliest time the aircraft can be there is
 * the delivery time (ScheduleWarnings), and the next flight needs an hour after it (ScheduleWarnings.DIVERT_TURNAROUND_TIME), not the usual gap between flights.</li>
 * <li>A flight which departs closer to the arrival of the flight before it than the gap needs is in conflict. It is counted that such a flight is moved to the least that
 * solves the conflict (the arrival of the flight before plus the gap), and the flights after it are counted from that. So the conflict goes down the roster: it is RED
 * where the flight before it is as it is (a late flight, a divert), and YELLOW where it appears only because the flights before it would have to be moved.</li>
 * </ul>
 * Nothing is moved, this is the forecast only.
 */
public final class RosterForecast {
    // the times which are suggested for a move are rounded up to this step
    private static final int SUGGESTION_STEP = 5 * Time.ONE_MINUTE;
    // the times which the pilot chooses from for the move of a flight go by this step (from the whole hour: :00, :15, :30, :45)
    public static final int RESCHEDULE_OPTION_STEP = 15 * Time.ONE_MINUTE;
    // a flight which has no flight after it can be moved this far after the planned departure
    public static final int RESCHEDULE_WITHOUT_NEXT_FLIGHT = 12 * Time.ONE_HOUR;
    // a flight which is planned earlier than its earliest time (it is in conflict with the flight before it) is offered the times this far after the earliest one
    public static final int RESCHEDULE_AFTER_EARLIEST = 2 * Time.ONE_HOUR;

    public enum Level {
        RED,
        YELLOW
    }

    /**
     * @param level            RED: the flight before it is as it is; YELLOW: it appears only after the moves of the flights before it
     * @param shortfallMinutes how many minutes the flight is short of the gap
     * @param cause            the flight before it
     * @param estimate         the cause is a diverted flight or goes after one: the time of its arrival is an estimate
     * @param suggestedDeparture the earliest departure which has no conflict with the flight before it (world time, a multiple of 5 minutes)
     */
    public record Conflict(Level level, int shortfallMinutes, FlightMissions.Mission cause, boolean estimate, int suggestedDeparture) {
    }

    /**
     * @param deliveryNotBefore the earliest time a diverted flight with passengers on board can deliver them to the destination of the plan (world time, it can be later), 0 when the
     *                          flight is not diverted with passengers or has no estimate
     * @param divertReminder    the flight is diverted with passengers on board, the airline expects them to be delivered
     * @param divertAirport     the airport of the divert (declared or landed at), null if the flight is not diverted with passengers
     * @param conflict          null when there is none
     */
    public record Entry(FlightMissions.Mission mission,
                        int expectedDeparture,
                        int expectedArrival,
                        int deliveryNotBefore,
                        boolean divertReminder,
                        Airports.Airport divertAirport,
                        Conflict conflict) {
    }

    private RosterForecast() {
    }

    public static List<Entry> of(final World world, final int userId) {
        if (userId <= 0) {
            return List.of();
        }
        final int now = world.getWorldTime();
        final List<FlightMissions.Mission> flights = world.flightMissions().allByUserId(userId)
                .filter(m -> !FlightMissionHelper.isFinishedOrCancelled(m))
                .sorted(FlightMissions.sortByDepartureTimeFromPastToFuture)
                .toList();

        final List<Entry> result = new ArrayList<>();
        Entry previous = null;
        for (final FlightMissions.Mission flight : flights) {
            final Optional<ScheduleWarnings.DivertInfo> divert = ScheduleWarnings.divertInfo(world, flight);
            final int deliveryNotBefore = divert.isPresent() && divert.get().earliestBlocksOn().isPresent() ? divert.get().earliestBlocksOn().getAsInt() : 0;

            int departure;
            int arrival;
            Conflict conflict = null;
            if (flight.getActualDepartureWorldTime() != 0) {
                departure = flight.getActualDepartureWorldTime();
                arrival = ScheduleWarnings.estimatedBlocksOn(world, flight);
            } else {
                final int plannedDuration = flight.getPlannedArrivalWorldTime() - flight.getPlannedDepartureWorldTime();
                final int wanted = Math.max(flight.getPlannedDepartureWorldTime(), now);
                departure = wanted;
                if (previous != null) {
                    final int needed = earliestNextDeparture(previous);
                    if (wanted < needed) {
                        conflict = new Conflict(previous.conflict() == null ? Level.RED : Level.YELLOW,
                                minutesUp(needed - wanted),
                                previous.mission(),
                                previous.deliveryNotBefore() != 0 || (previous.conflict() != null && previous.conflict().estimate()),
                                roundUp(needed));
                        departure = needed;
                    }
                }
                arrival = departure + plannedDuration;
            }

            result.add(new Entry(flight, departure, arrival, deliveryNotBefore, divert.isPresent(), divert.map(ScheduleWarnings.DivertInfo::divertAirport).orElse(null), conflict));
            previous = result.get(result.size() - 1);
        }
        return result;
    }

    /**
     * The earliest departure of the flight which goes after the flight: the gap after its arrival, and after a diverted flight (which starts from the destination of the plan
     * when the aircraft is delivered there) the hour of the turnaround after the delivery.
     */
    static int earliestNextDeparture(final Entry previous) {
        return previous.deliveryNotBefore() != 0
                ? previous.deliveryNotBefore() + ScheduleWarnings.DIVERT_TURNAROUND_TIME
                : previous.expectedArrival() + ShadowJetBooking.MIN_GAP_SECONDS;
    }

    /**
     * The times of the departure of a flight which keep it between its neighbours in the roster, for the move of the flight (the times go by RESCHEDULE_OPTION_STEP, from the
     * earliest to the latest). The earliest is after the flight before it has landed and the gap has passed (not earlier than now plus the least reserve of the departure), the
     * latest is the one where the flight lands the gap before the PLANNED departure of the flight after it (not the expected one: the expected departure of a flight which is in
     * conflict with this one is only where it would be moved to by this flight, so it floats with it); a flight without the one after it can be moved 12 hours later. Both are
     * inside the limits of the shift. The window is empty (the earliest is later than the latest) when there is no room between the neighbours, that is when there is no time
     * without a conflict (see rescheduleChoices for what the times with a conflict do).
     *
     * @return empty when the flight cannot be moved: it has left, or it is not in a state which the move needs (FlightMissionControl.rescheduleChecks)
     */
    public static Optional<Window> rescheduleWindow(final World world, final List<Entry> roster, final int index) {
        final FlightMissions.Mission mission = roster.get(index).mission();
        if (mission.getActualDepartureWorldTime() != 0 || !world.flightMissionControl().rescheduleChecks(mission).values().stream().allMatch(ok -> ok)) {
            return Optional.empty();
        }
        final int planned = mission.getPlannedDepartureWorldTime();
        final int maxShift = FlightMissionControl.RESCHEDULE_MAX_SHIFT_MINUTES * Time.ONE_MINUTE;

        int earliest = world.getWorldTime() + FlightMissionControl.RESCHEDULE_MIN_DEPARTURE_RESERVE;
        if (index > 0) {
            earliest = Math.max(earliest, earliestNextDeparture(roster.get(index - 1)));
        }
        final int duration = mission.getPlannedArrivalWorldTime() - planned;
        int latest = index + 1 < roster.size()
                ? plannedDepartureOfNext(world, roster.get(index + 1)) - ShadowJetBooking.MIN_GAP_SECONDS - duration
                : planned + RESCHEDULE_WITHOUT_NEXT_FLIGHT;

        earliest = Math.max(earliest, planned - maxShift);
        latest = Math.min(latest, planned + maxShift);
        return Optional.of(new Window(roundUp(earliest, RESCHEDULE_OPTION_STEP), Math.floorDiv(latest, RESCHEDULE_OPTION_STEP) * RESCHEDULE_OPTION_STEP));
    }

    // the planned departure of a flight which has not left, not earlier than now
    private static int plannedDepartureOfNext(final World world, final Entry next) {
        return Math.max(next.mission().getPlannedDepartureWorldTime(), world.getWorldTime());
    }

    /**
     * What a time of the move does to the flight after the moved one: it leaves at nextDeparture (the planned one, or later when the moved flight lands too late for it) and
     * is shortfallMinutes short of the gap (0 when there is no conflict).
     *
     * @param nextDeparture 0 when there is no flight after
     */
    public record Option(int departure, int nextDeparture, int shortfallMinutes) {
    }

    /**
     * The choices of the move of a flight: the times with what each of them does to the flight after it, and the neighbours.
     *
     * @param window    the times without a conflict (empty window: there are none)
     * @param options   the times to choose from, from the earliest: from the earliest after the flight before it to the latest time without a conflict, and to the current
     *                  departure when there is none (the later times only make the flight after it later); without the current departure
     * @param before    the flight before it, null for the first one
     * @param after     the flight after it, null for the last one
     * @param atEarliest the flight is already at the earliest time: there are no times to move to, and none is earlier than the current one
     */
    public record Choices(Window window, List<Option> options, Entry before, Entry after, boolean atEarliest) {
    }

    /**
     * The choices of the move of a flight (see Choices): empty when the flight cannot be moved (rescheduleWindow).
     */
    public static Optional<Choices> rescheduleChoices(final World world, final List<Entry> roster, final int index) {
        final Optional<Window> window = rescheduleWindow(world, roster, index);
        if (window.isEmpty()) {
            return Optional.empty();
        }
        final FlightMissions.Mission mission = roster.get(index).mission();
        final int planned = mission.getPlannedDepartureWorldTime();
        final int duration = mission.getPlannedArrivalWorldTime() - planned;
        final Entry before = index > 0 ? roster.get(index - 1) : null;
        final Entry after = index + 1 < roster.size() ? roster.get(index + 1) : null;

        // from the earliest to the latest time without a conflict, and not later than the current departure when there is none (inside the limits of the shift). A flight which
        // is planned earlier than the earliest time is in conflict with the flight before it now: it has no "current departure" to stop at, so the times go on for two hours
        // after the earliest one (the earliest is the best for the flight after it, the later ones are for the pilot who wants them for other reasons)
        final int maxShift = FlightMissionControl.RESCHEDULE_MAX_SHIFT_MINUTES * Time.ONE_MINUTE;
        int upper = Math.max(window.get().latestDeparture(), planned);
        if (planned < window.get().earliestDeparture()) {
            upper = Math.max(upper, window.get().earliestDeparture() + RESCHEDULE_AFTER_EARLIEST);
        }
        upper = Math.min(upper, planned + maxShift);
        final List<Option> options = new ArrayList<>();
        for (int time = window.get().earliestDeparture(); time <= upper; time += RESCHEDULE_OPTION_STEP) {
            if (time == planned) {
                continue;
            }
            if (after == null) {
                options.add(new Option(time, 0, 0));
                continue;
            }
            final int nextBase = plannedDepartureOfNext(world, after);
            final int needed = time + duration + ShadowJetBooking.MIN_GAP_SECONDS;
            options.add(new Option(time, Math.max(nextBase, needed), needed > nextBase ? minutesUp(needed - nextBase) : 0));
        }
        return Optional.of(new Choices(window.get(), options, before, after, options.isEmpty() && window.get().earliestDeparture() >= planned));
    }

    /**
     * @param earliestDeparture the earliest departure which keeps the flight between its neighbours (world time, on the step of the options)
     * @param latestDeparture   the latest one
     */
    public record Window(int earliestDeparture, int latestDeparture) {
        public boolean isEmpty() {
            return earliestDeparture > latestDeparture;
        }
    }

    private static int minutesUp(final int seconds) {
        return (seconds + Time.ONE_MINUTE - 1) / Time.ONE_MINUTE;
    }

    private static int roundUp(final int time) {
        return roundUp(time, SUGGESTION_STEP);
    }

    private static int roundUp(final int time, final int step) {
        return (time + step - 1) / step * step;
    }
}
