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
     * Whether the user may book BusyBirds flights at all: the pilot of the user is Idle at an airport or in a city. Whether the pilot stands where the aircraft
     * stands is checked when an aircraft is booked.
     */
    @GetMapping("/booking-status")
    public BookingStatusDto getBookingStatus(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
            final String problem = pilotBookingProblem(world, personId, 0);
            return new BookingStatusDto(problem == null, problem);
        });
    }

    @GetMapping("/mission/to-book")
    public List<MissionDto> getMissionsToBook(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            return world.busyBirdsMissionControl().getMissionsToBook().stream()
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

            return world.aircrafts()
                    .byAircraftOperatorIdAndIdleAndParkedAtAirport(World25.BusyBirdsOperatorId)
                    .map(a -> new AircraftDto(
                            Id.encode(a.getId()),
                            world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                            a.getRegNo(),
                            world.airports().byId(a.getLocationAirportId()).orElseThrow().getIcao()))
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
                                         @RequestParam(name = "turnaroundTime", required = false, defaultValue = "1") double turnaroundTime,
                                         @RequestParam(name = "intervals", required = false) String intervals,
                                         @RequestParam(name = "ferryBackToBase", required = false, defaultValue = "true") boolean ferryBackToBase) {
        // a quote changes the world: the price of the journey is set
        return worldBean.modifySync(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            Aircrafts.Aircraft aircraft = world.aircrafts().byId(Id.decode(aircraftId)).orElseThrow();
            Journeys.Journey journey = world.journeys().byId(Id.decode(missionId)).orElseThrow();

            final int price = world.busyBirdsMissionControl().quote(journey);

            List<BusyBirdsMissionControl.MissionPlan> plans = world.busyBirdsMissionControl().buildPlans(journey, aircraft, legGaps(intervals, turnaroundTime), ferryBackToBase,
                    payParamsOfUser(world, userId, aircraft));

            return new BuildPlansResponse(plans.stream().map(BusyBirdsController.PlanDto::from).toList(), price);
        });
    }

    @PutMapping("/mission/book")
    public BookMissionResponse bookMission(@RequestAttribute("userId") int userId,
                                           @RequestParam(name = "missionId") String missionId,
                                           @RequestParam(name = "aircraftId") String aircraftId,
                                           @RequestParam(name = "turnaroundTime", required = false, defaultValue = "1") double turnaroundTime,
                                           @RequestParam(name = "intervals", required = false) String intervals,
                                           @RequestParam(name = "ferryBackToBase", required = false, defaultValue = "true") boolean ferryBackToBase,
                                           @RequestParam(name = "planId") String planId) {
        return worldBean.modifySync(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            Aircrafts.Aircraft aircraft = world.aircrafts().byId(Id.decode(aircraftId)).orElseThrow();
            Journeys.Journey journey = world.journeys().byId(Id.decode(missionId)).orElseThrow();

            final String pilotProblem = pilotBookingProblem(world, userId, aircraft);
            if (pilotProblem != null) {
                return new BookMissionResponse("failure", Collections.singletonList(pilotProblem));
            }

            // the parameters of the pay are the currency and the level of the pilot on the type at the moment of the booking, the plans and the pilot contracts have them
            final PilotPayParams payParams = payParamsOfUser(world, userId, aircraft);
            List<BusyBirdsMissionControl.MissionPlan> plans = world.busyBirdsMissionControl().buildPlans(journey, aircraft, legGaps(intervals, turnaroundTime), ferryBackToBase, payParams);

            Optional<BusyBirdsMissionControl.MissionPlan> plan = plans.stream().filter(p -> planId.equals(p.getId())).findFirst();
            if (plan.isEmpty()) {
                return new BookMissionResponse("failure", Collections.singletonList("unable to find plan by planId"));
            }

            if (plan.get().getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                return new BookMissionResponse("failure", plan.get().getMessages());
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
                world.busyBirdsMissionControl().createContract(flight, personId, revenue ? journey : null, payParams);

                // all busyBirds aircraft are expected to have J-layout only
                if (leg.getType() == BusyBirdsMissionControl.Leg.Type.Revenue) {
                    TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(flight);
                    journey.setTransportFlight1Id(transportFlight.getId());
                    world.transportFlightControl().obtainFlightTickets(transportFlight, journey.getGroupSize(), CabinLayout.Service.J);
                    journey.setBookedCabinService(CabinLayout.Service.J);
                    world.journeyControl().waitForCheckin(journey);
                    messages.add("Transport flight # " + transportFlight.getId() + " created, journey # " + journey.getId() + " booked to the transport flight");
                }
            }

            return new BookMissionResponse("success", messages);
        });
    }

    @PutMapping("/ferry/book")
    public BookMissionResponse bookFerryFlight(@RequestAttribute("userId") int userId,
                                               @RequestParam(name = "aircraftId") String aircraftId,
                                               @RequestParam(name = "destinationIcao") String destinationIcao) {
        return worldBean.modifySync(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            Aircrafts.Aircraft aircraft = world.aircrafts().byId(Id.decode(aircraftId)).orElseThrow();

            final String pilotProblem = pilotBookingProblem(world, userId, aircraft);
            if (pilotProblem != null) {
                return new BookMissionResponse("failure", Collections.singletonList(pilotProblem));
            }

            if (aircraft.getLocationStatus() != Aircrafts.LocationStatus.ParkedAtAirport) {
                return new BookMissionResponse("failure", Collections.singletonList("aircraft is not parked at airport"));
            }
            if (aircraft.getOperationalStatus() != Aircrafts.OperationalStatus.Idle) {
                return new BookMissionResponse("failure", Collections.singletonList("aircraft is not idle"));
            }

            Optional<Airports.Airport> destinationAirport = world.airports().byIcao(destinationIcao.trim().toUpperCase());
            if (destinationAirport.isEmpty()) {
                return new BookMissionResponse("failure", Collections.singletonList("unable to find destination airport " + destinationIcao));
            }

            Airports.Airport departureAirport = world.airports().byId(aircraft.getLocationAirportId()).orElseThrow();
            if (departureAirport.getId() == destinationAirport.get().getId()) {
                return new BookMissionResponse("failure", Collections.singletonList("aircraft is already at destination airport"));
            }

            int departureTime = world.getWorldTime() + 30 * Time.ONE_MINUTE;

            FlightMissions.Mission flight = FlightMissionHelper.scheduleDispatchedMission(world, aircraft, departureAirport, destinationAirport.get(), departureTime);
            flight.setCharacterMode(FlightMissions.CharacterMode.PC);
            flight.setUserId(userId);

            return new BookMissionResponse("success", Collections.singletonList(
                    "Flight mission # " + flight.getId() + " scheduled, departure time: " + TimeTools.ts(flight.getPlannedDepartureWorldTime())));
        });
    }

    static final double MAX_LEG_GAP_HOURS = 48;

    /**
     * The gaps before the legs of a plan: the parameter "intervals" is the hours before every leg separated by commas ("0.5,1,3": the first leg departs in
     * half an hour from now, the second one an hour after the arrival of the first one and so on). Without it the first gap is the default one (30 minutes)
     * and the others are the old "turnaroundTime".
     */
    static List<Double> legGaps(final String intervals, final double turnaroundTime) {
        if (intervals == null || intervals.isBlank()) {
            return List.of(BusyBirdsMissionControl.FIRST_LEG_DEPARTURE_GAP_MINUTES / 60.0, turnaroundTime, turnaroundTime);
        }
        final List<Double> gaps = new ArrayList<>();
        for (final String part : intervals.split(",")) {
            final double hours;
            try {
                hours = Double.parseDouble(part.trim());
            } catch (final NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the interval is not a number: '" + part + "'");
            }
            if (!(hours >= 0 && hours <= MAX_LEG_GAP_HOURS)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the interval must be from 0 to " + (int) MAX_LEG_GAP_HOURS + " hours: " + hours);
            }
            gaps.add(hours);
        }
        return gaps;
    }

    // Nobody flies without a pilot: the user has to have a person who is Idle at the airport where the aircraft stands, or in a city which that airport is
    // linked to (the pilot travels to the airport before the start). Returns the reason of the refusal, null if the booking is allowed.
    private static String pilotBookingProblem(final World world, final int userId, final Aircrafts.Aircraft aircraft) {
        final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
        return pilotBookingProblem(world, personId, aircraft.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport ? aircraft.getLocationAirportId() : 0);
    }

    static String pilotBookingProblem(final World world, final int personId, final int aircraftAirportId) {
        final Optional<Persons.Person> person = personId > 0 ? world.persons().byId(personId) : Optional.empty();
        if (person.isEmpty()) {
            return "You have no pilot (person), so no flight can be booked.";
        }
        if (person.get().getState() != Persons.State.Idle) {
            return "Your pilot is busy now (" + person.get().getState() + "), so no flight can be booked.";
        }
        final Persons.LocationType locationType = person.get().getLocationType();
        if (locationType != Persons.LocationType.Airport && locationType != Persons.LocationType.City) {
            return "Your pilot is not at an airport or in a city, so no flight can be booked.";
        }
        if (aircraftAirportId > 0 && world.personControl().bookingAirports(person.get()).stream().noneMatch(a -> a.getId() == aircraftAirportId)) {
            final String aircraftAirport = world.airports().getIcao(aircraftAirportId).orElse("?");
            if (locationType == Persons.LocationType.City) {
                final String pilotCity = world.cities().byId(person.get().getLocationId()).map(c -> c.getName()).orElse("?");
                return "Your pilot is in " + pilotCity + ", which is not linked to " + aircraftAirport + " where the aircraft is, the flight cannot be booked.";
            }
            final String pilotAirport = world.airports().getIcao(person.get().getLocationId()).orElse("?");
            return "Your pilot is at " + pilotAirport + " but the aircraft is at " + aircraftAirport + ", the flight cannot be booked.";
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
    public static class BookingStatusDto {
        private boolean canBook;
        private String problem;
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
        private String locationAirportIcao;
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

        public static PlanDto from(BusyBirdsMissionControl.MissionPlan plan) {
            if (plan.getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                return new PlanDto("failure", null, null, null, null, plan.getMessages());
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

            return new PlanDto("success", plan.getId(), plan.getDescription(), legDtos, SalaryDto.from(plan.getTotalSalary()), null);
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
