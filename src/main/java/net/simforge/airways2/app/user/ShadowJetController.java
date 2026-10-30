package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.computations.PilotPayRates;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.ShadowJetSalesForecast;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.world.processors.ShadowJetBooking;
import net.simforge.airways2.world.processors.ShadowJetPilotPay;
import net.simforge.commons.misc.Geo;
import net.simforge.refdata.aircrafts.apd.AircraftPerformanceDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * The booking of ShadowJet flights: the page "Book Flight" of the site. For now the reference of the aircraft types and the terms of the pilot on a type.
 */
@RestController
@RequestMapping("/shadow-jet")
@CrossOrigin
public class ShadowJetController {
    /**
     * The least gap, in seconds: from now to the departure of a flight, and between any two flights of the pilot (the arrival of one and the departure of the other).
     * The gaps can be bigger.
     */
    public static final int MIN_GAP_SECONDS = ShadowJetBooking.MIN_GAP_SECONDS;

    @Autowired
    private WorldRunnerBean worldBean;

    /**
     * The reference of the aircraft types a ShadowJet flight can be booked on: the types of the world which have performance data, because the time of the
     * arrival of a flight is calculated from it. The types by the ICAO code.
     */
    @GetMapping("/aircraft-types")
    public List<AircraftTypeDto> getAircraftTypes() {
        return worldBean.read(world -> world.aircraftTypes().all()
                .filter(type -> AircraftPerformanceDatabase.getPerformance(type.getIcao()).isPresent())
                .sorted(Comparator.comparing(AircraftTypes.AircraftType::getIcao))
                .map(type -> new AircraftTypeDto(type.getIcao()))
                .toList());
    }

    /**
     * What the pilot of the user gets on a type of aircraft at ShadowJet: the qualifications of the pilot on the type, the base hourly rate, the deductions and the
     * shares of ShadowJet. The same answer as the one of BusyBirds (GET /busy-birds/pilot-terms), but by the type of aircraft, not by an aircraft, and with the rates
     * of ShadowJet (PilotPayRates.SHADOW_JET).
     */
    @GetMapping("/pilot-terms")
    public BusyBirdsController.PilotTermsDto getPilotTerms(@RequestAttribute("userId") final int userId,
                                                           @RequestParam("type") final String aircraftType) {
        return worldBean.read(world -> {
            final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
            if (personId <= 0 || world.persons().byId(personId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The user has no pilot (person)");
            }
            if (world.aircraftTypes().byIcao(aircraftType).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown aircraft type " + aircraftType);
            }
            return BusyBirdsController.buildPilotTerms(world, personId, aircraftType, ShadowJetPilotPay.baseHourlyRate(aircraftType), PilotPayRates.SHADOW_JET);
        });
    }

    /**
     * Everything the page "Book Flight" asks when the type, the route and the time of the departure are known, in one answer of three parts:
     * <ol>
     * <li>the arrival: how long the flight lasts (from blocks off to blocks on, the same calculation as of the planned arrival of the flight which is booked,
     * FlightMissionHelper.calculateFlightDuration) and when the flight arrives;</li>
     * <li>the slot: whether the flight can be at this time: it starts not earlier than 30 minutes from now, and there are not less than 30 minutes between it and every
     * other planned flight of the pilot (before and after it); if it is too close to a flight, which flight it is;</li>
     * <li>the expected load: what the sales team hopes to gather by how long before the departure the flight is booked (ShadowJetSalesForecast). It is a hope, the real
     * load is not known until the end of the booking. The approximate income is not calculated yet, it is null.</li>
     * </ol>
     * The distance is in nautical miles, the times are UTC milliseconds.
     */
    @GetMapping("/plan-flight")
    public FlightPlanDto planFlight(@RequestAttribute("userId") final int userId,
                                    @RequestParam("type") final String aircraftType,
                                    @RequestParam("from") final String fromIcao,
                                    @RequestParam("to") final String toIcao,
                                    @RequestParam("departureTime") final long departureTimeMillis) {
        return worldBean.read(world -> {
            if (world.aircraftTypes().byIcao(aircraftType).isEmpty()
                    || AircraftPerformanceDatabase.getPerformance(aircraftType).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown aircraft type " + aircraftType);
            }
            final Airports.Airport from = world.airports().byIcao(fromIcao)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown airport " + fromIcao));
            final Airports.Airport to = world.airports().byIcao(toIcao)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown airport " + toIcao));
            if (from.getId() == to.getId()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The airports of the departure and of the arrival are the same");
            }

            final Duration duration = FlightMissionHelper.calculateFlightDuration(aircraftType, from, to);
            final long arrivalTimeMillis = departureTimeMillis + duration.toMillis();
            final double distanceNm = Geo.distance(from.getCoords(), to.getCoords());
            final int seats = world.transportFlightControl().cabinLayoutOfType(aircraftType).getTotal();
            return new FlightPlanDto(
                    (int) Math.round(distanceNm),
                    (int) duration.getSeconds(),
                    arrivalTimeMillis,
                    checkSlot(world, userId, departureTimeMillis, arrivalTimeMillis, world.getWorldTime()),
                    expectedLoad(departureTimeMillis - millis(world.getWorldTime()), seats, distanceNm));
        });
    }

    // the slot of a flight, see ShadowJetBooking.checkSlot: not too soon and not too close to a planned flight of the pilot (the first one which is closer is the conflict)
    static SlotDto checkSlot(final World world, final int userId, final long departureTimeMillis, final long arrivalTimeMillis, final int nowWorldTime) {
        final ShadowJetBooking.SlotCheck slot = ShadowJetBooking.checkSlot(world, userId, departureTimeMillis, arrivalTimeMillis, nowWorldTime);
        final FlightMissions.Mission m = slot.conflict();
        return new SlotDto(slot.free(), slot.tooSoon(), slot.earliestDepartureTime(), m == null ? null : new ConflictDto(
                Id.encode(m.getId()),
                world.airports().getIcao(m.getDepartureAirportId()).orElseThrow(),
                world.airports().getIcao(m.getDestinationAirportId()).orElseThrow(),
                millis(m.getPlannedDepartureWorldTime()),
                millis(m.getPlannedArrivalWorldTime()),
                m.getStatus().name()));
    }

    // the world time (seconds) as UTC milliseconds
    private static long millis(final int worldTime) {
        return worldTime * 1000L;
    }

    /**
     * Books a flight: the page "Book Flight" after the pilot has seen the plan (plan-flight). What is made is in ShadowJetBooking: the aircraft (reserved), the flight of the
     * pilot, the contract and the transport flight with the passengers. A refusal (the pilot is not idle, the time is taken or too soon, no access) is a normal answer
     * with status "failure" and the reasons, the same as at BusyBirds; a request which makes no sense (an unknown type or airport) is an answer with the status 404 or 400.
     * The parameter aircraft (the registration) is for a flight which goes on after a flight of the pilot on the same aircraft.
     */
    @PutMapping("/flight/book")
    public BookFlightResponse bookFlight(@RequestAttribute("userId") final int userId,
                                         @RequestParam("type") final String aircraftType,
                                         @RequestParam("from") final String fromIcao,
                                         @RequestParam("to") final String toIcao,
                                         @RequestParam("departureTime") final long departureTimeMillis,
                                         @RequestParam(name = "aircraft", required = false) final String aircraftRegNo) {
        if (!userHasAccessToShadowJet(userId)) {
            return new BookFlightResponse("failure", List.of("You have no access to ShadowJet."), null);
        }
        return worldBean.modifySync(world -> {
            final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
            if (personId <= 0 || world.persons().byId(personId).isEmpty()) {
                return new BookFlightResponse("failure", List.of("The user has no pilot (person)."), null);
            }
            // the parameters of the pay are the currency and the level of the pilot on the type at the moment of the booking
            final PilotPayParams params = PilotController.payParams(world, personId, aircraftType);
            final ShadowJetBooking.Result result = ShadowJetBooking.book(
                    world, userId, personId, params, aircraftType, fromIcao.toUpperCase(), toIcao.toUpperCase(), departureTimeMillis, aircraftRegNo);
            if (!result.booked()) {
                return new BookFlightResponse("failure", result.problems(), null);
            }
            return new BookFlightResponse("success", List.of("The flight is booked."), Id.encode(result.mission().getId()));
        });
    }

    /**
     * Whether the user may book ShadowJet flights at all.
     * todo ak1 a stub: everybody has access for now, the same as checkUserHasAccessToBusyBirds is going to become a list of the users who are allowed (see docs/users.md)
     */
    static boolean userHasAccessToShadowJet(final int userId) {
        return true;
    }

    @Data
    @AllArgsConstructor
    public static class BookFlightResponse {
        // success or failure
        private String status;
        private List<String> messages;
        // the id of the booked flight, null for a failure
        private String flightId;
    }

    @Data
    @AllArgsConstructor
    public static class FlightPlanDto {
        // nautical miles
        private int distance;
        // from blocks off to blocks on
        private int durationSeconds;
        // UTC milliseconds
        private long arrivalTime;
        private SlotDto slot;
        // what the sales team hopes to gather by how long before the departure the flight is booked
        private ExpectedLoadDto expectedLoad;
    }

    @Data
    @AllArgsConstructor
    public static class SlotDto {
        // the flight can be at this time: it is not too soon and not too close to another planned flight of the pilot
        private boolean free;
        // the departure is earlier than 30 minutes from now
        private boolean tooSoon;
        // the earliest departure which the server accepts now: 30 minutes from now (UTC milliseconds)
        private long earliestDepartureTime;
        // the planned flight which is too close (the first one), null if there is none
        private ConflictDto conflict;
    }

    @Data
    @AllArgsConstructor
    public static class ConflictDto {
        private String flightId;
        private String from;
        private String to;
        // UTC milliseconds
        private long departureTime;
        private long arrivalTime;
        private String status;
    }

    private static ExpectedLoadDto expectedLoad(final long millisBeforeDeparture, final int seats, final double distanceNm) {
        final ShadowJetSalesForecast.ExpectedLoad load = ShadowJetSalesForecast.expectedLoad(millisBeforeDeparture / 1000);
        return new ExpectedLoadDto(load.loadFromPercent(), load.loadToPercent(), ShadowJetSalesForecast.approximateIncome(seats, load, distanceNm));
    }

    @Data
    @AllArgsConstructor
    public static class ExpectedLoadDto {
        // what the sales team hopes to gather, in percents of the seats: from and to
        private int loadFromPercent;
        private int loadToPercent;
        // the approximate income of such a load: the seats * the middle of the load * the price of an economy passenger on the distance (ShadowJetSalesForecast.approximateIncome)
        private Integer income;
    }

    @Data
    @AllArgsConstructor
    public static class AircraftTypeDto {
        private String icao;
        // TODO the name of the type
    }
}
