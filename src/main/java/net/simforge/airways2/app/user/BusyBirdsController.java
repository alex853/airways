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
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.BusyBirdsMissionControl;
import net.simforge.airways2.world.processors.BusyBirdsPilotPay;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.JavaTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
     * Whether the user may book BusyBirds flights at all: the pilot of the user is Idle at an airport. Whether the pilot stands where the aircraft
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

    @GetMapping("/mission/build-plans")
    public BuildPlansResponse buildPlans(@RequestAttribute("userId") int userId,
                                         @RequestParam(name = "missionId") String missionId,
                                         @RequestParam(name = "aircraftId") String aircraftId,
                                         @RequestParam(name = "turnaroundTime", required = false, defaultValue = "1") int turnaroundTime,
                                         @RequestParam(name = "ferryBackToBase", required = false, defaultValue = "true") boolean ferryBackToBase) {
        // a quote changes the world: the price of the journey is set
        return worldBean.modifySync(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            Aircrafts.Aircraft aircraft = world.aircrafts().byId(Id.decode(aircraftId)).orElseThrow();
            Journeys.Journey journey = world.journeys().byId(Id.decode(missionId)).orElseThrow();

            final int price = world.busyBirdsMissionControl().quote(journey);

            List<BusyBirdsMissionControl.MissionPlan> plans = world.busyBirdsMissionControl().buildPlans(journey, aircraft, turnaroundTime, ferryBackToBase);

            return new BuildPlansResponse(plans.stream().map(BusyBirdsController.PlanDto::from).toList(), price);
        });
    }

    @PutMapping("/mission/book")
    public BookMissionResponse bookMission(@RequestAttribute("userId") int userId,
                                           @RequestParam(name = "missionId") String missionId,
                                           @RequestParam(name = "aircraftId") String aircraftId,
                                           @RequestParam(name = "turnaroundTime", required = false, defaultValue = "1") int turnaroundTime,
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

            List<BusyBirdsMissionControl.MissionPlan> plans = world.busyBirdsMissionControl().buildPlans(journey, aircraft, turnaroundTime, ferryBackToBase);

            Optional<BusyBirdsMissionControl.MissionPlan> plan = plans.stream().filter(p -> planId.equals(p.getId())).findFirst();
            if (plan.isEmpty()) {
                return new BookMissionResponse("failure", Collections.singletonList("unable to find plan by planId"));
            }

            if (plan.get().getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                return new BookMissionResponse("failure", plan.get().getMessages());
            }

            // the pilot contracts of the flights: the parameters are the currency and the level of the pilot on the type at the moment of the booking
            final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElseThrow();
            final String aircraftTypeIcao = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao();
            final PilotPayParams payParams = PilotController.payParams(world, personId, aircraftTypeIcao);

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

    // Nobody flies without a pilot: the user has to have a person who is Idle at the airport where the aircraft stands. Returns the reason of
    // the refusal, null if the booking is allowed.
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
        if (person.get().getLocationType() != Persons.LocationType.Airport) {
            return "Your pilot is not at an airport, so no flight can be booked.";
        }
        if (aircraftAirportId > 0 && person.get().getLocationId() != aircraftAirportId) {
            final String pilotAirport = world.airports().getIcao(person.get().getLocationId()).orElse("?");
            final String aircraftAirport = world.airports().getIcao(aircraftAirportId).orElse("?");
            return "Your pilot is at " + pilotAirport + " but the aircraft is at " + aircraftAirport + ", the flight cannot be booked.";
        }
        return null;
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
                    SalaryDto.from(leg.getSalary())
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
        private final int flightPay; // base hourly rate of the aircraft type * planned duration
        private final int incomeShare; // share of the income of the mission, revenue leg only
        private final int total;

        public static SalaryDto from(final BusyBirdsPilotPay.Salary salary) {
            return new SalaryDto(salary.flightPay(), salary.incomeShare(), salary.total());
        }
    }

    @Data
    @AllArgsConstructor
    public static class BookMissionResponse {
        private String status;
        private List<String> messages;
    }
}
