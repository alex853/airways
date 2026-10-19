package net.simforge.airways2.world.processors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.AircraftPerformanceData;
import net.simforge.airways2.world.computations.SimpleFlight;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

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
            int pay = calcIncome(j, fromCity, toCity, distance);

            return new Mission(
                    j,
                    null,
                    distance,
                    pay);

        }).toList();
    }

    // The income of the mission, the formula is a rough one and is going to be reworked.
    private static int calcIncome(final Journeys.Journey journey, final Cities.City fromCity, final Cities.City toCity, final int distance) {
        return (int) (((distance / 400.0) * 7000.0 + 2000.0)
                * (1 + Tools.lastDigit(fromCity.getId())*0.01)
                * (1 + Tools.lastDigit(toCity.getId())*0.01)
                * (1 + Tools.lastDigit(journey.getId())*0.01));
    }

    private int calcIncome(final Journeys.Journey journey) {
        final Cities.City fromCity = world.cities().byId(journey.getLocationCityId()).orElseThrow();
        final Cities.City toCity = world.cities().byId(journey.getToCityId()).orElseThrow();
        return calcIncome(journey, fromCity, toCity, (int) Geo.distance(fromCity.getCoords(), toCity.getCoords()));
    }

    /**
     * The pilot of a finished BusyBirds flight gets paid: the flight pay for the planned duration of the flight and, if the flight carried
     * the passengers (it has a transport flight), the share of the income of their journeys. There are no accounts and transactions yet,
     * so the payment is only written to the log.
     *
     * @return what the pilot has got, empty if there is nothing to pay: it is not a BusyBirds flight or the flight has no pilot (person)
     */
    public Optional<BusyBirdsPilotPay.Salary> payPilot(final FlightMissions.Mission mission) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        if (aircraft.getAircraftOperatorId() != World25.BusyBirdsOperatorId) {
            return Optional.empty();
        }

        final Optional<Persons.Person> person = mission.getUserId() > 0 ? world.persons().byUserId(mission.getUserId()) : Optional.empty();
        if (person.isEmpty()) {
            log.warn("f/m #{} - BusyBirds flight finished, but it has no pilot (user #{}), nobody is paid", mission.getId(), mission.getUserId());
            return Optional.empty();
        }

        final String aircraftTypeIcao = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao();
        final int plannedDurationSeconds = mission.getPlannedArrivalWorldTime() - mission.getPlannedDepartureWorldTime();

        final Optional<TransportFlights.Flight> transportFlight = world.transportFlights().byFlightMissionId(mission.getId());
        final int missionIncome = transportFlight
                .map(tf -> world.journeys().filter(world.journeys().byTransportFlight1Id(tf.getId()))
                        .mapToInt(this::calcIncomeOfFinishedJourney)
                        .sum())
                .orElse(0);

        final BusyBirdsPilotPay.Salary salary = BusyBirdsPilotPay.calcLegSalary(aircraftTypeIcao, plannedDurationSeconds, transportFlight.isPresent(), missionIncome);

        log.info("p/s #{}, f/m #{}, t/f {} - BusyBirds flight, pilot gets paid {} (flight pay {}, income share {})",
                person.get().getId(),
                mission.getId(),
                transportFlight.map(tf -> "#" + tf.getId()).orElse("none"),
                salary.total(),
                salary.flightPay(),
                salary.incomeShare());

        return Optional.of(salary);
    }

    // The journey is already at the destination city, so its income is calculated by the cities of the trip, not by the current location.
    private int calcIncomeOfFinishedJourney(final Journeys.Journey journey) {
        final Cities.City fromCity = world.cities().byId(journey.getFromCityId()).orElseThrow();
        final Cities.City toCity = world.cities().byId(journey.getToCityId()).orElseThrow();
        return calcIncome(journey, fromCity, toCity, (int) Geo.distance(fromCity.getCoords(), toCity.getCoords()));
    }

    public List<BusyBirdsMissionControl.MissionPlan> buildPlans(Journeys.Journey journey, Aircrafts.Aircraft aircraft, int turnaroundTimeHours, boolean ferryBackToBase) {
        List<String> messages = new ArrayList<>();

        AircraftOperators.AircraftOperator busyBirdsOperator = getBusyBirdsOperator();

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
                plans.add(_buildPlan(journey, busyBirdsOperator, aircraft, fromAirport, toAirport, messages, turnaroundTimeHours, ferryBackToBase));
            }
        }

        plans.sort(Comparator.comparingInt(p -> p.totalDistance));

        return plans;
    }

    private MissionPlan _buildPlan(Journeys.Journey journey, AircraftOperators.AircraftOperator busyBirdsOperator, Aircrafts.Aircraft aircraft, Airports.Airport fromAirport, Airports.Airport toAirport, List<String> messages, int turnaroundTimeHours, boolean ferryBackToBase) {
        Airports.Airport baseAirport = findNearestBaseAirport(world, busyBirdsOperator, toAirport).orElseThrow();

        Airports.Airport locationAirport = world.airports().byId(aircraft.getLocationAirportId()).orElseThrow();
        boolean needFerryFlightToDepartureAirport = locationAirport.getId() != fromAirport.getId();
        boolean needFerryFlightToBaseAirport = toAirport.getId() != baseAirport.getId();

        AircraftTypes.AircraftType aircraftType = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow();
        AircraftPerformanceData performanceData = AircraftPerformanceData.getData(aircraftType.getIcao());

        final int missionIncome = calcIncome(journey);
        final String aircraftTypeIcao = aircraftType.getIcao();

        int nextDepTime = world.getWorldTime() + Time.ONE_HOUR;

        List<Leg> legs = new ArrayList<>();
        Leg leg;

        if (needFerryFlightToDepartureAirport) {
            leg = addLeg(nextDepTime, legs, Leg.Type.Reposition, locationAirport, fromAirport, 0, performanceData, aircraftTypeIcao, 0);
            nextDepTime = leg.getPlannedArrTime() + turnaroundTimeHours * Time.ONE_HOUR;
        }

        leg = addLeg(nextDepTime, legs, Leg.Type.Revenue, fromAirport, toAirport, journey.getGroupSize(), performanceData, aircraftTypeIcao, missionIncome);
        nextDepTime = leg.getPlannedArrTime() + turnaroundTimeHours * Time.ONE_HOUR;

        if (needFerryFlightToBaseAirport && ferryBackToBase) {
            addLeg(nextDepTime, legs, Leg.Type.Reposition, toAirport, baseAirport, 0, performanceData, aircraftTypeIcao, 0);
        }

        int totalDistance = legs.stream().reduce(0, (total, l) -> total + l.distance, Integer::sum);

        return new MissionPlan(
                MissionPlan.Status.Success,
                fromAirport.getIcao() + "-" + toAirport.getIcao(),
                "From " + fromAirport.getIcao() + " to " + toAirport.getIcao() + ", " + legs.size() + " leg" + (legs.size() > 1 ? "s" : "") + ", " + totalDistance + " nm",
                legs,
                null);
    }

    private Leg addLeg(int plannedTime, List<Leg> legs,
                       Leg.Type type,
                       Airports.Airport fromAirport, Airports.Airport toAirport,
                       int pax,
                       AircraftPerformanceData performanceData,
                       String aircraftTypeIcao,
                       int missionIncome) {
        SimpleFlight simpleFlight = SimpleFlight.forRoute(fromAirport.getCoords(), toAirport.getCoords(), performanceData);

        int plannedDepTime = Time.alignTo5mins(plannedTime);
        int plannedArrTime = plannedDepTime + (int) simpleFlight.getTotalTime().toSeconds();

        final BusyBirdsPilotPay.Salary salary = BusyBirdsPilotPay.calcLegSalary(aircraftTypeIcao, plannedArrTime - plannedDepTime, type == Leg.Type.Revenue, missionIncome);
        Leg leg = new Leg(type, fromAirport, toAirport, (int) Geo.distance(fromAirport.getCoords(), toAirport.getCoords()), pax, plannedDepTime, plannedArrTime, salary);
        legs.add(leg);

        return leg;
    }

    private Optional<Airports.Airport> chooseAirport(final AircraftOperators.AircraftOperator aircraftOperator, final List<Airports.Airport> airports) {
        if (airports.isEmpty()) {
            return Optional.empty();
        }

        final Optional<Airports.Airport> baseAirport = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, aircraftOperator, AirportFacilities.Type.BaseAirport))
                .findFirst();
        if (baseAirport.isPresent()) {
            return baseAirport;
        }

        final List<Airports.Airport> preferredBusinessTerminals = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, aircraftOperator, AirportFacilities.Type.BusinessAviationTerminal))
                .toList();
        if (!preferredBusinessTerminals.isEmpty()) {
            return Optional.of(preferredBusinessTerminals.get((int) (Math.random()*preferredBusinessTerminals.size())));
        }

        final List<Airports.Airport> anyBusinessTerminals = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, AirportFacilities.Type.BusinessAviationTerminal))
                .toList();
        if (!anyBusinessTerminals.isEmpty()) {
            return Optional.of(anyBusinessTerminals.get((int) (Math.random()*anyBusinessTerminals.size())));
        }

        return Optional.of(airports.get((int) (Math.random()*airports.size())));
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


    // todo ak2 take into account aircraft max range
    // todo ak2 check aircraft seats vs journey size

    private static Optional<Aircrafts.Aircraft> findNearestSuitableAircraft(final World world, final AircraftOperators.AircraftOperator aircraftOperator, final Airports.Airport airport) {
        return world.aircrafts()
                .byAircraftOperatorIdAndIdleAndParkedAtAirport(aircraftOperator.getId())
                .min(Comparator.comparingDouble(a -> Geo.distance(a.getLocationCoords(), airport.getCoords())));
    }

    @Data
    @AllArgsConstructor
    public static class Mission {
        private final Journeys.Journey journey;
        private final LocalDateTime validTill;
        private final int distance;
        private final int pay;
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

        public Leg(final Type type, final Airports.Airport fromAirport, final Airports.Airport toAirport, int distance, final int pax, int plannedDepTime, int plannedArrTime, final BusyBirdsPilotPay.Salary salary) {
            this.type = type;
            this.fromAirport = fromAirport;
            this.toAirport = toAirport;
            this.distance = distance;
            this.pax = pax;
            this.plannedDepTime = plannedDepTime;
            this.plannedArrTime = plannedArrTime;
            this.salary = salary;
        }

        public Duration getPlannedDuration() {
            return Duration.ofSeconds(plannedArrTime - plannedDepTime);
        }

        public enum Type {
            Reposition,
            Revenue,
        }
    }
}
