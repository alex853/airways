package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.computations.FlightTimeline;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.AircraftPerformanceData;
import net.simforge.airways2.world.computations.PilotPay;
import net.simforge.airways2.world.computations.PilotPayRates;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.computations.SimpleFlight;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BusyBirdsMissionControl {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsMissionControl.class);

    private final World world;

    public BusyBirdsMissionControl(final World world) {
        this.world = world;
    }

    public AircraftOperators.AircraftOperator getBusyBirdsOperator() {
        return world.aircraftOperators().byId(World25.BusyBirdsOperatorId).orElseThrow();
    }

    public List<Mission> getMissionsToBook() {
        List<Journeys.Journey> allJourneys = world.journeys()
                .filter(world.journeys().byStatus(Journeys.Status.LookingForTickets))
                .filter(j -> j.getLocationCityId() > 0) // journey located nowhere is not able to be booked
                .filter(j -> j.getPreferredCabinService() == CabinLayout.Service.F
                        || j.getPreferredCabinService() == CabinLayout.Service.J)
                .toList();

        return allJourneys.stream().map(j -> {
            Cities.City fromCity = world.cities().byId(j.getLocationCityId()).orElseThrow();
            Cities.City toCity = world.cities().byId(j.getToCityId()).orElseThrow();

            int distance = (int) Geo.distance(fromCity.getCoords(), toCity.getCoords());
            final boolean payExact = j.getPrice() > 0;
            int pay = payExact ? j.getPrice() : roughPay(calcIncome(j, fromCity, toCity, distance));

            return new Mission(
                    j,
                    null,
                    distance,
                    pay,
                    payExact);

        }).toList();
    }

    /**
     * The number for the list of missions when the journey has no price yet: the income made a bit smaller and rough, down to thousands (to hundreds
     * below a thousand). The exact price appears when a pilot gets a quote.
     */
    static int roughPay(final int pay) {
        return pay >= 1000 ? pay / 1000 * 1000 : pay / 100 * 100;
    }

    /**
     * The pilot gets a quote for the journey: if the journey has no price yet, the price which the journey pays is set to the income by the formula
     * multiplied by a random factor from {@link #PRICE_FACTOR_MIN} to {@link #PRICE_FACTOR_MAX}. The price stays in the journey, the next quotes do not
     * change it. The plans are built with this price. The price goes back to zero when the trip of the journey is over (JourneyControl.switchToReturnTrip).
     *
     * @return the price of the journey
     */
    public int quote(final Journeys.Journey journey) {
        return quote(journey, PRICE_FACTOR_MIN + (PRICE_FACTOR_MAX - PRICE_FACTOR_MIN) * ThreadLocalRandom.current().nextDouble());
    }

    // the factor is a parameter to check the price in tests
    int quote(final Journeys.Journey journey, final double factor) {
        if (journey.getPrice() == 0) {
            final int price = (int) Math.round(calcIncome(journey) * factor);
            journey.setPrice(price);
            log.info("j/y #{} - price {} is set for BusyBirds quote (factor {})", journey.getId(), price, String.format("%.3f", factor));
        }
        return journey.getPrice();
    }

    // The gap between now and the departure of the first leg of a plan, before the departure time is aligned to 5 minutes. It is half an hour for the
    // tests for now, later the pilot is going to choose (ASAP or later).
    public static final int FIRST_LEG_DEPARTURE_GAP_MINUTES = 30;

    public static final double PRICE_FACTOR_MIN = 0.95;
    public static final double PRICE_FACTOR_MAX = 1.05;

    // The income of the mission, the formula is a rough one and is going to be reworked.
    private static int calcIncome(final Journeys.Journey journey, final Cities.City fromCity, final Cities.City toCity, final int distance) {
        return (int) (((distance / 400.0) * 7000.0 + 2000.0)
                * (1 + Tools.lastDigit(fromCity.getId())*0.01)
                * (1 + Tools.lastDigit(toCity.getId())*0.01)
                * (1 + Tools.lastDigit(journey.getId())*0.01));
    }

    private int calcIncome(final Journeys.Journey journey) {
        if (journey.getPrice() > 0) {
            return journey.getPrice();
        }
        final Cities.City fromCity = world.cities().byId(journey.getLocationCityId()).orElseThrow();
        final Cities.City toCity = world.cities().byId(journey.getToCityId()).orElseThrow();
        return calcIncome(journey, fromCity, toCity, (int) Geo.distance(fromCity.getCoords(), toCity.getCoords()));
    }

    /**
     * The contract of the pilot for a flight of a booked plan, see docs/flight-contracts.md. It keeps the base flight pay for the planned duration by the hourly
     * rate of the type and the parameters (the currency and the experience level of the pilot), which the caller takes at the booking. The income of the flight is not in
     * the contract: it is the income of the transport flight of the mission (the revenue leg has the price of its journey there, a reposition leg has none).
     */
    public PilotContracts.Contract createContract(final FlightMissions.Mission mission,
                                                  final int personId,
                                                  final PilotPayParams params) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        final String aircraftTypeIcao = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao();
        final int plannedDurationSeconds = mission.getPlannedArrivalWorldTime() - mission.getPlannedDepartureWorldTime();

        final int baseFlightPay = BusyBirdsPilotPay.baseFlightPay(aircraftTypeIcao, plannedDurationSeconds);

        final PilotContracts.Contract contract = world.pilotContracts().create(
                mission.getId(), aircraft.getAircraftOperatorId(), personId, baseFlightPay, params);
        log.info("p/s #{}, f/m #{} - pilot contract #{}: base flight pay {}, params {}",
                personId, mission.getId(), contract.getId(), baseFlightPay, params);
        return contract;
    }

    /**
     * The pilot of a finished flight of BusyBirds or of ShadowJet (the rates of each, OperatorPay) gets paid by the contract of the flight: the flight pay and the income share which PilotPay calculates from
     * the base numbers and the parameters of the contract. The operator pays the pilot by two transfers from its account to the account of the person: the
     * flight pay and the income share (if an account does not exist, nothing moves, see BankControl.transferIfAccountsExist). It is written to the log too.
     * <p>
     * A flight without a contract (a free ferry flight, a flight booked before the contracts, a manual dispatch) pays nothing.
     *
     * @return what the pilot has got, empty if there is nothing to pay: the operator of the flight does not pay (it is neither BusyBirds nor ShadowJet) or the flight has no contract
     */
    public Optional<BusyBirdsPilotPay.Salary> payPilot(final FlightMissions.Mission mission) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        final Optional<PilotPayRates> rates = OperatorPay.ratesOf(aircraft.getAircraftOperatorId());
        if (rates.isEmpty()) {
            return Optional.empty();
        }

        final Optional<PilotContracts.Contract> contract = world.pilotContracts().byFlightMissionId(mission.getId());
        if (contract.isPresent()) {
            // the income share is by the income of the transport flight of the mission: what the journeys really paid who boarded (TransportFlightExtras)
            final int income = world.transportFlightControl().getIncomeOfMission(mission);
            final BusyBirdsPilotPay.Salary salary = new BusyBirdsPilotPay.Salary(PilotPay.flightPay(contract.get(), rates.get()), PilotPay.incomeShare(income, contract.get(), rates.get()));
            log.info("p/s #{}, f/m #{} - pilot contract #{}, pilot gets paid {} (flight pay {}, income share {}), params {}",
                    contract.get().getPersonId(),
                    mission.getId(),
                    contract.get().getId(),
                    salary.total(),
                    salary.flightPay(),
                    salary.incomeShare(),
                    contract.get().getPayParams());

            final int operatorAccountId = world.bankControl().operatorAccountId(contract.get().getOperatorId());
            final int personAccountId = world.persons().byId(contract.get().getPersonId()).map(Persons.Person::getBankAccountId).orElse(0);
            final String reason = "f/m #" + mission.getId() + ", pilot contract #" + contract.get().getId();
            world.bankControl().transferIfAccountsExist(operatorAccountId, personAccountId, salary.flightPay(), BankTransactions.Type.PilotFlightPay, reason);
            world.bankControl().transferIfAccountsExist(operatorAccountId, personAccountId, salary.incomeShare(), BankTransactions.Type.PilotIncomeShare, reason);
            return Optional.of(salary);
        }

        // no contract: a free ferry flight, a flight which was booked before the contracts, a manual dispatch
        log.info("f/m #{} - flight finished, it has no pilot contract, nobody is paid", mission.getId());
        return Optional.empty();
    }

    /**
     * The plans of a mission for an aircraft. The pay of every leg is what the pilot will get: it is calculated by the parameters of the pilot on the type of
     * the aircraft (the currency and the experience level, see PilotPay), the same as the pilot contract is going to be created with.
     */
    public List<BusyBirdsMissionControl.MissionPlan> buildPlans(Journeys.Journey journey, Aircrafts.Aircraft aircraft, double turnaroundTimeHours, boolean ferryBackToBase, PilotPayParams payParams) {
        return buildPlans(journey, aircraft, List.of(FIRST_LEG_DEPARTURE_GAP_MINUTES / 60.0, turnaroundTimeHours, turnaroundTimeHours), ferryBackToBase, payParams);
    }

    /**
     * The same with a gap for every leg of the plan: the gap of the first leg is the time from now to its departure, the gap of any other leg is the turnaround
     * time between the arrival of the previous leg and its departure (hours). A leg which has no gap in the list gets the default: 30 minutes for the first
     * leg, one hour for the others.
     */
    public List<BusyBirdsMissionControl.MissionPlan> buildPlans(Journeys.Journey journey, Aircrafts.Aircraft aircraft, List<Double> legGapHours, boolean ferryBackToBase, PilotPayParams payParams) {
        List<String> messages = new ArrayList<>();

        AircraftOperators.AircraftOperator busyBirdsOperator = getBusyBirdsOperator();

        // todo ak2 take into account aircraft max range
        // todo ak2 check aircraft seats vs journey size
        if (aircraft.getLocationStatus() != Aircrafts.LocationStatus.ParkedAtAirport) {
            messages.add("aircraft is not parked at airport");
        }
        if (aircraft.getOperationalStatus() != Aircrafts.OperationalStatus.Idle) {
            messages.add("aircraft is not idle");
        }

        List<Airports.Airport> fromAirports = listAirports(busyBirdsOperator, journey.getLocationCityId());
        List<Airports.Airport> toAirports = listAirports(busyBirdsOperator, journey.getToCityId());

        if (fromAirports.isEmpty()) {
            messages.add("unable to find suitable 'from' airport");
        }

        if (toAirports.isEmpty()) {
            messages.add("unable to find suitable 'to' airport");
        }

        List<MissionPlan> plans = new ArrayList<>();

        for (Airports.Airport fromAirport : fromAirports) {
            for (Airports.Airport toAirport : toAirports) {
                plans.add(_buildPlan(journey, busyBirdsOperator, aircraft, fromAirport, toAirport, messages, legGapHours, ferryBackToBase, payParams));
            }
        }

        plans.sort(Comparator.comparingInt(p -> p.totalDistance));

        return plans;
    }

    private MissionPlan _buildPlan(Journeys.Journey journey, AircraftOperators.AircraftOperator busyBirdsOperator, Aircrafts.Aircraft aircraft, Airports.Airport fromAirport, Airports.Airport toAirport, List<String> messages, List<Double> legGapHours, boolean ferryBackToBase, PilotPayParams payParams) {
        Airports.Airport baseAirport = findNearestBaseAirport(world, busyBirdsOperator, toAirport).orElseThrow();

        Airports.Airport locationAirport = world.airports().byId(aircraft.getLocationAirportId()).orElseThrow();
        boolean needFerryFlightToDepartureAirport = locationAirport.getId() != fromAirport.getId();
        boolean needFerryFlightToBaseAirport = toAirport.getId() != baseAirport.getId();

        AircraftTypes.AircraftType aircraftType = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow();
        AircraftPerformanceData performanceData = AircraftPerformanceData.getData(aircraftType.getIcao());

        final int missionIncome = calcIncome(journey);
        final String aircraftTypeIcao = aircraftType.getIcao();

        List<Leg> legs = new ArrayList<>();
        Leg leg;

        // every leg has its own gap: the first one is counted from now, the others from the arrival of the previous leg
        int nextDepTime = world.getWorldTime() + legGapSeconds(legGapHours, 0);

        if (needFerryFlightToDepartureAirport) {
            leg = addLeg(nextDepTime, legs, Leg.Type.Reposition, locationAirport, fromAirport, 0, performanceData, aircraftTypeIcao, 0, payParams);
            nextDepTime = leg.getPlannedArrTime() + legGapSeconds(legGapHours, legs.size());
        }

        leg = addLeg(nextDepTime, legs, Leg.Type.Revenue, fromAirport, toAirport, journey.getGroupSize(), performanceData, aircraftTypeIcao, missionIncome, payParams);
        nextDepTime = leg.getPlannedArrTime() + legGapSeconds(legGapHours, legs.size());

        if (needFerryFlightToBaseAirport && ferryBackToBase) {
            addLeg(nextDepTime, legs, Leg.Type.Reposition, toAirport, baseAirport, 0, performanceData, aircraftTypeIcao, 0, payParams);
        }

        int totalDistance = legs.stream().reduce(0, (total, l) -> total + l.distance, Integer::sum);

        return new MissionPlan(
                MissionPlan.Status.Success,
                fromAirport.getIcao() + "-" + toAirport.getIcao(),
                "From " + fromAirport.getIcao() + " to " + toAirport.getIcao() + ", " + legs.size() + " leg" + (legs.size() > 1 ? "s" : "") + ", " + totalDistance + " nm",
                legs,
                null);
    }

    // the gap before the leg with the index, in seconds; the leg which has no gap in the list gets 30 minutes (the first one) or an hour
    private static int legGapSeconds(final List<Double> legGapHours, final int legIndex) {
        if (legGapHours != null && legIndex < legGapHours.size() && legGapHours.get(legIndex) != null) {
            return (int) Math.round(legGapHours.get(legIndex) * Time.ONE_HOUR);
        }
        return legIndex == 0 ? FIRST_LEG_DEPARTURE_GAP_MINUTES * Time.ONE_MINUTE : Time.ONE_HOUR;
    }

    /**
     * The time which is paid for a leg: from blocks off to blocks on, that is the flying time of the leg and the taxi at both ends. It is the same as the
     * planned time of the flight mission, so the quote of the plan and the pilot contract of the booked flight give the same pay. The flight mission keeps its
     * planned times in minutes (the seconds are dropped, see DateMinutes), so the flying time here is cut to whole minutes too: with the seconds the quote
     * promised a few units more than the contract gave (953 and 950).
     */
    static Duration paidDuration(final long flyingSeconds) {
        return Duration.ofMinutes(flyingSeconds / 60).plusMinutes(FlightTimeline.BLOCKS_OFF_TO_TAKEOFF_MINUTES + FlightTimeline.LANDING_TO_BLOCKS_ON_MINUTES);
    }

    private Leg addLeg(int plannedTime, List<Leg> legs,
                       Leg.Type type,
                       Airports.Airport fromAirport, Airports.Airport toAirport,
                       int pax,
                       AircraftPerformanceData performanceData,
                       String aircraftTypeIcao,
                       int missionIncome,
                       PilotPayParams payParams) {
        SimpleFlight simpleFlight = SimpleFlight.forRoute(fromAirport.getCoords(), toAirport.getCoords(), performanceData);

        int plannedDepTime = Time.alignTo5mins(plannedTime);
        int plannedArrTime = plannedDepTime + (int) simpleFlight.getTotalTime().toSeconds();

        // what the pilot gets: the base numbers with the coefficients of the pilot; a reposition leg carries nobody, so it has no income
        final int baseFlightPay = BusyBirdsPilotPay.baseFlightPay(aircraftTypeIcao, (int) paidDuration(plannedArrTime - plannedDepTime).toSeconds());
        final int baseIncome = type == Leg.Type.Revenue ? missionIncome : 0;
        final BusyBirdsPilotPay.Salary salary = new BusyBirdsPilotPay.Salary(PilotPay.flightPay(baseFlightPay, payParams), PilotPay.incomeShare(baseIncome, payParams));
        Leg leg = new Leg(type, fromAirport, toAirport, (int) Geo.distance(fromAirport.getCoords(), toAirport.getCoords()), pax, plannedDepTime, plannedArrTime, salary,
                BusyBirdsPilotPay.baseHourlyRate(aircraftTypeIcao), baseIncome, payParams);
        legs.add(leg);

        return leg;
    }

    private List<Airports.Airport> listAirports(AircraftOperators.AircraftOperator aircraftOperator, int cityId) {
        List<Airports.Airport> airports = world.airport2city().allByCityId(cityId)
                .map(l -> world.airports().byId(l.getAirportId()).orElseThrow())
                .filter(a -> !a.isExcluded())
                .toList();

        if (airports.isEmpty()) {
            return Collections.emptyList();
        }

        final Optional<Airports.Airport> baseAirport = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, aircraftOperator, AirportFacilities.Type.BaseAirport))
                .findFirst();
        if (baseAirport.isPresent()) {
            return Collections.singletonList(baseAirport.get());
        }

        final List<Airports.Airport> preferredBusinessTerminals = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, aircraftOperator, AirportFacilities.Type.BusinessAviationTerminal))
                .toList();
        if (!preferredBusinessTerminals.isEmpty()) {
            return preferredBusinessTerminals;
        }

        final List<Airports.Airport> anyBusinessTerminals = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, AirportFacilities.Type.BusinessAviationTerminal))
                .toList();
        if (!anyBusinessTerminals.isEmpty()) {
            return anyBusinessTerminals;
        }

        return airports;
    }

    private Optional<Airports.Airport> findNearestBaseAirport(final World world, final AircraftOperators.AircraftOperator aircraftOperator, final Airports.Airport airport) {
        return world.airportFacilities().by(aircraftOperator, AirportFacilities.Type.BaseAirport)
                .map(f -> world.airports().byId(f.getAirportId()).orElseThrow())
                .min(Comparator.comparingDouble(a -> Geo.distance(a.getCoords(), airport.getCoords())));
    }

    public void checkUserHasAccessToBusyBirds(int userId) {
        // todo ak2 put here some check if user has access to BusyBirds OR throw exception!
    }


    @Data
    @AllArgsConstructor
    public static class Mission {
        private final Journeys.Journey journey;
        private final LocalDateTime validTill;
        private final int distance;
        // the price of the journey if it has one, otherwise the income made rough (see roughPay)
        private final int pay;
        private final boolean payExact;
    }

    @Getter
    public static class MissionPlan {
        private final Status status;
        private final String id;
        private final String description;
        private final List<Leg> legs;
        private final int totalDistance;
        private final BusyBirdsPilotPay.Salary totalSalary;
        private final List<String> messages;

        public MissionPlan(final Status status, String id, String description, final List<Leg> legs, final List<String> messages) {
            this.status = status;
            this.id = id;
            this.description = description;
            this.legs = legs != null ? Collections.unmodifiableList(legs) : null;
            this.totalDistance = legs != null ? legs.stream().mapToInt(Leg::getDistance).sum() : 0;
            this.totalSalary = legs != null ? legs.stream().map(Leg::getSalary).reduce(BusyBirdsPilotPay.Salary.ZERO, BusyBirdsPilotPay.Salary::plus) : BusyBirdsPilotPay.Salary.ZERO;
            this.messages = messages != null ? Collections.unmodifiableList(messages) : null;
        }

        public enum Status {
            Success,
            Failure,
        }
    }

    @Data
    public static class Leg {
        private final Type type;
        private final Airports.Airport fromAirport;
        private final Airports.Airport toAirport;
        private final int distance;
        private final int pax;
        private final int plannedDepTime;
        private final int plannedArrTime;
        // what the pilot gets for the leg
        private final BusyBirdsPilotPay.Salary salary;
        // what the pay is made of: the base hourly rate of the type, the income of the mission which the share is taken from (zero for a reposition leg) and
        // the parameters of the pilot
        private final int hourlyRate;
        private final int baseIncome;
        private final PilotPayParams payParams;

        public Leg(final Type type, final Airports.Airport fromAirport, final Airports.Airport toAirport, int distance, final int pax, int plannedDepTime, int plannedArrTime, final BusyBirdsPilotPay.Salary salary,
                   final int hourlyRate, final int baseIncome, final PilotPayParams payParams) {
            this.type = type;
            this.fromAirport = fromAirport;
            this.toAirport = toAirport;
            this.distance = distance;
            this.pax = pax;
            this.plannedDepTime = plannedDepTime;
            this.plannedArrTime = plannedArrTime;
            this.salary = salary;
            this.hourlyRate = hourlyRate;
            this.baseIncome = baseIncome;
            this.payParams = payParams;
        }

        public Duration getPlannedDuration() {
            return Duration.ofSeconds(plannedArrTime - plannedDepTime);
        }

        /**
         * The time the pay is for: blocks off to blocks on (the flying time and the taxi), see paidDuration.
         */
        public Duration getPaidDuration() {
            return paidDuration(plannedArrTime - plannedDepTime);
        }

        public enum Type {
            Reposition,
            Revenue,
        }
    }
}
