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
                    // after a diverted flight the next one starts from the destination of the plan, when the aircraft is delivered there
                    final int needed = previous.deliveryNotBefore() != 0
                            ? previous.deliveryNotBefore() + ScheduleWarnings.DIVERT_TURNAROUND_TIME
                            : previous.expectedArrival() + ShadowJetBooking.MIN_GAP_SECONDS;
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

    private static int minutesUp(final int seconds) {
        return (seconds + Time.ONE_MINUTE - 1) / Time.ONE_MINUTE;
    }

    private static int roundUp(final int time) {
        return (time + SUGGESTION_STEP - 1) / SUGGESTION_STEP * SUGGESTION_STEP;
    }
}
