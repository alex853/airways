package net.simforge.airways2.world.processors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
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
        Properties properties = BusyBirdsMissionGenerator.loadMissionsFile();
        List<BusyBirdsMissionGenerator.MissionInfo> missionInfos = BusyBirdsMissionGenerator.getMissionInfos(properties);
        List<BusyBirdsMissionGenerator.MissionInfo> validMissions = missionInfos.stream().filter(BusyBirdsMissionGenerator.MissionInfo::isValid).toList();

        return validMissions.stream().map(m -> {
            Journeys.Journey j = world.journeys().byId(m.getJourneyId()).orElseThrow();

            Cities.City fromCity = world.cities().byId(j.getFromCityId()).orElseThrow();
            Cities.City toCity = world.cities().byId(j.getToCityId()).orElseThrow();

            int distance = (int) Geo.distance(fromCity.getCoords(), toCity.getCoords());
            int pay = (int) (((distance / 400.0) * 7000.0 + 2000.0)
                    * (1 + Tools.lastDigit(fromCity.getId())*0.01)
                    * (1 + Tools.lastDigit(toCity.getId())*0.01)
                    * (1 + Tools.lastDigit(j.getId())*0.01));

            return new Mission(
                    j,
                    Time.toLdt((int) (m.getValidTill() / 1000)),
                    distance,
                    pay);

        }).toList();
    }

    public BusyBirdsMissionControl.MissionPlan buildPlan(final Journeys.Journey journey, final Aircrafts.Aircraft aircraft) {
        final List<String> messages = new ArrayList<>();

        final AircraftOperators.AircraftOperator busyBirdsOperator = getBusyBirdsOperator();

        if (aircraft.getLocationStatus() != Aircrafts.LocationStatus.ParkedAtAirport) {
            messages.add("aircraft is not parked at airport");
        }
        if (aircraft.getOperationalStatus() != Aircrafts.OperationalStatus.Idle) {
            messages.add("aircraft is not idle");
        }

        final Optional<Airports.Airport> fromAirport = chooseAirport(
                busyBirdsOperator,
                world.airport2city().linksByCityId(journey.getFromCityId())
                        .map(l -> world.airports().byId(l.getAirportId()).orElseThrow())
                        .filter(a -> !a.isExcluded())
                        .toList());
        final Optional<Airports.Airport> toAirport = chooseAirport(
                busyBirdsOperator,
                world.airport2city().linksByCityId(journey.getToCityId())
                        .map(l -> world.airports().byId(l.getAirportId()).orElseThrow())
                        .filter(a -> !a.isExcluded())
                        .toList());

        if (fromAirport.isEmpty()) {
            messages.add("unable to find suitable 'from' airport");
        }

        if (toAirport.isEmpty()) {
            messages.add("unable to find suitable 'to' airport");
        }

        final Optional<Airports.Airport> baseAirport = toAirport.flatMap(airport -> findNearestBaseAirport(world, busyBirdsOperator, airport));
        if (baseAirport.isEmpty()) {
            messages.add("unable to find suitable 'base' airport");
        }

        if (!messages.isEmpty()) {
            return new MissionPlan(MissionPlan.Status.Failure, null, messages, null, 0);
        }

        Airports.Airport locationAirport = world.airports().byId(aircraft.getLocationAirportId()).orElseThrow();
        boolean needFerryFlightToDepartureAirport = locationAirport.getId() != fromAirport.get().getId();
        boolean needFerryFlightToBaseAirport = toAirport.get().getId() != baseAirport.get().getId();

        AircraftTypes.AircraftType aircraftType = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow();
        AircraftPerformanceData performanceData = AircraftPerformanceData.getData(aircraftType.getIcao());

        int nextDepTime = world.getWorldTime() + Time.ONE_HOUR;

        List<Leg> legs = new ArrayList<>();
        Leg leg;

        if (needFerryFlightToDepartureAirport) {
            leg = addLeg(nextDepTime, legs, Leg.Type.Reposition, locationAirport, fromAirport.get(), 0, performanceData);
            nextDepTime = leg.getPlannedArrTime() + Time.ONE_HOUR;
        }

        leg = addLeg(nextDepTime, legs, Leg.Type.Revenue, fromAirport.get(), toAirport.get(), journey.getGroupSize(), performanceData);
        nextDepTime = leg.getPlannedArrTime() + Time.ONE_HOUR;

        if (needFerryFlightToBaseAirport) {
            addLeg(nextDepTime, legs, Leg.Type.Reposition, toAirport.get(), baseAirport.get(), 0, performanceData);
        }

        return new MissionPlan(MissionPlan.Status.Success, legs, null, null, 0);
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

        List<Airports.Airport> fromAirports = listAirports(busyBirdsOperator, journey.getFromCityId());
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

        int nextDepTime = world.getWorldTime() + Time.ONE_HOUR;

        List<Leg> legs = new ArrayList<>();
        Leg leg;

        if (needFerryFlightToDepartureAirport) {
            leg = addLeg(nextDepTime, legs, Leg.Type.Reposition, locationAirport, fromAirport, 0, performanceData);
            nextDepTime = leg.getPlannedArrTime() + turnaroundTimeHours * Time.ONE_HOUR;
        }

        leg = addLeg(nextDepTime, legs, Leg.Type.Revenue, fromAirport, toAirport, journey.getGroupSize(), performanceData);
        nextDepTime = leg.getPlannedArrTime() + turnaroundTimeHours * Time.ONE_HOUR;

        if (needFerryFlightToBaseAirport && ferryBackToBase) {
            addLeg(nextDepTime, legs, Leg.Type.Reposition, toAirport, baseAirport, 0, performanceData);
        }

        int totalDistance = legs.stream().reduce(0, (total, l) -> total + l.distance, Integer::sum);

        return new MissionPlan(MissionPlan.Status.Success, legs, null, "From " + fromAirport.getIcao() + " to " + toAirport.getIcao(), totalDistance);
    }

    private Leg addLeg(int plannedTime, List<Leg> legs,
                       Leg.Type type,
                       Airports.Airport fromAirport, Airports.Airport toAirport,
                       int pax,
                       AircraftPerformanceData performanceData) {
        SimpleFlight simpleFlight = SimpleFlight.forRoute(fromAirport.getCoords(), toAirport.getCoords(), performanceData);

        int plannedDepTime = Time.alignTo5mins(plannedTime);
        int plannedArrTime = plannedDepTime + (int) simpleFlight.getTotalTime().toSeconds();

        Leg leg = new Leg(type, fromAirport, toAirport, (int) Geo.distance(fromAirport.getCoords(), toAirport.getCoords()), pax, plannedDepTime, plannedArrTime);
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
        List<Airports.Airport> airports = world.airport2city().linksByCityId(cityId)
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
        private final List<Leg> legs;
        private final List<String> messages;
        private final String description;
        private final int totalDistance;

        public MissionPlan(final Status status, final List<Leg> legs, final List<String> messages, String description, int totalDistance) {
            this.status = status;
            this.legs = legs != null ? Collections.unmodifiableList(legs) : null;
            this.messages = messages != null ? Collections.unmodifiableList(messages) : null;
            this.description = description;
            this.totalDistance = totalDistance;
        }

        public enum Status {
            Success,
            Failure,
        }
    }

    public static class Leg {
        @Getter
        private final Type type;
        private final Airports.Airport fromAirport;
        private final Airports.Airport toAirport;
        private final int distance;
        private final int pax;
        private final int plannedDepTime;
        private final int plannedArrTime;

        public Leg(final Type type, final Airports.Airport fromAirport, final Airports.Airport toAirport, int distance, final int pax, int plannedDepTime, int plannedArrTime) {
            this.type = type;
            this.fromAirport = fromAirport;
            this.toAirport = toAirport;
            this.distance = distance;
            this.pax = pax;
            this.plannedDepTime = plannedDepTime;
            this.plannedArrTime = plannedArrTime;
        }

        public Airports.Airport getFromAirport() {
            return fromAirport;
        }

        public Airports.Airport getToAirport() {
            return toAirport;
        }

        public int getPax() {
            return pax;
        }

        public int getPlannedDepTime() {
            return plannedDepTime;
        }

        public int getPlannedArrTime() {
            return plannedArrTime;
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
