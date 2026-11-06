package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotPay;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.computations.PilotPayRates;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.BusyBirdsMissionControl;
import net.simforge.airways2.world.processors.BusyBirdsPilotPay;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.JavaTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/busy-birds")
@CrossOrigin
public class BusyBirdsController {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    /**
     * The cities for the dropdowns of the page (the list of all missions is too big to load): all cities by name. The page preselects the current one.
     */
    @GetMapping("/cities")
    public List<CityDto> getCities(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            return world.cities().all()
                    .map(c -> new CityDto(Id.encode(c.getId()), c.getName()))
                    .sorted(Comparator.comparing(CityDto::getName))
                    .toList();
        });
    }

    /**
     * The missions between the given cities; at least one of the cities is required (without any the answer is empty, the list of all missions is too big).
     */
    @GetMapping("/mission/to-book")
    public List<MissionDto> getMissionsToBook(@RequestAttribute("userId") int userId,
                                              @RequestParam(name = "fromCityId", required = false) String fromCityId,
                                              @RequestParam(name = "toCityId", required = false) String toCityId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            final int fromId = fromCityId == null || fromCityId.isBlank() ? 0 : Id.decode(fromCityId);
            final int toId = toCityId == null || toCityId.isBlank() ? 0 : Id.decode(toCityId);
            if (fromId <= 0 && toId <= 0) {
                return List.<MissionDto>of();
            }

            return world.busyBirdsMissionControl().getMissionsToBook(fromId, toId).stream()
                    .map(m -> {
                        Journeys.Journey j = m.getJourney();

                        Cities.City fromCity = world.cities().byId(j.getLocationCityId()).orElseThrow();
                        Cities.City toCity = world.cities().byId(j.getToCityId()).orElseThrow();

                        return new MissionDto(
                                Id.encode(j.getId()),
                                Id.encode(fromCity.getId()),
                                fromCity.getName(),
                                Id.encode(toCity.getId()),
                                toCity.getName(),
                                j.getPreferredCabinService().name(),
                                j.getGroupSize(),
                                m.getDistance(),
                                m.getPay(),
                                m.isPayExact());
                    }).sorted(Comparator.comparing(MissionDto::getFromCityName)
                            .thenComparing(MissionDto::getToCityName))
                    .toList();
        });
    }

    @GetMapping("/aircraft/available")
    public List<AircraftDto> getAvailableAircraft(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            // the aircraft which are free (idle at an airport, no missions of anybody) and the aircraft which have missions of this user only: the mission goes on after them, the
            // aircraft is where its last mission lands and is free from its arrival plus the least gap
            final List<Aircrafts.Aircraft> all = world.aircrafts().byAircraftOperatorId(World25.BusyBirdsOperatorId).toList();
            final Map<Integer, BusyBirdsMissionControl.AircraftOutlook> outlooks = world.busyBirdsMissionControl().outlooks(all);
            return all.stream()
                    .filter(a -> {
                        final BusyBirdsMissionControl.AircraftOutlook outlook = outlooks.get(a.getId());
                        if (outlook.airport() == null || !outlook.isOpenTo(userId)) {
                            return false;
                        }
                        return outlook.hasOpenMissions() || (a.getOperationalStatus() == Aircrafts.OperationalStatus.Idle && a.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport);
                    })
                    .map(a -> {
                        final BusyBirdsMissionControl.AircraftOutlook outlook = outlooks.get(a.getId());
                        return new AircraftDto(
                                Id.encode(a.getId()),
                                world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                                a.getRegNo(),
                                outlook.airport().getIcao(),
                                aircraftEarliestStart(outlook),
                                world.busyBirdsMissionControl().seatsOf(a));
                    })
                    .toList();
        });
    }

    /**
     * What the pilot of the user gets on an aircraft: the qualifications of the pilot on the type of the aircraft, the hourly rate and the share of the
     * income with their deductions (see PilotPay). The numbers are calculated here, so the page only shows them.
     */
    @GetMapping("/pilot-terms")
    public PilotTermsDto getPilotTerms(@RequestAttribute("userId") int userId,
                                       @RequestParam(name = "aircraftId") String aircraftId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
            if (personId <= 0 || world.persons().byId(personId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The user has no pilot (person)");
            }
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(Id.decode(aircraftId)).orElseThrow();
            return buildPilotTerms(world, personId, aircraft);
        });
    }

    // The parameters of the pay of the pilot of the user on the type of the aircraft. A user without a pilot gets the parameters of nothing (no currency).
    static PilotPayParams payParamsOfUser(final World world, final int userId, final Aircrafts.Aircraft aircraft) {
        final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
        if (personId <= 0 || world.persons().byId(personId).isEmpty()) {
            return PilotPayParams.none();
        }
        final String aircraftTypeIcao = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao();
        return PilotController.payParams(world, personId, aircraftTypeIcao);
    }

    static PilotTermsDto buildPilotTerms(final World world, final int personId, final Aircrafts.Aircraft aircraft) {
        final String aircraftType = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao();
        return buildPilotTerms(world, personId, aircraftType, BusyBirdsPilotPay.baseHourlyRate(aircraftType), PilotPayRates.BUSY_BIRDS);
    }

    /**
     * What the pilot gets on a type of aircraft: the qualifications of the pilot on the type, the base hourly rate, the rates of the operator (the deductions and the
     * shares), and what comes out of them. BusyBirds asks it by the aircraft, ShadowJet by the type.
     */
    static PilotTermsDto buildPilotTerms(final World world, final int personId, final String aircraftType, final int hourlyRate, final PilotPayRates rates) {
        final PilotPayParams params = PilotController.payParams(world, personId, aircraftType);

        final List<PilotPay.Deduction> flightPayDeductions = PilotPay.flightPayDeductions(params);
        final int flightPayCoefficient = PilotPay.flightPayCoefficientPercent(params, rates);

        final float incomeShare = PilotPay.baseIncomeSharePercent(params.getExperienceLevel(), rates);
        final List<PilotPay.Deduction> incomeShareDeductions = PilotPay.incomeShareDeductions(params);
        final int incomeShareCoefficient = PilotPay.incomeShareCoefficientPercent(params, rates);

        return new PilotTermsDto(
                aircraftType,
                params.getExperienceLevel(),
                params.hasTypeCurrency(),
                params.hasNightCurrency(),
                params.canCarryPassengers(),
                hourlyRate,
                flightPayDeductions.stream().map(d -> DeductionDto.from(d, rates)).toList(),
                flightPayCoefficient,
                hourlyRate * flightPayCoefficient / 100.0,
                incomeShare,
                incomeShareDeductions.stream().map(d -> DeductionDto.from(d, rates)).toList(),
                incomeShareCoefficient,
                incomeShare * incomeShareCoefficient / 100.0);
    }

    @GetMapping("/mission/build-plans")
    public BuildPlansResponse buildPlans(@RequestAttribute("userId") int userId,
                                         @RequestParam(name = "missionId") String missionId,
                                         @RequestParam(name = "aircraftId") String aircraftId,
                                         @RequestParam(name = "startTime") long startTime,
                                         @RequestParam(name = "pauses", required = false) String pauses,
                                         @RequestParam(name = "ferryBackToBase", required = false, defaultValue = "true") boolean ferryBackToBase) {
        // a quote changes the world: the price of the journey is set
        return worldBean.modifySync(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            Aircrafts.Aircraft aircraft = world.aircrafts().byId(Id.decode(aircraftId)).orElseThrow();
            Journeys.Journey journey = world.journeys().byId(Id.decode(missionId)).orElseThrow();

            final int price = world.busyBirdsMissionControl().quote(journey);

            final BusyBirdsMissionControl.AircraftOutlook outlook = world.busyBirdsMissionControl().outlooks(List.of(aircraft)).get(aircraft.getId());
            final String aircraftProblem = aircraftProblem(outlook, userId);
            if (aircraftProblem != null) {
                return new BuildPlansResponse(List.of(PlanDto.failure(aircraftProblem)), price);
            }
            // the group has to fit into the aircraft
            final String seatsProblem = BusyBirdsMissionControl.seatsProblem(world.busyBirdsMissionControl().seatsOf(aircraft), journey.getGroupSize());
            if (seatsProblem != null) {
                return new BuildPlansResponse(List.of(PlanDto.failure(seatsProblem)), price);
            }

            List<BusyBirdsMissionControl.MissionPlan> plans = world.busyBirdsMissionControl().buildPlans(journey, aircraft, outlook.airport(), pauses(pauses), ferryBackToBase,
                    payParamsOfUser(world, userId, aircraft), firstDepartureTime(startTime));

            // every plan tells whether its time is free in the roster of the user and for the aircraft
            final int now = world.getWorldTime();
            return new BuildPlansResponse(plans.stream().map(plan -> {
                final PlanDto dto = PlanDto.from(plan);
                if (dto.getStartTime() != null) {
                    dto.setSlot(checkStartSlot(world, userId, dto.getStartTime(), dto.getEndTime(), now, aircraftEarliestStart(outlook)));
                }
                return dto;
            }).toList(), price);
        });
    }

    @PutMapping("/mission/book")
    public BookMissionResponse bookMission(@RequestAttribute("userId") int userId,
                                           @RequestParam(name = "missionId") String missionId,
                                           @RequestParam(name = "aircraftId") String aircraftId,
                                           @RequestParam(name = "startTime") long startTime,
                                           @RequestParam(name = "pauses", required = false) String pauses,
                                           @RequestParam(name = "ferryBackToBase", required = false, defaultValue = "true") boolean ferryBackToBase,
                                           @RequestParam(name = "planId") String planId) {
        return worldBean.modifySync(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            Aircrafts.Aircraft aircraft = world.aircrafts().byId(Id.decode(aircraftId)).orElseThrow();
            Journeys.Journey journey = world.journeys().byId(Id.decode(missionId)).orElseThrow();

            // only a pilot is needed, not its state or place, as at ShadowJet (decided 2026-10-30): it is the business of the pilot to come to the aircraft and start the flight
            final String pilotProblem = noPilotProblem(world, userId);
            if (pilotProblem != null) {
                return new BookMissionResponse("failure", Collections.singletonList(pilotProblem));
            }

            // the aircraft is free, or all its missions are the missions of this user (the mission goes on after them)
            final BusyBirdsMissionControl.AircraftOutlook outlook = world.busyBirdsMissionControl().outlooks(List.of(aircraft)).get(aircraft.getId());
            final String aircraftProblem = aircraftProblem(outlook, userId);
            if (aircraftProblem != null) {
                return new BookMissionResponse("failure", Collections.singletonList(aircraftProblem));
            }
            // the group has to fit into the aircraft
            final String seatsProblem = BusyBirdsMissionControl.seatsProblem(world.busyBirdsMissionControl().seatsOf(aircraft), journey.getGroupSize());
            if (seatsProblem != null) {
                return new BookMissionResponse("failure", Collections.singletonList(seatsProblem));
            }

            // the parameters of the pay are the currency and the level of the pilot on the type at the moment of the booking, the plans and the pilot contracts have them
            final PilotPayParams payParams = payParamsOfUser(world, userId, aircraft);
            List<BusyBirdsMissionControl.MissionPlan> plans = world.busyBirdsMissionControl().buildPlans(journey, aircraft, outlook.airport(), pauses(pauses), ferryBackToBase, payParams,
                    firstDepartureTime(startTime));

            Optional<BusyBirdsMissionControl.MissionPlan> plan = plans.stream().filter(p -> planId.equals(p.getId())).findFirst();
            if (plan.isEmpty()) {
                return new BookMissionResponse("failure", Collections.singletonList("unable to find plan by planId"));
            }

            if (plan.get().getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                return new BookMissionResponse("failure", plan.get().getMessages());
            }

            // the whole mission (from the first departure to the last arrival) must fit into the roster of the user: not too soon, not later than 7 days from now,
            // not closer than 30 minutes to another flight of the pilot, and not before the aircraft is free (its booked missions are over and the least gap has passed)
            final PlanDto planDto = PlanDto.from(plan.get());
            final StartSlotDto slot = checkStartSlot(world, userId, planDto.getStartTime(), planDto.getEndTime(), world.getWorldTime(), aircraftEarliestStart(outlook));
            if (!slot.isFree()) {
                return new BookMissionResponse("failure", startSlotProblems(slot));
            }

            // the pilot contracts of the flights
            final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElseThrow();

            List<String> messages = new ArrayList<>();
            for (final BusyBirdsMissionControl.Leg leg : plan.get().getLegs()) {
                FlightMissions.Mission flight = FlightMissionHelper.scheduleDispatchedMission(world, aircraft, leg.getFromAirport(), leg.getToAirport(), leg.getPlannedDepTime());
                flight.setCharacterMode(FlightMissions.CharacterMode.PC);
                flight.setUserId(userId);
                messages.add("Flight mission # " + flight.getId() + " scheduled, departure time: " + TimeTools.ts(flight.getPlannedDepartureWorldTime()));

                final boolean revenue = leg.getType() == BusyBirdsMissionControl.Leg.Type.Revenue;
                // the income of the journey is fixed in the contract before the journey is booked to the transport flight
                world.busyBirdsMissionControl().createContract(flight, personId, payParams);

                // all busyBirds aircraft are expected to have J-layout only
                if (leg.getType() == BusyBirdsMissionControl.Leg.Type.Revenue) {
                    TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(flight);
                    journey.setTransportFlight1Id(transportFlight.getId());
                    world.transportFlightControl().obtainFlightTickets(transportFlight, journey.getGroupSize(), CabinLayout.Service.J);
                    // the price of the journey is the income of the flight: the price of the business charter, the quote sets it if the pilot has booked without one
                    world.transportFlightControl().addIncome(transportFlight, world.busyBirdsMissionControl().quote(journey));
                    journey.setBookedCabinService(CabinLayout.Service.J);
                    world.journeyControl().waitForCheckin(journey);
                    messages.add("Transport flight # " + transportFlight.getId() + " created, journey # " + journey.getId() + " booked to the transport flight");
                }
            }

            return new BookMissionResponse("success", messages);
        });
    }

    // The mission of the page has to be booked not later than this from now, and the start may be earlier than 30 minutes from now by the difference of the clock of the browser
    // and the clock of the server
    static final int START_HORIZON_DAYS = 7;
    static final int START_CLOCK_TOLERANCE_SECONDS = 5 * 60;

    // the world time (seconds) of the first departure for the start time of the request (UTC milliseconds)
    private static int firstDepartureTime(final long startTimeMillis) {
        return (int) (startTimeMillis / 1000);
    }

    // the earliest start which the aircraft allows (UTC milliseconds): 30 minutes after the last landing of its booked missions, 0 for an aircraft which has none
    static long aircraftEarliestStart(final BusyBirdsMissionControl.AircraftOutlook outlook) {
        return outlook.hasOpenMissions() ? outlook.freeFromWorldTime() * 1000L + 30 * 60 * 1000L : 0;
    }

    // why the user cannot book on the aircraft, null if the aircraft is free or goes on with the missions of this user
    static String aircraftProblem(final BusyBirdsMissionControl.AircraftOutlook outlook, final int userId) {
        if (outlook.airport() == null) {
            return "The aircraft is not at an airport.";
        }
        if (!outlook.isOpenTo(userId)) {
            return "The aircraft has missions of another pilot.";
        }
        return null;
    }

    /**
     * Whether the mission [start, end] (UTC milliseconds, the first departure and the last arrival of the plan) can be booked: it starts not earlier than 30 minutes from now (with the
     * tolerance of the clocks), not later than 7 days from now, and there are not less than 30 minutes between it and every flight of the pilot which is not over (the same rule as
     * at ShadowJet, ShadowJetBooking.checkSlot, for the whole block of the legs).
     */
    static StartSlotDto checkStartSlot(final World world, final int userId, final long startMillis, final long endMillis, final int nowWorldTime) {
        return checkStartSlot(world, userId, startMillis, endMillis, nowWorldTime, 0);
    }

    /** The same with the earliest start which the aircraft allows (0: it has no booked missions, see aircraftEarliestStart). */
    static StartSlotDto checkStartSlot(final World world, final int userId, final long startMillis, final long endMillis, final int nowWorldTime, final long aircraftEarliestStart) {
        final ShadowJetController.SlotDto slot = ShadowJetController.checkSlot(world, userId, startMillis, endMillis, nowWorldTime - START_CLOCK_TOLERANCE_SECONDS);
        final long nowMillis = nowWorldTime * 1000L;
        final long latestStart = nowMillis + START_HORIZON_DAYS * 24 * 3600 * 1000L;
        final boolean tooLate = startMillis > latestStart;
        final boolean aircraftBusy = startMillis < aircraftEarliestStart;
        return new StartSlotDto(slot.isFree() && !tooLate && !aircraftBusy, slot.isTooSoon(), tooLate, aircraftBusy, aircraftEarliestStart, nowMillis + 30 * 60 * 1000L, latestStart,
                slot.getConflict());
    }

    static List<String> startSlotProblems(final StartSlotDto slot) {
        final List<String> problems = new ArrayList<>();
        if (slot.isTooSoon()) {
            problems.add("The mission starts too soon: the earliest start is 30 minutes from now.");
        }
        if (slot.isTooLate()) {
            problems.add("The mission starts too late: the latest start is " + START_HORIZON_DAYS + " days from now.");
        }
        if (slot.isAircraftBusy()) {
            problems.add("The aircraft is busy: its booked missions end later, the earliest start for it is "
                    + LocalDateTime.ofEpochSecond(slot.getAircraftEarliestStart() / 1000, 0, ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " UTC.");
        }
        final ShadowJetController.ConflictDto conflict = slot.getConflict();
        if (conflict != null) {
            final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            problems.add("The mission conflicts with your flight " + conflict.getFrom() + " - " + conflict.getTo() + ", "
                    + LocalDateTime.ofEpochSecond(conflict.getDepartureTime() / 1000, 0, ZoneOffset.UTC).format(format) + " - "
                    + LocalDateTime.ofEpochSecond(conflict.getArrivalTime() / 1000, 0, ZoneOffset.UTC).format(format)
                    + " UTC: there must be at least 30 minutes between the flights.");
        }
        return problems;
    }

    static final double MAX_PAUSE_HOURS = 48;

    /**
     * The pauses before the legs of a plan from the parameter "pauses": hours separated by commas, the first one is the pause before the second leg, the next one before the
     * third leg ("0.5,3": half an hour before the second leg, three hours before the third one). Without it every pause is the default one, 30 minutes.
     */
    static List<Double> pauses(final String pauses) {
        if (pauses == null || pauses.isBlank()) {
            return List.of();
        }
        final List<Double> hoursList = new ArrayList<>();
        for (final String part : pauses.split(",")) {
            final double hours;
            try {
                hours = Double.parseDouble(part.trim());
            } catch (final NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the pause is not a number: '" + part + "'");
            }
            if (!(hours >= 0 && hours <= MAX_PAUSE_HOURS)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the pause must be from 0 to " + (int) MAX_PAUSE_HOURS + " hours: " + hours);
            }
            hoursList.add(hours);
        }
        return hoursList;
    }

    // The booking needs a pilot (person) of the user and nothing else: not the state, not the place. Returns the reason of the refusal, null if the booking is allowed.
    static String noPilotProblem(final World world, final int userId) {
        final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
        if (personId <= 0 || world.persons().byId(personId).isEmpty()) {
            return "You have no pilot (person), so no flight can be booked.";
        }
        return null;
    }

    @Data
    @AllArgsConstructor
    public static class PilotTermsDto {
        private String aircraftType;
        // the qualifications of the pilot on the type
        private int experienceLevel;
        private boolean typeCurrent;
        private boolean nightFlying;
        private boolean canCarryPassengers;
        // the pay for the flown hours: the base hourly rate of the type, the deductions, the coefficient in percents (10 to 100) and the rate which the pilot gets
        private int hourlyRate;
        private List<DeductionDto> flightPayDeductions;
        private int flightPayCoefficientPercent;
        private double effectiveHourlyRate;
        // the share of the income of the mission: the base percent of the experience level, the deductions, the coefficient and the percent which the pilot gets
        private double incomeSharePercent;
        private List<DeductionDto> incomeShareDeductions;
        private int incomeShareCoefficientPercent;
        private double effectiveIncomeSharePercent;
    }

    @Data
    @AllArgsConstructor
    public static class DeductionDto {
        // NoTypeCurrency, NoNightCurrency, CannotCarryPassengers, Manual
        private String code;
        private int percent;

        public static DeductionDto from(final PilotPay.Deduction deduction, final PilotPayRates rates) {
            return new DeductionDto(deduction.name(), rates.deductionPercent(deduction));
        }
    }

    @Data
    @AllArgsConstructor
    public static class CityDto {
        private String id;
        private String name;
    }

    /**
     * Whether a start of a mission can be booked (checkStartSlot): free when it is not too soon, not too late and has no conflict with the flights of the pilot.
     * The times are UTC milliseconds.
     */
    @Data
    @AllArgsConstructor
    public static class StartSlotDto {
        private boolean free;
        private boolean tooSoon;
        private boolean tooLate;
        // the aircraft is not free yet at the start: its booked missions end later (aircraftEarliestStart, UTC milliseconds, 0 for an aircraft which has none)
        private boolean aircraftBusy;
        private long aircraftEarliestStart;
        // the earliest and the latest start which the server accepts now: 30 minutes and 7 days from now
        private long earliestStart;
        private long latestStart;
        // the flight of the pilot which is too close (the first one), null if there is none
        private ShadowJetController.ConflictDto conflict;
    }

    @Data
    @AllArgsConstructor
    public static class MissionDto {
        private String id;
        private String fromCityId;
        private String fromCityName;
        private String toCityId;
        private String toCityName;
        private String pCs;
        private int pax;
        private int distance;
        // exact if payExact, otherwise a rough number (the exact price appears when a pilot gets a quote)
        private int pay;
        private boolean payExact;
    }

    @Data
    @AllArgsConstructor
    public static class AircraftDto {
        private String id;
        private String typeCode;
        private String regNo;
        // the airport where the aircraft is when a new mission begins: where it stands now, or where its booked missions end
        private String locationAirportIcao;
        // the earliest start of a new mission on the aircraft, UTC milliseconds: 0 for a free aircraft, 30 minutes after the last landing of its booked missions otherwise
        private long earliestStart;
        // the seats for a mission (the business class of the cabin layout of the type): a mission with a bigger group does not fit
        private int seats;
    }

    @Data
    @AllArgsConstructor
    public static class BuildPlansResponse {
        private List<PlanDto> plans;
        // the exact price of the journey, set by this quote
        private int price;
    }

    @Data
    @AllArgsConstructor
    public static class PlanDto {
        private String status;
        private String id;
        private String description;
        private List<LegDto> legs;
        private SalaryDto salary; // total for the pilot, the sum of the legs
        private List<String> messages;
        // the first departure and the last arrival of the plan, UTC milliseconds (null for a failure)
        private Long startTime;
        private Long endTime;
        // whether the time of the plan is free in the roster of the user: only for a request with a start time
        private StartSlotDto slot;

        public static PlanDto failure(final String message) {
            return new PlanDto("failure", null, null, null, null, List.of(message), null, null, null);
        }

        public static PlanDto from(BusyBirdsMissionControl.MissionPlan plan) {
            if (plan.getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                return new PlanDto("failure", null, null, null, null, plan.getMessages(), null, null, null);
            }

            final List<LegDto> legDtos = plan.getLegs().stream().map(leg -> new LegDto(
                    leg.getType().name(),
                    leg.getFromAirport().getIcao(),
                    leg.getToAirport().getIcao(),
                    leg.getPax(),
                    leg.getDistance(),
                    Time.toLdt(leg.getPlannedDepTime()).toLocalDate().toString(),
                    JavaTime.toHhmm(Time.toLdt(leg.getPlannedDepTime()).toLocalTime()),
                    JavaTime.toHhmm(Time.toLdt(leg.getPlannedArrTime()).toLocalTime()),
                    JavaTime.toHhmm(leg.getPlannedDuration()),
                    SalaryDto.forLeg(leg)
            )).toList();

            final List<BusyBirdsMissionControl.Leg> legs = plan.getLegs();
            return new PlanDto("success", plan.getId(), plan.getDescription(), legDtos, SalaryDto.from(plan.getTotalSalary()), null,
                    legs.get(0).getPlannedDepTime() * 1000L, legs.get(legs.size() - 1).getPlannedArrTime() * 1000L, null);
        }
    }

    @Data
    @AllArgsConstructor
    private static class LegDto {
        private final String type; // reposition, revenue
        private final String fromAirportIcao;
        private final String toAirportIcao;
        private final int pax;
        private final int distance;
        private final String dof;
        private final String depTime;
        private final String arrTime;
        private final String duration;
        private final SalaryDto salary; // what the pilot gets for the leg
    }

    // Money is integer. total is flightPay + incomeShare.
    @Data
    @AllArgsConstructor
    public static class SalaryDto {
        private final int flightPay; // the hourly rate of the pilot on the aircraft * planned duration
        private final int incomeShare; // share of the income of the mission, revenue leg only
        private final int total;
        // What the pay of a leg is made of, for the page to explain it (null in the total of a plan):
        // the hours of the leg (two decimals) and the hourly rate of the pilot, that is the base rate of the type with the deductions
        private final Double durationHours;
        private final Double hourlyRate;
        // the income of the mission which the share is taken from and the percent of it which the pilot gets, null for a leg without an income
        private final Integer baseIncome;
        private final Double incomeSharePercent;

        public static SalaryDto from(final BusyBirdsPilotPay.Salary salary) {
            return new SalaryDto(salary.flightPay(), salary.incomeShare(), salary.total(), null, null, null, null);
        }

        public static SalaryDto forLeg(final BusyBirdsMissionControl.Leg leg) {
            final BusyBirdsPilotPay.Salary salary = leg.getSalary();
            final double durationHours = Math.round(leg.getPaidDuration().toSeconds() / 3600.0 * 100.0) / 100.0;
            final double hourlyRate = leg.getHourlyRate() * PilotPay.flightPayCoefficientPercent(leg.getPayParams()) / 100.0;
            final boolean hasIncome = leg.getBaseIncome() > 0;
            return new SalaryDto(salary.flightPay(), salary.incomeShare(), salary.total(), durationHours, hourlyRate,
                    hasIncome ? leg.getBaseIncome() : null,
                    hasIncome ? PilotPay.effectiveIncomeSharePercent(leg.getPayParams()) : null);
        }
    }

    @Data
    @AllArgsConstructor
    public static class BookMissionResponse {
        private String status;
        private List<String> messages;
    }
}
