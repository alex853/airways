package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.AircraftPerformanceData;
import net.simforge.airways2.world.computations.FlightMissionToTimeline;
import net.simforge.airways2.world.computations.SimpleFlight;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * What the flight dashboard tells the pilot about the flights which follow the current one in his roster (docs/flight-reschedule.md). It only tells, nothing is moved. The
 * numbers are those of the forecast of the roster (RosterForecast), the roster page shows the same.
 * <ul>
 * <li>LATE: the current flight is late (the estimated blocks on) so that the next flight of the pilot cannot start by its plan: its blocks off is closer than the gap between
 * two flights of a pilot (ShadowJetBooking.MIN_GAP_SECONDS) to the estimated blocks on. There is no warning without a conflict.</li>
 * <li>DIVERT: the flight is diverted (declared in the air or landed at a wrong airport) with passengers on board, and the airline expects them to be delivered to the
 * destination all the same. The earliest time the aircraft may be at the destination is counted: from the position to the divert airport, an hour on the ground there
 * (a divert is never by the plan), the planned flight from there to the destination. A flight of the pilot which starts earlier than an hour after that time is in
 * conflict by our estimate. Without the next flight there is no conflict, only the reminder.</li>
 * </ul>
 */
public final class ScheduleWarnings {
    private static final Logger log = LoggerFactory.getLogger(ScheduleWarnings.class);

    /** The time on the ground at the divert airport which is counted (a divert is not by the plan, so it is generous). */
    public static final int DIVERT_GROUND_TIME = Time.ONE_HOUR;
    /** The time between the blocks on at the destination and the blocks off of the next flight which is counted for a diverted flight. */
    public static final int DIVERT_TURNAROUND_TIME = Time.ONE_HOUR;

    private static final Set<FlightMissions.Status> LATE_STATUSES = EnumSet.of(
            FlightMissions.Status.Dispatched,
            FlightMissions.Status.Preflight,
            FlightMissions.Status.Departure,
            FlightMissions.Status.Flying,
            FlightMissions.Status.Arrival,
            FlightMissions.Status.Postflight);
    private static final Set<FlightMissions.Status> DIVERT_STATUSES = EnumSet.of(
            FlightMissions.Status.Departure,
            FlightMissions.Status.Flying,
            FlightMissions.Status.Arrival,
            FlightMissions.Status.Postflight);
    private static final Set<TransportFlights.Status> PASSENGERS_ON_BOARD_STATUSES = EnumSet.of(
            TransportFlights.Status.Flying,
            TransportFlights.Status.Arrival,
            TransportFlights.Status.WaitingForDeboarding);

    public enum Kind {
        LATE,
        DIVERT
    }

    /**
     * @param nextFlight       the next flight of the pilot, null when there is none (only for DIVERT)
     * @param earliestBlocksOn when the aircraft is at the destination at the earliest (DIVERT) or when it is estimated to be at the blocks on (LATE), world time; 0 when a
     *                         diverted flight has no estimate
     * @param shortfallMinutes how many minutes are not enough before the next flight (0 without a conflict)
     */
    public record Warning(Kind kind, boolean conflict, Airports.Airport destination, FlightMissions.Mission nextFlight, int earliestBlocksOn, int shortfallMinutes) {
    }

    /**
     * A flight which is diverted with passengers on board.
     *
     * @param earliestBlocksOn the earliest time the aircraft can be at the destination, empty when the type has no performance data
     */
    public record DivertInfo(Airports.Airport destination, Airports.Airport divertAirport, OptionalInt earliestBlocksOn) {
    }

    private ScheduleWarnings() {
    }

    public static Optional<Warning> of(final World world, final FlightMissions.Mission flight) {
        if (flight == null || flight.getUserId() <= 0) {
            return Optional.empty();
        }
        final List<RosterForecast.Entry> roster = RosterForecast.of(world, flight.getUserId());
        int index = -1;
        for (int i = 0; i < roster.size(); i++) {
            if (roster.get(i).mission().getId() == flight.getId()) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return Optional.empty();
        }
        final RosterForecast.Entry entry = roster.get(index);
        final RosterForecast.Entry next = index + 1 < roster.size() ? roster.get(index + 1) : null;
        final RosterForecast.Conflict nextConflict = next != null && next.conflict() != null && next.conflict().level() == RosterForecast.Level.RED ? next.conflict() : null;
        final FlightMissions.Mission nextFlight = next != null ? next.mission() : null;

        if (entry.divertReminder()) {
            // the destination of the flight: where the passengers are expected
            final Airports.Airport destination = world.airports().byId(flight.getDestinationAirportId()).orElse(null);
            final boolean conflict = entry.arrivalNotBefore() && nextConflict != null;
            return Optional.of(new Warning(Kind.DIVERT, conflict, destination, nextFlight,
                    entry.arrivalNotBefore() ? entry.expectedArrival() : 0, conflict ? nextConflict.shortfallMinutes() : 0));
        }
        if (isDiverted(flight) || !LATE_STATUSES.contains(flight.getStatus()) || nextConflict == null) {
            return Optional.empty();
        }
        return Optional.of(new Warning(Kind.LATE, true, null, nextFlight, entry.expectedArrival(), nextConflict.shortfallMinutes()));
    }

    private static boolean isDiverted(final FlightMissions.Mission flight) {
        return flight.isDivertDeclared() || FlightMissionHelper.isLandedAtWrongAirport(flight);
    }

    /**
     * The estimated blocks on of the flight: the estimate of its timeline (the actual events move it, and a flight in the air is counted from the position of the aircraft), for a
     * flight which has not left yet the planned one plus the delay since the planned departure; never in the past.
     */
    static int estimatedBlocksOn(final World world, final FlightMissions.Mission flight) {
        final int now = world.getWorldTime();
        final int actualBlocksOn = flight.getActualArrivalWorldTime();
        if (actualBlocksOn != 0) {
            return actualBlocksOn;
        }
        int estimated = Time.fromLdt(FlightMissionToTimeline.byMissionAndAircraftPosition(world, flight).getBlocksOn().getEstimatedTime());
        if (flight.getActualDepartureWorldTime() == 0) {
            estimated += Math.max(0, now - flight.getPlannedDepartureWorldTime());
        }
        return Math.max(estimated, now);
    }

    /**
     * The flight is diverted (declared in the air, or landed at a wrong airport) and the passengers are on board: the airline expects them at the destination all the same.
     * Empty for any other flight.
     */
    static Optional<DivertInfo> divertInfo(final World world, final FlightMissions.Mission flight) {
        if (!DIVERT_STATUSES.contains(flight.getStatus()) || !isDiverted(flight)) {
            return Optional.empty();
        }
        final boolean passengersOnBoard = world.transportFlights().byFlightMissionId(flight.getId())
                .map(tf -> PASSENGERS_ON_BOARD_STATUSES.contains(tf.getStatus()) && tf.getPaxOnBoard() > 0)
                .orElse(false);
        if (!passengersOnBoard) {
            return Optional.empty();
        }

        final int divertAirportId = flight.isDivertDeclared() ? flight.getDivertAirportId() : flight.getActualLandingAirportId();
        final Optional<Airports.Airport> divertAirport = world.airports().byId(divertAirportId);
        final Optional<Airports.Airport> destination = world.airports().byId(flight.getDestinationAirportId());
        final Optional<Aircrafts.Aircraft> aircraft = world.aircrafts().byId(flight.getAircraftId());
        if (divertAirport.isEmpty() || destination.isEmpty() || aircraft.isEmpty()) {
            return Optional.empty();
        }
        final String typeIcao = world.aircraftTypes().byId(aircraft.get().getAircraftTypeId()).map(AircraftTypes.AircraftType::getIcao).orElse(null);
        if (typeIcao == null) {
            return Optional.empty();
        }
        return Optional.of(new DivertInfo(destination.get(), divertAirport.get(),
                earliestBlocksOnAtDestination(world, flight, aircraft.get(), typeIcao, divertAirport.get(), destination.get())));
    }

    // The earliest blocks on at the destination of a diverted flight, empty when the type of the aircraft has no performance data
    private static OptionalInt earliestBlocksOnAtDestination(final World world, final FlightMissions.Mission flight, final Aircrafts.Aircraft aircraft, final String typeIcao,
                                                             final Airports.Airport divertAirport, final Airports.Airport destination) {
        final int now = world.getWorldTime();
        try {
            // when the aircraft is at the divert airport: it is there already (blocks on, or the landing), or it flies from where it is
            final int atDivertAirport;
            if (flight.getActualArrivalWorldTime() != 0) {
                atDivertAirport = flight.getActualArrivalWorldTime();
            } else if (flight.getActualLandingWorldTime() != 0) {
                atDivertAirport = flight.getActualLandingWorldTime();
            } else {
                final SimpleFlight toDivert = SimpleFlight.fromPosition(aircraft.getLocationCoords(), aircraft.getLocationAltitude(), divertAirport.getCoords(), AircraftPerformanceData.getData(typeIcao));
                atDivertAirport = now + (int) toDivert.getTotalTime().toSeconds();
            }
            final int departsFromDivert = Math.max(now, atDivertAirport + DIVERT_GROUND_TIME);
            return OptionalInt.of(departsFromDivert
                    + (int) FlightMissionHelper.calculateFlightDuration(typeIcao, divertAirport, destination).toSeconds());
        } catch (final NoSuchElementException e) {
            log.warn("f/m #{} - no performance data of {}, the divert has no estimate", flight.getId(), typeIcao);
            return OptionalInt.empty();
        }
    }
}
