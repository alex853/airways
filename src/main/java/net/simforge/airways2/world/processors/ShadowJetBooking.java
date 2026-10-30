package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.refdata.aircrafts.apd.AircraftPerformanceDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The booking of a ShadowJet flight by a pilot (the page "Book Flight"): the flight is made ahead, it is not started. The pilot chooses the type of the aircraft, the route
 * and the time of the departure; what is made:
 * <ul>
 * <li>the aircraft: a free one of the type at the airport of the departure (or one which is brought from another airport at once, or a new one, ShadowJetLogic), reserved by
 * the dispatched flight (a flight ahead holds the aircraft, ShadowJetLogic.aircraftIdsWithFlightsAhead); for a flight which goes on after another flight of the pilot it is the
 * aircraft of that flight;</li>
 * <li>the flight mission of the pilot (PC) and the contract of the pilot with the flight pay of ShadowJet;</li>
 * <li>the transport flight with the journeys of the passengers (ShadowJetLogic.provideTransportFlightForBookedFlight): when the check-in comes, it is planned.</li>
 * </ul>
 * What the pilot does with the flight after that (starts it, the boarding) is the business of the pilot, see the dashboard.
 * <p>
 * The rules: the departure is not earlier than 30 minutes from now, and there are not less than 30 minutes between the flight and every other planned flight of the pilot.
 * The state and the place of the pilot are NOT looked at (decided 2026-10-30): the pilot is the subject, the pilot starts the flight and it is the pilot's problem to be in
 * the right place in the right state (a sleeping pilot can book too, as at BusyBirds).
 * <p>
 * todo ak1 some day there will be a check whether the pilot can be in the right place in the right state by the time the flight starts; not now, nothing about the state or
 * the place of the pilot is checked here.
 */
public final class ShadowJetBooking {
    private static final Logger log = LoggerFactory.getLogger(ShadowJetBooking.class);

    /**
     * The least gap, in seconds: from now to the departure of a flight, and between any two flights of the pilot (the arrival of one and the departure of the other).
     * The gaps can be bigger.
     */
    public static final int MIN_GAP_SECONDS = 30 * 60;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ShadowJetBooking() {
    }

    /**
     * Whether a flight can be at the time: not too soon and not too close to a flight of the pilot.
     *
     * @param earliestDepartureTime now plus the least gap, UTC milliseconds
     * @param conflict              the planned flight of the pilot which is too close (the first one by the departure), null if there is none
     */
    public record SlotCheck(boolean free, boolean tooSoon, long earliestDepartureTime, FlightMissions.Mission conflict) {
    }

    /**
     * Whether the time of a flight is good: the flight [departure, arrival] starts not earlier than the least gap (30 minutes) from now, and the gap between it and every
     * planned flight of the user which is not over (the finished and the cancelled flights are over) is not less than the least gap, before it and after it.
     */
    public static SlotCheck checkSlot(final World world, final int userId, final long departureTimeMillis, final long arrivalTimeMillis, final int nowWorldTime) {
        final long gapMillis = MIN_GAP_SECONDS * 1000L;
        final long earliestDepartureMillis = millis(nowWorldTime) + gapMillis;
        final boolean tooSoon = departureTimeMillis < earliestDepartureMillis;

        final FlightMissions.Mission conflict = world.flightMissions().allByUserId(userId)
                .filter(m -> m.getStatus() != FlightMissions.Status.Finished && m.getStatus() != FlightMissions.Status.Cancelled)
                .filter(m -> millis(m.getPlannedDepartureWorldTime()) < arrivalTimeMillis + gapMillis && departureTimeMillis < millis(m.getPlannedArrivalWorldTime()) + gapMillis)
                .min(FlightMissions.sortByDepartureTimeFromPastToFuture)
                .orElse(null);
        return new SlotCheck(conflict == null && !tooSoon, tooSoon, earliestDepartureMillis, conflict);
    }

    // the world time (seconds) as UTC milliseconds
    private static long millis(final int worldTime) {
        return worldTime * 1000L;
    }

    private static String format(final long millis) {
        return LocalDateTime.ofEpochSecond(millis / 1000, 0, ZoneOffset.UTC).format(TIME_FORMAT) + " UTC";
    }

    /**
     * The answer of the booking: the booked flight mission, or the problems which stopped it (nothing is made then).
     */
    public record Result(FlightMissions.Mission mission, List<String> problems) {
        public boolean booked() {
            return mission != null;
        }
    }

    /**
     * Books a flight for the pilot.
     *
     * @param params         the parameters of the pay of the pilot on the type (the currency and the experience level), the contract is made with them
     * @param aircraftRegNo  the aircraft of the flight which this one goes on after, null for a flight on a free aircraft
     */
    public static Result book(final World world,
                              final int userId,
                              final int personId,
                              final PilotPayParams params,
                              final String aircraftTypeIcao,
                              final String fromIcao,
                              final String toIcao,
                              final long departureTimeMillis,
                              final String aircraftRegNo) {
        final List<String> problems = new ArrayList<>();

        final Optional<Persons.Person> person = world.persons().byId(personId);
        if (person.isEmpty()) {
            return failure("The user has no pilot.");
        }

        final Optional<AircraftTypes.AircraftType> aircraftType = world.aircraftTypes().byIcao(aircraftTypeIcao);
        if (aircraftType.isEmpty() || AircraftPerformanceDatabase.getPerformance(aircraftTypeIcao).isEmpty()) {
            return failure("Unknown aircraft type " + aircraftTypeIcao + ".");
        }
        final Optional<Airports.Airport> from = world.airports().byIcao(fromIcao);
        final Optional<Airports.Airport> to = world.airports().byIcao(toIcao);
        if (from.isEmpty()) {
            problems.add("Unknown airport " + fromIcao + ".");
        }
        if (to.isEmpty()) {
            problems.add("Unknown airport " + toIcao + ".");
        }
        if (!problems.isEmpty()) {
            return new Result(null, problems);
        }
        if (from.get().getId() == to.get().getId()) {
            return failure("The airports of the departure and of the arrival are the same.");
        }

        final Duration duration = FlightMissionHelper.calculateFlightDuration(aircraftTypeIcao, from.get(), to.get());
        final long arrivalTimeMillis = departureTimeMillis + duration.toMillis();

        final SlotCheck slot = checkSlot(world, userId, departureTimeMillis, arrivalTimeMillis, world.getWorldTime());
        if (slot.conflict() != null) {
            final FlightMissions.Mission c = slot.conflict();
            return failure("Your flight " + world.airports().getIcao(c.getDepartureAirportId()).orElse("?") + " -> " + world.airports().getIcao(c.getDestinationAirportId()).orElse("?")
                    + " (" + format(millis(c.getPlannedDepartureWorldTime())) + " - " + format(millis(c.getPlannedArrivalWorldTime()))
                    + ") is too close: there must be at least 30 minutes between the flights.");
        }
        if (slot.tooSoon()) {
            return failure("The departure is too soon: not earlier than " + format(slot.earliestDepartureTime()) + " (30 minutes from now).");
        }

        // the aircraft is the last thing which is looked for: it changes the world (an aircraft is brought or made)
        final Aircrafts.Aircraft aircraft;
        if (aircraftRegNo != null && !aircraftRegNo.isBlank()) {
            final Optional<Aircrafts.Aircraft> chosen = world.aircrafts().byRegNo(aircraftRegNo);
            final String aircraftProblem = chosen.isEmpty() ? "Unknown aircraft " + aircraftRegNo + "." : continuationProblem(world, userId, chosen.get(), aircraftType.get(), from.get());
            if (aircraftProblem != null) {
                return failure(aircraftProblem);
            }
            aircraft = chosen.get();
        } else {
            aircraft = ShadowJetLogic.findAvailableAircraftOrCreateNew(world, aircraftType.get(), from.get());
        }

        // the mission keeps its times in whole minutes
        final int departureWorldTime = (int) (departureTimeMillis / 1000 / 60 * 60);
        final FlightMissions.Mission mission = FlightMissionHelper.scheduleDispatchedMission(world, aircraft, from.get(), to.get(), departureWorldTime);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        mission.setUserId(userId);

        createContract(world, mission, aircraft, aircraftTypeIcao, personId, params);
        ShadowJetLogic.provideTransportFlightForBookedFlight(world, mission);

        log.info("p/s #{}, f/m #{} - ShadowJet flight {} - {} booked, a/c {} {}, departure {}",
                personId, mission.getId(), fromIcao, toIcao, aircraft.getRegNo(), aircraftTypeIcao, format(departureTimeMillis));
        return new Result(mission, List.of());
    }

    private static Result failure(final String problem) {
        return new Result(null, List.of(problem));
    }

    // The flight goes on after another flight of the pilot on the same aircraft: the aircraft is ShadowJet's, of the same type, all the flights ahead which it has are the pilot's
    // (there is at least one) and the last of them ends at the airport where this flight starts.
    private static String continuationProblem(final World world, final int userId, final Aircrafts.Aircraft aircraft, final AircraftTypes.AircraftType type, final Airports.Airport from) {
        if (aircraft.getAircraftOperatorId() != World25.ShadowJetOperatorId || aircraft.getAircraftTypeId() != type.getId()) {
            return "The aircraft " + aircraft.getRegNo() + " is not a ShadowJet aircraft of the type " + type.getIcao() + ".";
        }
        final List<FlightMissions.Mission> ahead = world.flightMissions().allForAircraft(aircraft)
                .filter(m -> m.getStatus() == FlightMissions.Status.Dispatched || m.getStatus() == FlightMissions.Status.PlannedManually || m.getStatus() == FlightMissions.Status.PlannedViaSchedule)
                .sorted(FlightMissions.sortByDepartureTimeFromPastToFuture)
                .toList();
        if (ahead.isEmpty() || ahead.stream().anyMatch(m -> m.getUserId() != userId)) {
            return "The aircraft " + aircraft.getRegNo() + " is not yours: the booking can go on only after a flight of yours on it.";
        }
        if (ahead.get(ahead.size() - 1).getDestinationAirportId() != from.getId()) {
            return "The flight must start where the last flight of the aircraft " + aircraft.getRegNo() + " ends.";
        }
        return null;
    }

    // the contract of the pilot: the flight pay by the hourly rate of ShadowJet for the planned time of the flight, the parameters of the pay of the pilot
    private static void createContract(final World world, final FlightMissions.Mission mission, final Aircrafts.Aircraft aircraft, final String aircraftTypeIcao,
                                       final int personId, final PilotPayParams params) {
        final int plannedDurationSeconds = mission.getPlannedArrivalWorldTime() - mission.getPlannedDepartureWorldTime();
        final int baseFlightPay = ShadowJetPilotPay.baseFlightPay(aircraftTypeIcao, plannedDurationSeconds);
        final var contract = world.pilotContracts().create(mission.getId(), aircraft.getAircraftOperatorId(), personId, baseFlightPay, params);
        log.info("p/s #{}, f/m #{} - pilot contract #{}: base flight pay {}, params {}", personId, mission.getId(), contract.getId(), baseFlightPay, params);
    }
}
